package com.netwatch.android

import android.util.Base64
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class GatewayException(val code: String, message: String) : IOException(message)

class PinnedGatewayClient private constructor(
    val host: String,
    val port: Int,
    val spkiSha256: String,
    val credential: String?,
) {
    private val origin = "https://$host:$port"
    val httpClient: OkHttpClient = buildHttpClient(host, spkiSha256, credential)

    fun get(path: String): JSONObject = call("GET", path)

    fun post(path: String, body: JSONObject): JSONObject = call("POST", path, body)

    fun delete(path: String): JSONObject = call("DELETE", path)

    fun getBytes(path: String): ByteArray {
        val request = Request.Builder().url(absoluteUrl(path)).get().build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw GatewayException("HTTP_${response.code}", "Asset request failed")
            val declared = response.body.contentLength()
            if (declared > MAX_ASSET_BYTES) throw GatewayException("RESPONSE_TOO_LARGE", "Asset is too large")
            return readBounded(response.body.byteStream(), MAX_ASSET_BYTES)
        }
    }

    fun absoluteUrl(path: String): String {
        require(path.startsWith("/remote/v1/") && !path.contains(".."))
        return origin + path
    }

    private fun call(method: String, path: String, body: JSONObject? = null): JSONObject {
        val request = Request.Builder()
            .url(absoluteUrl(path))
            .method(method, body?.toString()?.toRequestBody(JSON) ?: if (method == "POST") EMPTY_BODY else null)
            .header("Accept", "application/json")
            .build()
        httpClient.newCall(request).execute().use { response ->
            val responseBody = response.body
            val declared = responseBody.contentLength()
            if (declared > MAX_JSON_BYTES) throw GatewayException("RESPONSE_TOO_LARGE", "Gateway response is too large")
            val bytes = readBounded(responseBody.byteStream(), MAX_JSON_BYTES)
            if (bytes.size > MAX_JSON_BYTES) throw GatewayException("RESPONSE_TOO_LARGE", "Gateway response is too large")
            val json = runCatching { JSONObject(bytes.toString(Charsets.UTF_8).ifBlank { "{}" }) }
                .getOrElse { throw GatewayException("INVALID_RESPONSE", "Gateway returned malformed JSON") }
            if (!response.isSuccessful) {
                val error = json.optJSONObject("error")
                throw GatewayException(error?.optString("code") ?: "HTTP_${response.code}", error?.optString("message") ?: "Gateway request failed")
            }
            return json
        }
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private val EMPTY_BODY = ByteArray(0).toRequestBody(JSON)
        private const val MAX_JSON_BYTES = 8 * 1024 * 1024
        private const val MAX_ASSET_BYTES = 12 * 1024 * 1024

        fun forPairing(payload: PairingPayload) = PinnedGatewayClient(payload.host, payload.port, payload.serverSpkiSha256, null)

        fun forProfile(profile: GatewayProfile) = PinnedGatewayClient(
            profile.host,
            profile.port,
            profile.spkiSha256,
            profile.credential,
        )

        internal fun spkiSha256(certificate: X509Certificate): ByteArray =
            MessageDigest.getInstance("SHA-256").digest(certificate.publicKey.encoded)

        internal fun decodePin(pin: String): ByteArray = Base64.decode(pin, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

        private fun readBounded(input: java.io.InputStream, limit: Int): ByteArray {
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (output.size() <= limit) {
                val count = input.read(buffer)
                if (count < 0) break
                output.write(buffer, 0, count)
            }
            if (output.size() > limit) throw GatewayException("RESPONSE_TOO_LARGE", "Gateway response is too large")
            return output.toByteArray()
        }

        @android.annotation.SuppressLint("CustomX509TrustManager")
        private fun buildHttpClient(host: String, pin: String, credential: String?): OkHttpClient {
            val expectedPin = decodePin(pin)
            require(expectedPin.size == 32) { "Gateway fingerprint is invalid" }
            val trustManager = object : X509TrustManager {
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
                override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?) =
                    throw CertificateException("Client certificates are not accepted")

                override fun checkServerTrusted(chain: Array<X509Certificate>?, authType: String?) {
                    val certificate = chain?.firstOrNull() ?: throw CertificateException("Gateway certificate is missing")
                    if (!MessageDigest.isEqual(expectedPin, spkiSha256(certificate))) {
                        throw CertificateException("Gateway identity does not match the pairing QR")
                    }
                    certificate.checkValidity()
                }
            }
            val sslContext = SSLContext.getInstance("TLS").apply {
                init(null, arrayOf<TrustManager>(trustManager), SecureRandom())
            }
            val auth = Interceptor { chain ->
                val original = chain.request()
                if (original.url.scheme != "https" || original.url.host != host) throw IOException("Gateway origin changed")
                val builder = original.newBuilder()
                if (credential != null) builder.header("Authorization", "Bearer $credential")
                chain.proceed(builder.build())
            }
            return OkHttpClient.Builder()
                .sslSocketFactory(sslContext.socketFactory, trustManager)
                .hostnameVerifier { requestedHost, _ -> requestedHost == host }
                .addInterceptor(auth)
                .followRedirects(false)
                .followSslRedirects(false)
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(45, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .build()
        }
    }
}
