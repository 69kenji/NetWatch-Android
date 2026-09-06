package com.netwatch.android

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class CatalogView { HOME, DISCOVER, SEARCH, SETTINGS }

data class MainUiState(
    val profile: GatewayProfile? = null,
    val runtimeReady: Boolean = false,
    val loading: Boolean = false,
    val transitionLoading: Boolean = false,
    val error: String? = null,
    val view: CatalogView = CatalogView.HOME,
    val homeSections: List<HomeSection> = emptyList(),
    val keepWatching: List<KeepWatchingItem> = emptyList(),
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
    val episodeRangeIndex: Int = 0,
    val releaseSheetOpen: Boolean = false,
    val releaseSheetGeneration: Long = 0L,
    val releaseLoading: Boolean = false,
    val releaseError: String? = null,
)

class MainViewModel(private val repository: GatewayRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(MainUiState(profile = repository.profile()))
    val state: StateFlow<MainUiState> = mutableState.asStateFlow()
    private var releaseRequestId = 0L
    private var operationGeneration = 0L
    private var operationJob: Job? = null
    private var keepWatchingRefreshJob: Job? = null

    init { mutableState.value.profile?.let { refresh(it) } }

    fun pair(raw: String) {
        keepWatchingRefreshJob?.cancel()
        operation {
            val profile = repository.pair(PairingPayload.parse(raw))
            mutableState.value = MainUiState(profile = profile)
            loadHome(profile)
        }
    }

    fun refresh(profile: GatewayProfile? = null, showTransition: Boolean = false) {
        val activeProfile = profile ?: mutableState.value.profile ?: return
        operation(showTransition = showTransition) { loadHome(activeProfile) }
    }

    private suspend fun loadHome(profile: GatewayProfile) {
        val status = repository.status(profile)
        val sections = repository.home(profile)
        val keepWatching = runCatching { repository.keepWatching(profile) }.getOrDefault(emptyList())
        mutableState.value = mutableState.value.copy(
            runtimeReady = status.optBoolean("runtime_ready"), view = CatalogView.HOME,
            homeSections = sections, keepWatching = keepWatching, selected = null,
            options = emptyList(), seasons = emptyList(), episodes = emptyList(), episode = null,
            episodeRangeIndex = 0, releaseSheetOpen = false, releaseSheetGeneration = 0L, releaseLoading = false, releaseError = null,
        )
    }

    fun showView(view: CatalogView) {
        when (view) {
            CatalogView.HOME -> refresh(showTransition = mutableState.value.view == CatalogView.DISCOVER)
            CatalogView.DISCOVER -> {
                val transition = mutableState.value.view == CatalogView.HOME
                mutableState.value = mutableState.value.copy(view = view, selected = null)
                discover(showTransition = transition)
            }
            CatalogView.SEARCH, CatalogView.SETTINGS -> {
                cancelOperation()
                mutableState.value = mutableState.value.copy(view = view, selected = null)
            }
        }
    }

    fun discover(
        media: String = mutableState.value.discoverMedia,
        category: String = mutableState.value.discoverCategory,
        genre: Int? = mutableState.value.discoverGenre,
        showTransition: Boolean = false,
    ) {
        val profile = mutableState.value.profile ?: return
        operation(showTransition = showTransition) {
            val selectedGenre = genre.takeIf { media == mutableState.value.discoverMedia }
            val genres = runCatching { repository.genres(profile, media) }.getOrDefault(emptyList())
            val items = repository.discover(profile, media, category, selectedGenre)
            mutableState.value = mutableState.value.copy(
                view = CatalogView.DISCOVER, discoverMedia = media, discoverCategory = category,
                discoverGenres = genres, discoverGenre = selectedGenre, items = items, selected = null,
            )
        }
    }

    fun search(query: String) {
        val profile = mutableState.value.profile ?: return
        val normalized = query.trim()
        mutableState.value = mutableState.value.copy(view = CatalogView.SEARCH, searchQuery = normalized)
        if (normalized.isBlank()) {
            cancelOperation()
            mutableState.value = mutableState.value.copy(items = emptyList())
            return
        }
        operation { mutableState.value = mutableState.value.copy(items = repository.search(profile, normalized), selected = null) }
    }

    fun select(item: CatalogItem) {
        releaseRequestId += 1
        mutableState.value = mutableState.value.copy(
            selected = item, options = emptyList(), seasons = emptyList(), episodes = emptyList(), episode = null,
            episodeRangeIndex = 0, releaseSheetOpen = false, releaseSheetGeneration = 0L, releaseLoading = false, releaseError = null, error = null,
        )
        if (item.isSeries) loadSeasons() else loadOptions()
    }

    fun selectSeason(season: Int) {
        val current = mutableState.value
        val profile = current.profile ?: return
        val item = current.selected ?: return
        releaseRequestId += 1
        mutableState.value = current.copy(
            season = season, episode = null, episodeRangeIndex = 0, episodes = emptyList(), options = emptyList(),
            releaseSheetOpen = false, releaseSheetGeneration = 0L, releaseLoading = false, releaseError = null,
        )
        operation {
            val episodes = repository.episodes(profile, item, season)
            if (mutableState.value.selected?.catalogId == item.catalogId && mutableState.value.season == season) {
                mutableState.value = mutableState.value.copy(episodes = episodes)
            }
        }
    }

    fun selectEpisodeRange(index: Int) {
        mutableState.value = mutableState.value.copy(episodeRangeIndex = index.coerceAtLeast(0))
    }

    fun selectEpisode(episode: Int) {
        releaseRequestId += 1
        val requestId = releaseRequestId
        val current = mutableState.value
        if (
            current.episode == episode &&
            current.options.isNotEmpty() &&
            !current.releaseLoading
        ) {
            mutableState.value = current.copy(
                releaseSheetOpen = true,
                releaseSheetGeneration = requestId,
                releaseError = null,
            )
            return
        }
        mutableState.value = mutableState.value.copy(
            episode = episode,
            options = emptyList(),
            releaseSheetOpen = true,
            releaseSheetGeneration = requestId,
            releaseLoading = true,
            releaseError = null,
        )
        loadEpisodeOptions(requestId)
    }

    fun dismissReleaseSheet(generation: Long) {
        // A dialog removed while the player/activity is changing can deliver its dismiss
        // callback late. Never let an obsolete sheet clear a newly opened release search.
        if (mutableState.value.releaseSheetGeneration != generation) return
        releaseRequestId += 1
        mutableState.value = mutableState.value.copy(
            releaseSheetOpen = false,
            releaseSheetGeneration = 0L,
            releaseLoading = false,
            releaseError = null,
        )
    }

    fun retryReleaseSearch() {
        if (mutableState.value.episode == null) return
        releaseRequestId += 1
        val requestId = releaseRequestId
        mutableState.value = mutableState.value.copy(options = emptyList(), releaseLoading = true, releaseError = null)
        loadEpisodeOptions(requestId)
    }

    fun closeSelection() {
        releaseRequestId += 1
        mutableState.value = mutableState.value.copy(
            selected = null, options = emptyList(), seasons = emptyList(), episodes = emptyList(), episode = null,
            episodeRangeIndex = 0, releaseSheetOpen = false, releaseSheetGeneration = 0L, releaseLoading = false, releaseError = null,
        )
    }

    private fun loadSeasons() {
        val current = mutableState.value
        val profile = current.profile ?: return
        val item = current.selected ?: return
        operation {
            val seasons = repository.seasons(profile, item)
            val selectedSeason = seasons.firstOrNull { it.number > 0 }?.number ?: seasons.firstOrNull()?.number ?: 1
            val episodes = repository.episodes(profile, item, selectedSeason)
            if (mutableState.value.selected?.catalogId == item.catalogId) {
                mutableState.value = mutableState.value.copy(
                    seasons = seasons, season = selectedSeason, episodes = episodes, episode = null,
                    episodeRangeIndex = 0, options = emptyList(),
                )
            }
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

    private fun loadEpisodeOptions(requestId: Long) {
        val current = mutableState.value
        val profile = current.profile ?: return
        val item = current.selected ?: return
        val episode = current.episode ?: return
        val season = current.season
        viewModelScope.launch {
            try {
                val options = repository.streamOptions(profile, item, season, episode)
                if (requestId == releaseRequestId) {
                    mutableState.value = mutableState.value.copy(options = options, releaseLoading = false, releaseError = null)
                }
            } catch (error: Exception) {
                if (requestId != releaseRequestId) return@launch
                if (error is GatewayException && error.code == "AUTH_REQUIRED") forgetAfterAuthFailure()
                else mutableState.value = mutableState.value.copy(
                    releaseLoading = false, releaseError = error.message ?: "Release search failed",
                )
            }
        }
    }

    fun start(option: StreamOption, onReady: (GatewayProfile, PlaybackSession, String, String?) -> Unit) {
        val current = mutableState.value
        val profile = current.profile ?: return
        val item = current.selected ?: return
        // Retire the dialog/request generation before launching PlayerActivity so
        // no delayed callback from that window can target the next selection.
        releaseRequestId += 1
        mutableState.value = if (item.isSeries) {
            current.copy(
                episode = null,
                options = emptyList(),
                releaseSheetOpen = false,
                releaseSheetGeneration = 0L,
                releaseLoading = false,
                releaseError = null,
            )
        } else {
            current.copy(releaseSheetOpen = false, releaseSheetGeneration = 0L, releaseLoading = false)
        }
        operation {
            val session = repository.createPlayback(profile, item, option, current.season.takeIf { item.isSeries }, current.episode)
            val episodeName = current.episodes.firstOrNull { it.number == current.episode }?.name
            val episodeCode = current.episode?.toString()?.padStart(2, '0')
            val title = if (episodeName != null) "${item.title} · S${current.season.toString().padStart(2, '0')}E$episodeCode · $episodeName" else item.title
            onReady(profile, session, title, item.backdropPath ?: item.artworkPath)
        }
    }

    fun resume(entry: KeepWatchingItem, onReady: (GatewayProfile, PlaybackSession, String, String?) -> Unit) {
        val profile = mutableState.value.profile ?: return
        operation {
            val session = repository.resumeKeepWatching(profile, entry)
            onReady(profile, session, session.title ?: entry.item.title, session.backdropPath ?: entry.item.backdropPath ?: entry.item.artworkPath)
        }
    }

    fun refreshAfterPlayer() {
        val profile = mutableState.value.profile ?: return
        keepWatchingRefreshJob?.cancel()
        keepWatchingRefreshJob = viewModelScope.launch {
            delay(1_000L)
            try {
                val items = repository.keepWatching(profile)
                if (mutableState.value.profile == profile) {
                    mutableState.value = mutableState.value.copy(keepWatching = items)
                }
            } catch (error: GatewayException) {
                if (error.code == "AUTH_REQUIRED" && mutableState.value.profile == profile) {
                    forgetAfterAuthFailure()
                }
            } catch (_: Exception) {
            }
        }
    }

    fun disconnect() {
        val profile = mutableState.value.profile ?: return
        keepWatchingRefreshJob?.cancel()
        operation { repository.revokeAndClear(profile); mutableState.value = MainUiState() }
    }

    fun forgetAfterAuthFailure() {
        keepWatchingRefreshJob?.cancel()
        repository.clearLocalProfile()
        mutableState.value = MainUiState(error = "This device is no longer authorized. Pair it again from the PC.")
    }

    fun clearError() { mutableState.value = mutableState.value.copy(error = null) }

    private fun cancelOperation() {
        operationGeneration += 1
        operationJob?.cancel()
        operationJob = null
        mutableState.value = mutableState.value.copy(loading = false, transitionLoading = false)
    }

    private fun operation(showTransition: Boolean = false, block: suspend () -> Unit) {
        operationJob?.cancel()
        val generation = ++operationGeneration
        operationJob = viewModelScope.launch {
            mutableState.value = mutableState.value.copy(loading = true, transitionLoading = showTransition, error = null)
            try { block() }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                if (generation == operationGeneration) {
                    if (error is GatewayException && error.code == "AUTH_REQUIRED") forgetAfterAuthFailure()
                    else mutableState.value = mutableState.value.copy(error = error.message ?: "Request failed")
                }
            } finally {
                if (generation == operationGeneration) {
                    mutableState.value = mutableState.value.copy(loading = false, transitionLoading = false)
                    operationJob = null
                }
            }
        }
    }
}
