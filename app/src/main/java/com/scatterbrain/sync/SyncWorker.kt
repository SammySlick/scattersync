package com.scatterbrain.sync

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        // Promote to a foreground service while syncing: Health Connect refuses
        // series reads from anything not foreground, and the app is closed when
        // the 30-min scheduler fires. The notification is Android's way of
        // making that exemption visible to the user.
        try {
            setForeground(fgInfo())
        } catch (e: Exception) {
            e.printStackTrace() // if FGS fails (e.g. notification permission off),
            // still attempt the sync — it may get partial results or fail with
            // the usual "must be in foreground" message shown in the app.
        }
        val summary = try {
            SyncEngine.run(applicationContext, manual = false)
        } catch (e: Exception) {
            e.printStackTrace()
            "Background sync crashed: ${e.javaClass.simpleName}: ${e.message ?: "(no message)"}"
        }
        // Persist the result so the status screen shows real background activity.
        Prefs.setLastBgMs(applicationContext, System.currentTimeMillis())
        Prefs.setLastBgSummary(applicationContext, summary ?: "(no result)")
        return Result.success()
    }

    private fun fgInfo(): ForegroundInfo {
        val ctx = applicationContext
        if (Build.VERSION.SDK_INT >= 26) {
            val mgr = NotificationManagerCompat.from(ctx)
            mgr.createNotificationChannel(
                android.app.NotificationChannel("sync", "Syncing", android.app.NotificationManager.IMPORTANCE_MIN))
        }
        val notif = NotificationCompat.Builder(ctx, "sync")
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle("ScatterSync")
            .setContentText("Syncing health data…")
            .setOngoing(true)
            .build()
        return if (Build.VERSION.SDK_INT >= 34) {
            ForegroundInfo(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)
        } else {
            ForegroundInfo(NOTIF_ID, notif)
        }
    }

    companion object { const val NOTIF_ID = 4242 }
}
