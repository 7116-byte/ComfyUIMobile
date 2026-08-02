package com.local.comfyuimobile.service

import android.content.Intent
import com.local.comfyuimobile.model.AppDestination

/** 通知点击必须复用已有主页面，不能重新创建一套应用状态。 */
object JobNotificationNavigation {
    val activityFlags: Int =
        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP

    fun destination(completed: Boolean): AppDestination =
        if (completed) AppDestination.RESULTS else AppDestination.PARAMETERS

    fun requestCode(promptId: String): Int = promptId.hashCode()

    fun completionTitle(localSaveRequested: Boolean, savedCount: Int, failed: Boolean): String = when {
        localSaveRequested && failed -> "本地保存失败"
        localSaveRequested -> "本地保存完成，共 $savedCount 项"
        failed -> "生成失败"
        else -> "生成完成"
    }
}
