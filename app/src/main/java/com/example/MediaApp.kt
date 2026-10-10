package com.example

import android.app.Application
import android.util.Log
import com.example.api.NetworkModule
import com.example.api.OkHttpDownloader
import org.schabi.newpipe.extractor.NewPipe
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.ffmpeg.FFmpeg
import com.yausername.aria2c.Aria2c
import kotlinx.coroutines.launch

class MediaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        
        NetworkModule.applicationContext = this
        
        try {
            NewPipe.init(OkHttpDownloader(NetworkModule.okHttpClient))
        } catch (e: Throwable) {
            Log.e("MediaApp", "Failed to init NewPipe", e)
        }

        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                YoutubeDL.getInstance().init(this@MediaApp)
                FFmpeg.getInstance().init(this@MediaApp)
                Aria2c.getInstance().init(this@MediaApp)
                
                try {
                    // Start with NIGHTLY to ensure we have the absolute latest bot bypasses for YouTube
                    YoutubeDL.getInstance().updateYoutubeDL(this@MediaApp, YoutubeDL.UpdateChannel.NIGHTLY)
                    Log.d("MediaApp", "YoutubeDL updated successfully to NIGHTLY channel")
                } catch (e: Throwable) {
                    Log.w("MediaApp", "Failed to update YoutubeDL to NIGHTLY channel, trying STABLE", e)
                    try {
                        YoutubeDL.getInstance().updateYoutubeDL(this@MediaApp, YoutubeDL.UpdateChannel.STABLE)
                    } catch (e2: Throwable) {
                        Log.w("MediaApp", "Failed to update YoutubeDL to STABLE channel as well", e2)
                    }
                }
                
                Log.d("MediaApp", "Tools initialized successfully")
            } catch (e: Throwable) {
                Log.e("MediaApp", "Failed to init youtubedl-android tools", e)
            }
        }
    }
}
