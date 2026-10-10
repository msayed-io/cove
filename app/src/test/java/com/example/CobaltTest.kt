package com.example

import org.junit.Test
import org.junit.Assert.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

class CobaltTest {
    @Test
    fun testCobalt() {
        val client = OkHttpClient()
        val jsonBody = """{"url": "https://www.youtube.com/watch?v=aqz-KE-bpKQ"}"""
        val body = jsonBody.toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("https://api.cobalt.tools/")
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .post(body)
            .build()
            
        val response = client.newCall(request).execute()
        println("COBALT SUCCESS: " + response.isSuccessful)
        println("COBALT BODY: " + response.body?.string())
    }
}
