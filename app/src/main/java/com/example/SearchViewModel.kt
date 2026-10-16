package com.example

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.api.NetworkModule
import com.example.domain.SearchResult
import com.example.domain.StreamInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class SearchUiState {
    object Idle : SearchUiState()
    object Loading : SearchUiState()
    data class Success(val results: List<SearchResult>) : SearchUiState()
    data class Error(val message: String) : SearchUiState()
    data class Blocked(val message: String, val hadith: String, val prayer: String) : SearchUiState()
}

sealed class StreamResolveState {
    object Idle : StreamResolveState()
    data class Loading(val videoId: String) : StreamResolveState()
    data class Ready(val info: StreamInfo, val forDownload: Boolean) : StreamResolveState()
    data class Error(val message: String) : StreamResolveState()
}

class SearchViewModel(application: Application) : AndroidViewModel(application) {

    private val sharedPrefs: SharedPreferences by lazy {
        getApplication<Application>().getSharedPreferences("search_history_prefs", Context.MODE_PRIVATE)
    }

    private val _searchHistory = MutableStateFlow<List<String>>(emptyList())
    val searchHistory: StateFlow<List<String>> = _searchHistory.asStateFlow()

    init {
        loadSearchHistory()
    }

    private fun loadSearchHistory() {
        val historyString = sharedPrefs.getString("search_history_key", "") ?: ""
        if (historyString.isNotEmpty()) {
            _searchHistory.value = historyString.split("\n").filter { it.isNotBlank() }
        } else {
            _searchHistory.value = emptyList()
        }
    }

    private fun saveSearchHistory(history: List<String>) {
        sharedPrefs.edit()
            .putString("search_history_key", history.joinToString("\n"))
            .apply()
        _searchHistory.value = history
    }

    fun addSearchQueryToHistory(queryToAdd: String) {
        val trimmed = queryToAdd.trim()
        if (trimmed.isEmpty()) return
        
        val currentHistory = _searchHistory.value.toMutableList()
        currentHistory.remove(trimmed)
        currentHistory.add(0, trimmed)
        
        if (currentHistory.size > 15) {
            currentHistory.removeAt(currentHistory.size - 1)
        }
        
        saveSearchHistory(currentHistory)
    }

    fun removeSearchQueryFromHistory(queryToRemove: String) {
        val currentHistory = _searchHistory.value.toMutableList()
        currentHistory.remove(queryToRemove)
        saveSearchHistory(currentHistory)
    }

    fun clearSearchHistory() {
        saveSearchHistory(emptyList())
    }

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _state = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private val _resolveState = MutableStateFlow<StreamResolveState>(StreamResolveState.Idle)
    val resolveState: StateFlow<StreamResolveState> = _resolveState.asStateFlow()

    private val _suggestions = MutableStateFlow<List<String>>(emptyList())
    val suggestions: StateFlow<List<String>> = _suggestions.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _searchSuggestionsActive = MutableStateFlow(false)
    val searchSuggestionsActive: StateFlow<Boolean> = _searchSuggestionsActive.asStateFlow()

    private var searchJob: Job? = null
    private var suggestionsJob: Job? = null

    /**
     * Background warm-up for the first search result.
     *
     * Resolving a playable stream takes a moment; doing it right after the results render means
     * tapping the top result starts playback immediately instead of waiting on the resolver.
     */
    private var playbackWarmUpJob: Job? = null

    // Use cases from NetworkModule (Manual DI)
    private val searchUseCase = NetworkModule.searchUseCase
    private val getStreamUseCase = NetworkModule.getStreamUseCase
    private val getPlaybackStreamUseCase = NetworkModule.getPlaybackStreamUseCase
    private val getSuggestionsUseCase = NetworkModule.getSuggestionsUseCase

    fun onQueryChange(newQuery: String, fetchAutocomplete: Boolean = true) {
        _query.value = newQuery
        
        if (newQuery.isBlank()) {
            _suggestions.value = emptyList()
            _searchSuggestionsActive.value = false
            return
        }

        if (ContentFilter.containsBlockedContent(newQuery)) {
            _suggestions.value = emptyList()
            _searchSuggestionsActive.value = false
            return
        }

        if (fetchAutocomplete) {
            _searchSuggestionsActive.value = true
            suggestionsJob?.cancel()
            suggestionsJob = viewModelScope.launch {
                delay(200) // Fast debounce for suggestions
                val result = getSuggestionsUseCase(newQuery)
                _suggestions.value = result.filter { !ContentFilter.containsBlockedContent(it) }
            }
        }
    }

    fun setSearching(searching: Boolean) {
        _isSearching.value = searching
        if (!searching) {
            // Reset to default home state
            _query.value = ""
            _state.value = SearchUiState.Idle
            _suggestions.value = emptyList()
            _searchSuggestionsActive.value = false
        }
    }

    fun performSearch(searchQuery: String) {
        _query.value = searchQuery
        _isSearching.value = true
        _searchSuggestionsActive.value = false
        _suggestions.value = emptyList()
        
        if (ContentFilter.containsBlockedContent(searchQuery)) {
            _state.value = SearchUiState.Blocked(
                message = "عذراً، يُمنع البحث عن الأغاني والموسيقى والمحتوى الموسيقي تماشياً مع قيمنا وأخلاقياتنا الإسلامية وحرصاً على عدم كسب الذنوب.",
                hadith = "قال رسول الله ﷺ: «ليكونن من أمتي أقوام يستحلون الحر والحرير والخمر والمعازف» (رواه البخاري في صحيحه، والمعازف هي آلات اللهو والموسيقى).",
                prayer = "نسأل الله العلي العظيم أن يهدينا وإياك، وأن يجنبنا الفتن والمحرمات ما ظهر منها وما بطن، ويرزقنا الهداية والتقى وصلاح الأعمال."
            )
            return
        }
        
        addSearchQueryToHistory(searchQuery)
        
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _state.value = SearchUiState.Loading
            try {
                val results = searchUseCase(searchQuery.trim())
                val filteredResults = results.filter { result ->
                    !ContentFilter.containsBlockedContent(result.title) && 
                    !ContentFilter.containsBlockedContent(result.uploaderName)
                }
                _state.value = if (filteredResults.isEmpty()) {
                    if (results.isNotEmpty()) {
                        SearchUiState.Blocked(
                            message = "عذراً، تم حجب وتصفية نتائج البحث بالكامل لتضمنها مواد وأغانٍ موسيقية تماشياً مع قيمنا الإسلامية.",
                            hadith = "قال رسول الله ﷺ: «ليكونن من أمتي أقوام يستحلون الحر والحرير والخمر والمعازف» (رواه البخاري في صحيحه).",
                            prayer = "نسأل الله العلي العظيم أن يهدينا وإياك، وأن يجنبنا الفتن والمحرمات، ويرزقنا الهداية والتوفيق والعمل الصالح."
                        )
                    } else {
                        SearchUiState.Error("لا توجد نتائج مطابقة لبحثك...")
                    }
                } else {
                    warmUpFirstResultPlayback(filteredResults)
                    SearchUiState.Success(filteredResults)
                }
            } catch (e: Exception) {
                _state.value = SearchUiState.Error("فشل البحث: ${e.localizedMessage}")
            }
        }
    }

    fun prepareDownload(result: SearchResult) {
        val info = StreamInfo(
            videoId = result.videoId,
            title = result.title,
            primaryPlayUrl = "", // Not needed for download
            videoUrl = null,
            audioUrl = null,
            uploader = result.uploaderName,
            thumbnailUrl = result.thumbnailUrl,
            durationSeconds = result.durationSeconds
        )
        _resolveState.value = StreamResolveState.Ready(info, forDownload = true)
    }
    
    fun resolveStream(videoId: String, forDownload: Boolean) {
        _resolveState.value = StreamResolveState.Loading(videoId)
        viewModelScope.launch {
            try {
                val info = getStreamUseCase(videoId)
                if (info != null) {
                    _resolveState.value = StreamResolveState.Ready(info, forDownload)
                } else {
                    _resolveState.value = StreamResolveState.Error("تعذر الحصول على روابط التشغيل")
                }
            } catch (e: Exception) {
                _resolveState.value = StreamResolveState.Error("حدث خطأ: ${e.localizedMessage}")
            }
        }
    }
    
    fun resetResolveState() {
        _resolveState.value = StreamResolveState.Idle
    }

    /**
     * Pre-resolves the stream URL of the first result (silently, in the background) so the very
     * first tap on a search result plays instantly. Any previous warm-up is cancelled first, and
     * failures stay invisible — the normal resolver still runs when the user actually taps.
     */
    private fun warmUpFirstResultPlayback(results: List<SearchResult>) {
        val first = results.firstOrNull() ?: return
        playbackWarmUpJob?.cancel()
        playbackWarmUpJob = viewModelScope.launch {
            try {
                getPlaybackStreamUseCase(first.videoId)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                // Warm-up is best-effort only.
            }
        }
    }
}
