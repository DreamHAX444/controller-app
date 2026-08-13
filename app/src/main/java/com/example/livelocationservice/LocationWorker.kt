package com.example.livelocationservice

import android.content.Context
import android.content.Intent
import androidx.work.Worker
import androidx.work.WorkerParameters

class LocationWorker(appContext: Context, workerParams: WorkerParameters) :
    Worker(appContext, workerParams) {

    override fun doWork(): Result {
        return try {
            val intent = Intent(applicationContext, LiveLocationService::class.java)
            androidx.core.content.ContextCompat.startForegroundService(applicationContext, intent)
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}
