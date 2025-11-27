package com.example.comictranslate

import android.app.Application
import org.opencv.android.OpenCVLoader

class MyApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        if (!OpenCVLoader.initDebug()) {
            println("OpenCV load failed")
        } else {
            println("OpenCV loaded successfully")
        }
    }
}