package com.example.billingapps

import android.app.Application
import android.util.Log

class MyApp : Application() {

    companion object {
        const val BE_URL = "https://billing.klikrf.com/"
        const val TOKEN = "supersecrettoken123"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("MyApp", "Application created. BE_URL: $BE_URL  dan token $TOKEN")
    }
}
