package com.netwatch.android

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class CatalogView { HOME, DISCOVER, SEARCH, SETTINGS }

data class MainUiState(
    val profile: GatewayProfile? = null,
    val runtimeReady: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
    val view: CatalogView = CatalogView.HOME,
    val homeSections: List<HomeSection> = emptyList(),
    val items: List<CatalogItem> = emptyList(),
    val discoverMedia: String = "movies",
    val discoverCategory: String = "popular",
    val discoverGenres: List<GenreChoice> = emptyList(),
    val discoverGenre: Int? = null,
    val searchQuery: String = "",
    val selected: CatalogItem? = null,
    val options: List<StreamOption> = emptyList(),
    val seasons: List<SeasonChoice> = emptyList(),
    val episodes: List<EpisodeChoice> = emptyList(),
    val season: Int = 1,
    val episode: Int? = null,
)

class MainViewModel(private val repository: GatewayRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(MainUiState(profile = repository.profile()))
    val state: StateFlow<MainUiState> = mutableState.asStateFlow()

    init { mutableState.value.profile?.let { refresh(it) } }

    fun pair(raw: String) = operation {
        val profile = repository.pair(PairingPayload.parse(raw))
        mutableState.value = MainUiState(profile = profile)
        refresh(profile)
    }

    fun refresh(profile: GatewayProfile? = null) {
        val activeProfile = profile ?: mutableState.value.profile ?: return
        operation {
            val status = repository.status(activeProfile)
            val sections = repository.home(activeProfile)
            mutableState.value = mutableState.value.copy(
                runtimeReady = status.optBoolean("runtime_ready"), view = CatalogView.HOME,
                homeSections = sections, selected = null, options = emptyList(), seasons = emptyList(),
                episodes = emptyList(), episode = null,
            )
        }
    }

    fun showView(view: CatalogView) {
        when (view) {
            CatalogView.HOME -> refresh()
            CatalogView.DISCOVER -> {
                mutableState.value = mutableState.value.copy(view = view, selected = null)
                discover()
            }
            CatalogView.SEARCH, CatalogView.SETTINGS -> mutableState.value = mutableState.value.copy(view = view, selected = null)
        }
    }

    fun discover(
        media: String = mutableState.value.discoverMedia,
        category: String = mutableState.value.discoverCategory,
        genre: Int? = mutableState.value.discoverGenre,
    ) {
        val profile = mutableState.value.profile ?: return
        operation {
            val selectedGenre = genre.takeIf { media == mutableState.value.discoverMedia }
            // Genre filtering was added after the first remote-v1 desktop builds. Keep
            // the catalog usable while an older paired desktop is being upgraded.
            val genres = runCatching { repository.genres(profile, media) }.getOrDefault(emptyList())
            val items = repository.discover(profile, media, category, selectedGenre)
            mutableState.value = mutableState.value.copy(
                view = CatalogView.DISCOVER, discoverMedia = media, discoverCategory = category,
                discoverGenres = genres, discoverGenre = selectedGenre,
                items = items, selected = null,
            )
        }
    }

    fun search(query: String) {
        val profile = mutableState.value.profile ?: return
        val normalized = query.trim()
        mutableState.value = mutableState.value.copy(view = CatalogView.SEARCH, searchQuery = normalized)
        if (normalized.isBlank()) {
            mutableState.value = mutableState.value.copy(items = emptyList())
            return
        }
        operation { mutableState.value = mutableState.value.copy(items = repository.search(profile, normalized), selected = null) }
    }

    fun select(item: CatalogItem) {
        mutableState.value = mutableState.value.copy(
            selected = item, options = emptyList(), seasons = emptyList(), episodes = emptyList(), episode = null, error = null,
        )
        if (item.isSeries) loadSeasons() else loadOptions()
    }

    fun selectSeason(season: Int) {
        val current = mutableState.value
        val profile = current.profile ?: return
        val item = current.selected ?: return
        operation {
            val episodes = repository.episodes(profile, item, season)
            mutableState.value = mutableState.value.copy(season = season, episode = null, episodes = episodes, options = emptyList())
        }
    }

    fun selectEpisode(episode: Int) {
        mutableState.value = mutableState.value.copy(episode = episode, options = emptyList())
        loadOptions()
    }

    fun closeSelection() {
        mutableState.value = mutableState.value.copy(selected = null, options = emptyList(), seasons = emptyList(), episodes = emptyList(), episode = null)
    }

    private fun loadSeasons() {
        val current = mutableState.value
        val profile = current.profile ?: return
        val item = current.selected ?: return
        operation {
            val seasons = repository.seasons(profile, item)
            val selectedSeason = seasons.firstOrNull { it.number > 0 }?.number ?: seasons.firstOrNull()?.number ?: 1
            val episodes = repository.episodes(profile, item, selectedSeason)
            mutableState.value = mutableState.value.copy(
                seasons = seasons, season = selectedSeason, episodes = episodes, episode = null, options = emptyList(),
            )
        }
    }

    fun loadOptions() {
        val current = mutableState.value
        val profile = current.profile ?: return
        val item = current.selected ?: return
        if (item.isSeries && current.episode == null) return
        operation {
            mutableState.value = mutableState.value.copy(
                options = repository.streamOptions(profile, item, current.season.takeIf { item.isSeries }, current.episode),
            )
        }
    }

    fun start(option: StreamOption, onReady: (GatewayProfile, PlaybackSession, String, String?) -> Unit) {
        val current = mutableState.value
        val profile = current.profile ?: return
        val item = current.selected ?: return
        operation {
            val session = repository.createPlayback(profile, item, option, current.season.takeIf { item.isSeries }, current.episode)
            val episodeName = current.episodes.firstOrNull { it.number == current.episode }?.name
            val episodeCode = current.episode?.toString()?.padStart(2, '0')
            val title = if (episodeName != null) "${item.title} · S${current.season.toString().padStart(2, '0')}E$episodeCode · $episodeName" else item.title
            onReady(profile, session, title, item.backdropPath ?: item.artworkPath)
        }
    }

    fun disconnect() {
        val profile = mutableState.value.profile ?: return
        operation { repository.revokeAndClear(profile); mutableState.value = MainUiState() }
    }

    fun forgetAfterAuthFailure() {
        repository.clearLocalProfile()
        mutableState.value = MainUiState(error = "This device is no longer authorized. Pair it again from the PC.")
    }

    fun clearError() { mutableState.value = mutableState.value.copy(error = null) }

    private fun operation(block: suspend () -> Unit) {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(loading = true, error = null)
            try { block() }
            catch (error: Exception) {
                if (error is GatewayException && error.code == "AUTH_REQUIRED") forgetAfterAuthFailure()
                else mutableState.value = mutableState.value.copy(error = error.message ?: "Request failed")
            } finally { mutableState.value = mutableState.value.copy(loading = false) }
        }
    }
}
