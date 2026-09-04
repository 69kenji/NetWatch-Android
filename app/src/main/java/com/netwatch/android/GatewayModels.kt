package com.netwatch.android

import org.json.JSONObject
import java.time.Instant

data class PairingPayload(
    val version: Int,
    val host: String,
    val port: Int,
    val pairingSecret: String,
    val serverSpkiSha256: String,
    val expiresAt: String,
) {
    fun baseUrl(): String = "https://$host:$port"

    companion object {
        fun parse(raw: String): PairingPayload {
            require(raw.length in 80..2048) { "QR payload has an invalid size" }
            val json = JSONObject(raw)
            val payload = PairingPayload(
                version = json.getInt("version"),
                host = json.getString("host"),
                port = json.getInt("port"),
                pairingSecret = json.getString("pairing_secret"),
                serverSpkiSha256 = json.getString("server_spki_sha256"),
                expiresAt = json.getString("expires_at"),
            )
            require(payload.version == 1) { "Unsupported Remote Access protocol" }
            require(payload.port in 1024..65535) { "Gateway port is invalid" }
            require(Regex("^[A-Za-z0-9_-]{32}$").matches(payload.pairingSecret)) { "Pairing secret is invalid" }
            require(Regex("^[A-Za-z0-9_-]{43}$").matches(payload.serverSpkiSha256)) { "Gateway fingerprint is invalid" }
            require(isPrivateIpv4(payload.host)) { "Gateway must use a private IPv4 address" }
            val expiry = Instant.parse(payload.expiresAt)
            require(expiry.isAfter(Instant.now()) && expiry.isBefore(Instant.now().plusSeconds(10 * 60))) {
                "Pairing QR has expired"
            }
            return payload
        }

        internal fun isPrivateIpv4(host: String): Boolean {
            val parts = host.split('.')
            if (parts.size != 4 || parts.any { it.isEmpty() || !it.all(Char::isDigit) || (it.length > 1 && it.startsWith('0')) }) return false
            val bytes = parts.map { it.toIntOrNull() ?: return false }
            if (bytes.any { it !in 0..255 }) return false
            return bytes[0] == 10 ||
                (bytes[0] == 172 && bytes[1] in 16..31) ||
                (bytes[0] == 192 && bytes[1] == 168)
        }
    }
}

data class GatewayProfile(
    val host: String,
    val port: Int,
    val spkiSha256: String,
    val deviceId: String,
    val credential: String,
) {
    val baseUrl: String get() = "https://$host:$port"

    fun toJson(): JSONObject = JSONObject()
        .put("host", host)
        .put("port", port)
        .put("spki_sha256", spkiSha256)
        .put("device_id", deviceId)
        .put("credential", credential)

    companion object {
        fun fromJson(json: JSONObject): GatewayProfile = GatewayProfile(
            host = json.getString("host"),
            port = json.getInt("port"),
            spkiSha256 = json.getString("spki_sha256"),
            deviceId = json.getString("device_id"),
            credential = json.getString("credential"),
        ).also {
            require(PairingPayload.isPrivateIpv4(it.host))
            require(it.port in 1024..65535)
            require(Regex("^[A-Za-z0-9_-]{43}$").matches(it.spkiSha256))
            require(Regex("^[A-Za-z0-9_-]{43}$").matches(it.credential))
        }
    }
}

data class CatalogItem(
    val catalogId: String,
    val title: String,
    val year: String?,
    val overview: String?,
    val artworkPath: String?,
    val backdropPath: String?,
    val rating: Double,
    val language: String?,
    val isAnime: Boolean,
) {
    val isSeries: Boolean get() = catalogId.startsWith("tv:")
    val kindLabel: String get() = if (isAnime) "Anime" else if (isSeries) "TV" else "Movie"

    companion object {
        fun fromJson(json: JSONObject): CatalogItem? {
            val id = json.optLong("id", json.optLong("tmdb_id", 0L))
            if (id <= 0L) return null
            val mediaType = when (json.optString("media_type", json.optString("type", "movie")).lowercase()) {
                "tv", "series", "anime" -> "tv"
                else -> "movie"
            }
            val title = json.optString("title", json.optString("name", "Untitled")).trim()
            val artwork = sequenceOf("poster_url", "poster", "poster_path")
                .map { json.optString(it) }
                .firstOrNull { it.startsWith("/remote/v1/artwork/") }
            val backdrop = sequenceOf("backdrop_url", "backdrop", "player_backdrop")
                .map { json.optString(it) }
                .firstOrNull { it.startsWith("/remote/v1/artwork/") }
            return CatalogItem(
                catalogId = "$mediaType:$id",
                title = title.ifEmpty { "Untitled" },
                year = json.optString("year").ifBlank { null },
                overview = json.optString("overview").ifBlank { null },
                artworkPath = artwork,
                backdropPath = backdrop,
                rating = json.optDouble("rating", 0.0),
                language = json.optString("original_language").ifBlank { null },
                isAnime = json.optBoolean("is_anime", false),
            )
        }
    }
}

data class HomeSection(val title: String, val items: List<CatalogItem>)
data class GenreChoice(val id: Int, val name: String)

data class StreamOption(
    val releaseRef: String,
    val title: String,
    val seeders: Int,
    val sizeLabel: String?,
    val details: String,
) {
    companion object {
        fun fromJson(json: JSONObject): StreamOption? {
            val ref = json.optString("release_ref")
            if (!Regex("^[A-Za-z0-9_-]{32,128}$").matches(ref)) return null
            return StreamOption(
                releaseRef = ref,
                title = json.optString("title", "Release"),
                seeders = json.optInt("seeders", json.optInt("seed", 0)),
                sizeLabel = sizeLabel(json.opt("size")),
                details = listOf("resolution", "source", "codec", "audio", "indexer")
                    .map { json.optString(it) }
                    .filter { it.isNotBlank() && !it.equals("Unknown", ignoreCase = true) }
                    .joinToString(" · ")
                    .ifBlank { "Torrent release" },
            )
        }

        private fun sizeLabel(value: Any?): String? {
            val bytes = when (value) {
                is Number -> value.toDouble()
                is String -> value.toDoubleOrNull() ?: return value.ifBlank { null }
                else -> return null
            }
            if (bytes <= 0) return null
            val units = listOf("B", "KB", "MB", "GB", "TB")
            var amount = bytes
            var unit = 0
            while (amount >= 1024 && unit < units.lastIndex) { amount /= 1024; unit++ }
            return if (unit == 0) "${amount.toLong()} ${units[unit]}" else "%.1f %s".format(amount, units[unit])
        }
    }
}

data class PlaybackSession(val id: String, val state: String)

data class SeasonChoice(val number: Int, val name: String, val episodeCount: Int)

data class EpisodeChoice(
    val number: Int,
    val name: String,
    val overview: String?,
    val stillPath: String?,
    val runtime: Int?,
    val rating: Double,
)
