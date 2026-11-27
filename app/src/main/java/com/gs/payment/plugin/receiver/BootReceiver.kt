package com.gs.payment.plugin.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.gs.payment.plugin.utils.Logger
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.gs.payment.plugin.service.PaymentService
import com.gs.payment.plugin.work.MonitoringWorker
import java.util.concurrent.TimeUnit

/**
 * 开机自启服务
 * @author chyi
 * @date 2026/1/4 11:33
 */
class BootReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "BootReceiver"
        private const val ACTION_QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != ACTION_QUICKBOOT_POWERON) return

        val appContext = context?.applicationContext ?: return
        Logger.i(TAG, "开机启动，立即启动PaymentService")

        // BroadcastReceiver 生命周期很短，Handler 延时任务会随进程被回收而丢失，
        // 因此这里必须立即启动服务；监控任务用 WorkManager 延迟执行。
        try {
            PaymentService.start(appContext)
            Logger.i(TAG, "开机后已启动PaymentService")
        } catch (e: Exception) {
            Logger.e(TAG, "开机启动PaymentService失败", e)
        }

        startMonitoring(appContext)
    }

    private fun startMonitoring(context: Context) {
        // 每15分钟检查一次PaymentService是否在运行
        val request = PeriodicWorkRequestBuilder<MonitoringWorker>(
            15, TimeUnit.MINUTES
        ).setInitialDelay(30, TimeUnit.SECONDS).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "payment_service_monitoring",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
        Logger.i(TAG, "启动MonitoringWorker监控任务")
    }
}