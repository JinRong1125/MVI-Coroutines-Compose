package com.jinrong.mvi.mvicoroutinescompose

import android.app.Application
import org.koin.core.context.startKoin

class MviApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            modules(serviceModule)
        }
    }
}