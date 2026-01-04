package com.gs.payment.plugin.work

import android.app.ActivityManager
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.gs.payment.plugin.service.PaymentService
import com.gs.payment.plugin.utils.Logger

/**
 * 监控服务运行状态的Worker
 * 周期性检查PaymentService是否运行，如果未运行则重新启动
 * @author chyi
 * @date 2026/1/4
 */
class MonitoringWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            Logger.i("MonitoringWorker", "开始检查PaymentService运行状态")
            checkService()
            Result.success()
        } catch (e: Exception) {
            Logger.e("MonitoringWorker", "检查服务运行状态失败", e)
            Result.failure()
        }
    }

    /**
     * 检查PaymentService是否在运行
     */
    private fun checkService() {
        try {
            if (isServiceRunning()) {
                Logger.i("MonitoringWorker", "PaymentService正在运行")
            } else {
                Logger.w("MonitoringWorker", "PaymentService未运行，尝试重新启动")
                PaymentService.start(applicationContext)
            }
        } catch (e: Exception) {
            Logger.e("MonitoringWorker", "检查服务运行状态失败", e)
        }
    }

    /**
     * 检查PaymentService是否在运行
     */
    private fun isServiceRunning(): Boolean {
        return try {
            val activityManager =
                applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val runningServices = activityManager.getRunningServices(Integer.MAX_VALUE)

            val serviceClassName = PaymentService::class.java.name
            runningServices.any { serviceInfo ->
                serviceInfo.service.className == serviceClassName
            }
        } catch (e: Exception) {
            Logger.e("MonitoringWorker", "检查服务运行状态异常", e)
            false
        }
    }
}

