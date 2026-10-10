package com.example.domain

import com.example.data.SearchRepository

class SearchUseCase(private val repository: SearchRepository) {
    suspend operator fun invoke(query: String): List<SearchResult> =
        repository.search(query)
}

class GetStreamUseCase(private val repository: SearchRepository) {
    suspend operator fun invoke(videoId: String): StreamInfo? =
        repository.getStreams(videoId)
}

class GetSuggestionsUseCase(private val repository: SearchRepository) {
    suspend operator fun invoke(query: String): List<String> =
        repository.getSuggestions(query)
}
