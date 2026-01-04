package com.gs.payment.plugin.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
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
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED || 
            intent?.action == "android.intent.action.QUICKBOOT_POWERON") {
            if (context == null) {
                return
            }
            Log.i("BootReceiver", "开机启动，等待10秒后启动服务")

            // 延时10秒启动PaymentService
            Handler(Looper.getMainLooper()).postDelayed({
                PaymentService.start(context)
                Log.i("BootReceiver", "开机后延时10秒启动PaymentService")
            }, 10000)

            // 启动WorkManager周期性检查任务
            startMonitoring(context)
        }
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
        Log.i("BootReceiver", "启动MonitoringWorker监控任务")
    }
}