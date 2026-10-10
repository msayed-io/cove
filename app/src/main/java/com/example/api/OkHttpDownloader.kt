package com.example.api

import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody

class OkHttpDownloader(private val client: OkHttpClient) : Downloader() {
    override fun execute(request: Request): Response {
        val method = request.httpMethod()
        val data = request.dataToSend()
        
        val body = if (method == "GET" || method == "HEAD") {
            null
        } else {
            val bodyData = data ?: ByteArray(0)
            bodyData.toRequestBody(null, 0, bodyData.size)
        }

        val builder = okhttp3.Request.Builder()
            .url(request.url())
            .method(method, body)

        request.headers()?.forEach { (key, values) ->
            values.forEach { value ->
                builder.addHeader(key, value)
            }
        }

        val response = client.newCall(builder.build()).execute()
        return Response(
            response.code,
            response.message,
            response.headers.toMultimap(),
            response.body?.string() ?: "",
            request.url()
        )
    }
}
