package com.example

import android.content.Context
import com.yausername.ffmpeg.FFmpeg

fun test(c: Context) {
    println(FFmpeg.getInstance().getBinDirectory(c)?.absolutePath)
}
