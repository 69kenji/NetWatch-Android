package com.netwatch.android

import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class GatewayRepository(private val vault: CredentialVault) {
    fun profile(): GatewayProfile? = vault.load()

    suspend fun pair(payload: PairingPayload): GatewayProfile = withContext(Dispatchers.IO) {
        val response = PinnedGatewayClient.forPairing(payload).post(
            "/remote/v1/pair/claim",
            JSONObject()
                .put("pairing_secret", payload.pairingSecret)
                .put("device_name", "${Build.MANUFACTURER} ${Build.MODEL}".trim().take(80)),
        )
        val profile = GatewayProfile(
            host = payload.host,
            port = payload.port,
            spkiSha256 = payload.serverSpkiSha256,
            deviceId = response.getString("device_id"),
            credential = response.getString("device_credential"),
        )
        PinnedGatewayClient.forProfile(profile).get("/remote/v1/status")
        vault.save(profile)
        profile
    }

    suspend fun status(profile: GatewayProfile): JSONObject = withContext(Dispatchers.IO) {
        PinnedGatewayClient.forProfile(profile).get("/remote/v1/status")
    }

    suspend fun home(profile: GatewayProfile): List<HomeSection> = withContext(Dispatchers.IO) {
        parseHome(PinnedGatewayClient.forProfile(profile).get("/remote/v1/home"))
    }

    suspend fun discover(profile: GatewayProfile, media: String, category: String, genre: Int?): List<CatalogItem> = withContext(Dispatchers.IO) {
        val genreQuery = genre?.let { "&genre=$it" }.orEmpty()
        parseCatalogArray(PinnedGatewayClient.forProfile(profile).get("/remote/v1/discover?media=$media&category=$category$genreQuery").optJSONArray("results"))
    }

    suspend fun genres(profile: GatewayProfile, media: String): List<GenreChoice> = withContext(Dispatchers.IO) {
        val values = PinnedGatewayClient.forProfile(profile).get("/remote/v1/discover/genres?media=$media").optJSONArray("genres") ?: JSONArray()
        buildList {
            for (index in 0 until values.length()) {
                val genre = values.optJSONObject(index) ?: continue
                val id = genre.optInt("id", 0)
                if (id > 0) add(GenreChoice(id, genre.optString("name", "Genre")))
            }
        }
    }

    suspend fun search(profile: GatewayProfile, query: String): List<CatalogItem> = withContext(Dispatchers.IO) {
        parseCatalogArray(PinnedGatewayClient.forProfile(profile).get("/remote/v1/search?q=${encode(query)}&page=1").optJSONArray("results"))
    }

    suspend fun streamOptions(
        profile: GatewayProfile,
        item: CatalogItem,
        season: Int? = null,
        episode: Int? = null,
    ): List<StreamOption> = withContext(Dispatchers.IO) {
        val path = if (item.catalogId.startsWith("movie:")) {
            "/remote/v1/title/${item.catalogId}/stream-options"
        } else {
            require(season != null && episode != null) { "Select a season and episode" }
            "/remote/v1/title/${item.catalogId}/episode/$season/$episode/stream-options"
        }
        val payload = PinnedGatewayClient.forProfile(profile).get(path)
        val results = payload.optJSONArray("results") ?: JSONArray()
        buildList {
            for (index in 0 until results.length()) {
                results.optJSONObject(index)?.let(StreamOption::fromJson)?.let(::add)
            }
        }
    }

    suspend fun seasons(profile: GatewayProfile, item: CatalogItem): List<SeasonChoice> = withContext(Dispatchers.IO) {
        require(item.catalogId.startsWith("tv:"))
        val payload = PinnedGatewayClient.forProfile(profile).get("/remote/v1/title/${item.catalogId}/seasons")
        parseSeasons(payload)
    }

    suspend fun episodes(profile: GatewayProfile, item: CatalogItem, season: Int): List<EpisodeChoice> = withContext(Dispatchers.IO) {
        require(item.catalogId.startsWith("tv:"))
        val payload = PinnedGatewayClient.forProfile(profile).get("/remote/v1/title/${item.catalogId}/season/$season")
        parseEpisodes(payload)
    }

    suspend fun createPlayback(
        profile: GatewayProfile,
        item: CatalogItem,
        option: StreamOption,
        season: Int? = null,
        episode: Int? = null,
    ): PlaybackSession = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("catalog_id", item.catalogId)
            .put("release_ref", option.releaseRef)
            .put("media_name", item.title)
        if (season != null) body.put("season", season)
        if (episode != null) body.put("episode", episode)
        val response = PinnedGatewayClient.forProfile(profile).post("/remote/v1/playback", body)
        PlaybackSession(response.getString("session_id"), response.optString("state", "buffering"))
    }

    suspend fun revokeAndClear(profile: GatewayProfile) = withContext(Dispatchers.IO) {
        runCatching { PinnedGatewayClient.forProfile(profile).delete("/remote/v1/device/self") }
        vault.clear()
    }

    fun clearLocalProfile() = vault.clear()

    private fun encode(value: String): String = java.net.URLEncoder.encode(value, Charsets.UTF_8.name())

    companion object {
        private val HOME_RAILS = listOf(
            "movies" to "Trending Movies",
            "recent_movies" to "Recent Movies",
            "tv" to "Trending TV",
            "recent_tv" to "Recent TV",
            "anime" to "Trending Anime",
            "recent_anime" to "Recent Anime",
        )

        internal fun parseCatalogArray(values: JSONArray?): List<CatalogItem> = buildList {
            if (values == null) return@buildList
            for (index in 0 until values.length()) {
                values.optJSONObject(index)?.let(CatalogItem::fromJson)?.let(::add)
            }
        }

        internal fun parseHome(payload: JSONObject): List<HomeSection> = HOME_RAILS.map { (key, title) ->
            HomeSection(title, parseCatalogArray(payload.optJSONArray(key)))
        }.filter { it.items.isNotEmpty() }

        internal fun parseSeasons(payload: JSONObject): List<SeasonChoice> = buildList {
            val values = payload.optJSONArray("seasons") ?: return@buildList
            for (index in 0 until values.length()) {
                val season = values.optJSONObject(index) ?: continue
                val number = season.optInt("season_number", -1)
                if (number >= 0) add(SeasonChoice(number, season.optString("name", "Season $number"), season.optInt("episode_count", 0)))
            }
        }

        internal fun parseEpisodes(payload: JSONObject): List<EpisodeChoice> = buildList {
            val values = payload.optJSONArray("episodes") ?: return@buildList
            for (index in 0 until values.length()) {
                val episode = values.optJSONObject(index) ?: continue
                val number = episode.optInt("episode_number", -1)
                if (number >= 0) add(EpisodeChoice(
                    number = number,
                    name = episode.optString("name", "Episode $number"),
                    overview = episode.optString("overview").ifBlank { null },
                    stillPath = episode.optString("still").takeIf { it.startsWith("/remote/v1/artwork/") },
                    runtime = episode.optInt("runtime", 0).takeIf { it > 0 },
                    rating = episode.optDouble("rating", 0.0),
                ))
            }
        }
    }
}
