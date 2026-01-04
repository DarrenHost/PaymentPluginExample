package com.gs.payment.plugin.work

import android.app.ActivityManager
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import android.util.Log
import com.gs.payment.plugin.service.PaymentService

/**
 * 重启服务的Worker
 * 在服务销毁时被调用，延时重启服务
 * @author chyi
 * @date 2026/1/4
 */
class RestartWatchWorker(
   private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            Log.i("RestartWatchWorker", "应用关闭后启动服务")

            if (isServiceRunning()) {
                Log.i("RestartWatchWorker", "PaymentService正在运行")
            } else {
                Log.w("RestartWatchWorker", "PaymentService未运行，尝试重新启动")
                PaymentService.start(context)
            }
            Result.success()
        } catch (e: Exception) {
            Log.e("RestartWatchWorker", "应用关闭后启动服务失败", e)
            Result.failure()
        }
    }

    /**
     * 检查PaymentService是否在运行
     */
    private fun isServiceRunning(): Boolean {
        return try {
            val activityManager =
                context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val runningServices = activityManager.getRunningServices(Integer.MAX_VALUE)

            val serviceClassName = PaymentService::class.java.name
            runningServices.any { serviceInfo ->
                serviceInfo.service.className == serviceClassName
            }
        } catch (e: Exception) {
            Log.e("RestartWatchWorker", "检查服务运行状态异常", e)
            false
        }
    }
}

