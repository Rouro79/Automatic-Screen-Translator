package com.example.comictranslate // Use your app's package name

import android.app.Application
import org.opencv.android.OpenCVLoader

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize OpenCV
        OpenCVLoader.initDebug()
    }
}