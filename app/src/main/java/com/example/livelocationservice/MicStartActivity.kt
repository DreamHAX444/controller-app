package com.example.livelocationservice

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.core.content.ContextCompat

class MicStartActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            ContextCompat.startForegroundService(
                this,
                Intent(this, AudioUplinkService::class.java)
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
        finish()
    }
}
