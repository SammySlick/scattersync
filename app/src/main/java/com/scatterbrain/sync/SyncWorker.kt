package com.scatterbrain.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.runBlocking

class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val summary = try {
            SyncEngine.run(applicationContext, manual = false)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
        return Result.success()
    }
}
