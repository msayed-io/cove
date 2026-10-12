package com.example.api

import com.example.data.SearchRepository
import com.example.domain.GetStreamUseCase
import com.example.domain.SearchUseCase
import com.example.domain.GetSuggestionsUseCase
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient

object NetworkModule {
    var applicationContext: android.content.Context? = null
    
    val json: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    val okHttpClient by lazy {
        val dispatcher = okhttp3.Dispatcher().apply {
            maxRequests = 64
            maxRequestsPerHost = 16
        }
        val pool = okhttp3.ConnectionPool(32, 10, java.util.concurrent.TimeUnit.MINUTES)
        
        val builder = OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .connectionPool(pool)
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)

        applicationContext?.let { ctx ->
            try {
                val cacheDir = java.io.File(ctx.cacheDir, "http_cache")
                builder.cache(okhttp3.Cache(cacheDir, 25 * 1024 * 1024L)) // 25 MB Cache
            } catch (e: Exception) {
                android.util.Log.e("NetworkModule", "Failed to setup cache", e)
            }
        }

        builder.build()
    }

    // Singleton repository and use cases for manual DI
    val searchRepository by lazy { SearchRepository() }
    val downloadRepository by lazy { com.example.data.DownloadRepository() }
    val searchUseCase by lazy { SearchUseCase(searchRepository) }
    val getStreamUseCase by lazy { GetStreamUseCase(searchRepository) }
    val getSuggestionsUseCase by lazy { GetSuggestionsUseCase(searchRepository) }
}

