package com.netwatch.android

import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

private object GatewayArtworkCache {
    val images = ConcurrentHashMap<String, ImageBitmap>()
    private var clientKey: String? = null
    private var cachedClient: PinnedGatewayClient? = null

    fun profileKey(profile: GatewayProfile): String {
        val identity = "${profile.host}\n${profile.port}\n${profile.spkiSha256}\n${profile.credential}"
        return MessageDigest.getInstance("SHA-256")
            .digest(identity.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    @Synchronized
    fun client(profile: GatewayProfile): PinnedGatewayClient {
        val key = profileKey(profile)
        if (key != clientKey) {
            cachedClient?.httpClient?.connectionPool?.evictAll()
            cachedClient?.httpClient?.dispatcher?.executorService?.shutdown()
            images.clear()
            clientKey = key
            cachedClient = PinnedGatewayClient.forProfile(profile)
        }
        return requireNotNull(cachedClient)
    }

    fun put(key: String, image: ImageBitmap) {
        if (images.size >= 48) images.keys.take(12).forEach(images::remove)
        images[key] = image
    }

    fun decode(bytes: ByteArray): ImageBitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while (
            bounds.outWidth / sampleSize > MAX_BITMAP_DIMENSION ||
            bounds.outHeight / sampleSize > MAX_BITMAP_DIMENSION ||
            bounds.outWidth.toLong() * bounds.outHeight.toLong() / (sampleSize.toLong() * sampleSize) > MAX_BITMAP_PIXELS
        ) {
            sampleSize *= 2
        }
        return BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sampleSize },
        )?.asImageBitmap()
    }

    private const val MAX_BITMAP_DIMENSION = 4096
    private const val MAX_BITMAP_PIXELS = 12_000_000L
}

@Composable
fun GatewayArtwork(
    path: String?,
    profile: GatewayProfile,
    modifier: Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    showFallbackMark: Boolean = true,
) {
    val key = "${GatewayArtworkCache.profileKey(profile)}:$path"
    val bitmap by produceState<ImageBitmap?>(GatewayArtworkCache.images[key], key) {
        if (value == null && path != null) value = withContext(Dispatchers.IO) {
            runCatching {
                val bytes = GatewayArtworkCache.client(profile).getBytes(path)
                GatewayArtworkCache.decode(bytes)?.also { GatewayArtworkCache.put(key, it) }
            }.getOrNull()
        }
    }
    if (bitmap != null) {
        Image(bitmap!!, null, modifier, contentScale = contentScale)
    } else {
        Box(
            modifier.background(Brush.linearGradient(listOf(Color(0xFF15151C), Color(0xFF1D1729)))),
            contentAlignment = Alignment.Center,
        ) {
            if (showFallbackMark) Text("NW", color = Color.White.copy(.18f), fontWeight = FontWeight.Bold)
        }
    }
}
