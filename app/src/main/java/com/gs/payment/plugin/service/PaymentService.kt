package com.gs.payment.plugin.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.gs.payment.plugin.R
import com.gs.payment.plugin.domain.SerialPortManager
import com.gs.payment.plugin.work.RestartWatchWorker
import java.util.concurrent.TimeUnit

/**
 * 支付服务
 * @author chyi
 * @date 2026/1/4
 */
class PaymentService : Service() {

    companion object {
        private const val CHANNEL_ID = "payment_service_channel"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, PaymentService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, PaymentService::class.java)
            context.stopService(intent)
        }
    }

    @SuppressLint("ForegroundServiceType")
    override fun onCreate() {
        super.onCreate()
        // 启动前台服务
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 打开串口（固定配置）
        // 注意：需要根据实际硬件配置修改设备路径和波特率
        SerialPortManager.openSerialPort("/dev/ttyS7", 9600)
        return START_STICKY
    }

    private fun buildNotification(): Notification {
        // Android 8.0+ 必须创建通知渠道
        createNotificationChannel()

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.payment_service_running))
            .setContentText(getString(R.string.payment_service_task_running))
            .setSmallIcon(R.drawable.ic_pay_service)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.payment_service_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.payment_service_channel_description)
            }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        // 关闭串口
        SerialPortManager.closeSerialPort()
        // 启动重启任务
        restartWork(this)
    }

    private fun restartWork(context: Context) {
        val workRequest = OneTimeWorkRequestBuilder<RestartWatchWorker>()
            .setInitialDelay(10, TimeUnit.SECONDS) // 延时10秒
            .build()

        WorkManager.getInstance(context).enqueue(workRequest)
    }
}

