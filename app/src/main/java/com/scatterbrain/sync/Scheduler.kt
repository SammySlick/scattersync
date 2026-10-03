package com.scatterbrain.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object Scheduler {
    fun ensure(ctx: Context) {
        val req = PeriodicWorkRequestBuilder<SyncWorker>(30, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .build()
        // KEEP, not UPDATE: UPDATE cancels any in-flight run every time the app
        // opens — Sam's 12:23 sync died with JobCancellationException because he
        // opened the app mid-run. KEEP leaves a scheduled job untouched, so a
        // background run is never murdered by merely opening the app.
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
            "scattersync", ExistingPeriodicWorkPolicy.KEEP, req)
    }
}
