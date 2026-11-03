package com.gs.payment.plugin

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.text.InputType
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.gs.payment.plugin.receiver.StartPayReceiver

/**
 * 功能验证辅助：常驻前台服务。
 * Android 12+ 禁止后台直接 startActivity / 启动前台服务，因此改用 TYPE_APPLICATION_OVERLAY
 * 悬浮窗来呈现“账号密码输入页”——悬浮窗不走 Activity 启动限制，后台也能弹到前台，
 * 前提是已授予 SYSTEM_ALERT_WINDOW 权限。本服务在收到 SHOW_INPUT_ACTION 时挂出该悬浮窗。
 *
 * 悬浮窗用原生 View 构建，避免 ComposeView 在非 Activity 视图树下需要 LifecycleOwner 的问题。
 */
class PayKeepAliveService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null

    private val showReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == SHOW_INPUT_ACTION) {
                showOverlay(
                    orderId = intent.getStringExtra(PayContract.EXTRA_ORDER_ID) ?: "",
                    orderMoney = intent.getStringExtra(PayContract.EXTRA_ORDER_MONEY) ?: "",
                    productName = intent.getStringExtra(PayContract.EXTRA_PRODUCT_NAME) ?: "",
                    productId = intent.getStringExtra(PayContract.EXTRA_PRODUCT_ID) ?: "",
                    scanCode = intent.getStringExtra(PayContract.EXTRA_SCAN_CODE) ?: ""
                )
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WindowManager::class.java)
        ContextCompat.registerReceiver(
            this,
            showReceiver,
            IntentFilter(SHOW_INPUT_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        // 支持直接带参数启动（备用）
        if (intent?.hasExtra(PayContract.EXTRA_ORDER_ID) == true) {
            showOverlay(
                orderId = intent.getStringExtra(PayContract.EXTRA_ORDER_ID) ?: "",
                orderMoney = intent.getStringExtra(PayContract.EXTRA_ORDER_MONEY) ?: "",
                productName = intent.getStringExtra(PayContract.EXTRA_PRODUCT_NAME) ?: "",
                productId = intent.getStringExtra(PayContract.EXTRA_PRODUCT_ID) ?: "",
                scanCode = intent.getStringExtra(PayContract.EXTRA_SCAN_CODE) ?: ""
            )
        }
        return START_STICKY
    }

    private fun showOverlay(
        orderId: String,
        orderMoney: String,
        productName: String,
        productId: String,
        scanCode: String
    ) {
        if (!Settings.canDrawOverlays(this)) {
            sendResult(false, "no overlay permission, please grant SYSTEM_ALERT_WINDOW", orderMoney, "", "", orderId)
            return
        }
        removeOverlay()
        val root = buildOverlayView(
            orderId = orderId,
            orderMoney = orderMoney,
            productName = productName,
            productId = productId,
            scanCode = scanCode
        )
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER }
        overlayView = root
        try {
            windowManager?.addView(root, params)
        } catch (t: Throwable) {
            sendResult(false, "add overlay failed: ${t.message}", orderMoney, "", "", orderId)
        }
    }

    private fun buildOverlayView(
        orderId: String,
        orderMoney: String,
        productName: String,
        productId: String,
        scanCode: String
    ): View {
        val dp = { n: Int -> (resources.displayMetrics.density * n).toInt() }

        val root = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#80000000"))
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val bg = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(16).toFloat()
            }
            background = bg
            val pad = dp(20)
            setPadding(pad, pad, pad, pad)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
                val m = dp(28)
                setMargins(m, m, m, m)
            }
        }

        fun addInfo(label: String) {
            card.addView(
                TextView(this).apply {
                    text = label
                    textSize = 14f
                    setPadding(0, 0, 0, dp(6))
                }
            )
        }

        card.addView(
            TextView(this).apply {
                text = "支付确认"
                textSize = 20f
                setTextColor(Color.BLACK)
                setPadding(0, 0, 0, dp(12))
            }
        )
        addInfo("订单号: $orderId")
        addInfo("金额: ¥$orderMoney")
        if (productName.isNotBlank()) addInfo("商品: $productName")
        if (productId.isNotBlank()) addInfo("商品ID: $productId")
        if (scanCode.isNotBlank()) addInfo("扫码: $scanCode")

        val accountEt = EditText(this).apply {
            hint = "账号"
            inputType = InputType.TYPE_CLASS_TEXT
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setBackgroundColor(Color.parseColor("#F2F2F2"))
        }
        card.addView(accountEt)

        val pwdEt = EditText(this).apply {
            hint = "密码"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setBackgroundColor(Color.parseColor("#F2F2F2"))
        }
        card.addView(pwdEt)

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dp(12), 0, 0)
        }
        val cancelBtn = Button(this).apply {
            text = "取消"
            setOnClickListener {
                sendResult(false, "user cancel", orderMoney, "", "", orderId)
                removeOverlay()
            }
        }
        val okBtn = Button(this).apply {
            text = "确认支付"
            isEnabled = true
            setOnClickListener {
                val account = accountEt.text.toString()
                val password = pwdEt.text.toString()
                // 确认时打印输入内容日志（logcat + 调试页日志）
                Log.i(TAG, "Confirm input. account=$account, password=$password")
                //处理账号，密码事件

//                val logIntent = Intent(LOG_ACTION)
//                    .putExtra(LOG_MESSAGE_KEY, "确认输入: account=$account, password=$password")
//                sendBroadcast(logIntent)
//                sendResult(true, "user input", orderMoney, account, password, orderId)
                removeOverlay()
            }
        }
        row.addView(cancelBtn)
        val spacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(12), 0)
        }
        row.addView(spacer)
        row.addView(okBtn)
        card.addView(row)

        root.addView(card)
        return root
    }

    private fun removeOverlay() {
        overlayView?.let {
            try {
                windowManager?.removeView(it)
            } catch (_: Throwable) {
            }
            overlayView = null
        }
    }

    private fun sendResult(
        success: Boolean,
        message: String,
        money: String,
        account: String,
        password: String,
        orderId: String
    ) {
        val out = Intent(StartPayReceiver.RESULT_ACTION)
            .putExtra("STATE", if (success) "success" else "fail")
            .putExtra("MESSAGE", message)
            .putExtra("MONEY", money)
            .putExtra("ACCOUNT", account)
            .putExtra("PASSWORD", password)
            .putExtra("ORDER_ID", orderId)
        sendBroadcast(out)
    }

    private fun buildNotification(): Notification {
        val channelId = "pay_keepalive_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                channelId,
                "支付常驻服务",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("支付常驻已启用")
            .setContentText("允许在后台收到支付请求时弹出输入窗")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        removeOverlay()
        unregisterReceiver(showReceiver)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "PaymentPlugin.Overlay"
        const val SHOW_INPUT_ACTION = "com.coffeeji.payment.plugin.SHOW_INPUT_ACTION"
        const val LOG_ACTION = "com.gs.payment.plugin.LOG_ACTION"
        const val LOG_MESSAGE_KEY = "LOG_MESSAGE_KEY"
        private const val NOTIFICATION_ID = 1003
    }
}