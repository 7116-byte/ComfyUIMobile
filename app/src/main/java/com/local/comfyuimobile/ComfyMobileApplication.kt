package com.local.comfyuimobile

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import com.local.comfyuimobile.data.AppLogger
import com.local.comfyuimobile.service.JobMonitorService
import com.local.comfyuimobile.update.UpdateManager

class ComfyMobileApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLogger.initialize(this)
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(JobMonitorService.CHANNEL_ID, getString(R.string.monitor_channel), NotificationManager.IMPORTANCE_LOW),
        )
        manager.createNotificationChannel(
            NotificationChannel(
                JobMonitorService.COMPLETION_CHANNEL_ID,
                getString(R.string.completion_channel),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = getString(R.string.completion_channel_description)
                enableVibration(true)
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(UpdateManager.CHANNEL_ID, getString(R.string.update_channel), NotificationManager.IMPORTANCE_DEFAULT),
        )
    }
}
