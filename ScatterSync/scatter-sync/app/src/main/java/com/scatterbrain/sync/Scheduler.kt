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
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
            "scattersync", ExistingPeriodicWorkPolicy.UPDATE, req)
    }
}
