package com.koretide.app

import android.app.Application
import com.koretide.app.util.CrashLogger
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class KoreTideApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLogger.install(this)
    }
}
