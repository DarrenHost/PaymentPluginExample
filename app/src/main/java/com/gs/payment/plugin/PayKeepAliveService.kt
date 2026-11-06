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
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.CountDownTimer
import android.os.IBinder
import android.provider.Settings
import android.text.InputType
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.gs.payment.plugin.receiver.StartPayReceiver
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 功能验证辅助：常驻前台服务。
 * Android 12+ 禁止后台直接 startActivity / 启动前台服务，因此改用 TYPE_APPLICATION_OVERLAY
 * 悬浮窗来呈现“密码验证页”——悬浮窗不走 Activity 启动限制，后台也能弹到前台，
 * 前提是已授予 SYSTEM_ALERT_WINDOW 权限。本服务在收到 SHOW_INPUT_ACTION 时挂出该悬浮窗。
 *
 * 悬浮窗用原生 View 构建，避免 ComposeView 在非 Activity 视图树下需要 LifecycleOwner 的问题。
 * 弹窗带 180 秒倒计时，超时自动关闭并回传失败结果。
 */
class PayKeepAliveService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var countdownTimer: CountDownTimer? = null
    private var countdownView: TextView? = null

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
            sendResult(false, "no overlay permission, please grant SYSTEM_ALERT_WINDOW", orderMoney, "", orderId)
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
        ).apply {
            gravity = Gravity.CENTER
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
        }
        try {
            windowManager?.addView(root, params)
            overlayView = root
            debugLog("> OVERLAY READY  ORDER_ID=$orderId")
            startCountdown(orderMoney, orderId)
        } catch (t: Throwable) {
            sendResult(false, "add overlay failed: ${t.message}", orderMoney, "", orderId)
        }
    }

    /**
     * 极客风终端样式：深色面板 + 霓虹青描边 + 等宽字体 + 中英双语标签。
     */
    private fun buildOverlayView(
        orderId: String,
        orderMoney: String,
        productName: String,
        productId: String,
        scanCode: String
    ): View {
        val dp = { n: Int -> (resources.displayMetrics.density * n).toInt() }
        val neon = GeekPalette.NEON
        val neonDim = GeekPalette.NEON_DIM
        val panel = GeekPalette.PANEL
        val field = GeekPalette.FIELD
        val primary = GeekPalette.TEXT_PRIMARY
        val secondary = GeekPalette.TEXT_SECONDARY
        val danger = GeekPalette.DANGER
        val mono = Typeface.MONOSPACE
        // 弹出框宽度固定为屏幕宽度的一半，遮罩仍覆盖全屏
        val cardWidth = resources.displayMetrics.widthPixels / 2

        val root = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(GeekPalette.SCRIM)
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(panel)
                cornerRadius = dp(4).toFloat()
                setStroke(dp(2), neon)
            }
            setPadding(dp(14), dp(16), dp(14), dp(14))
            layoutParams = FrameLayout.LayoutParams(
                cardWidth,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
            }
        }

        // 顶部：饮料杯图标 + 双语标题 + 呼吸状态灯
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        val cup = ContextCompat.getDrawable(this, R.drawable.ic_drink_cup)?.mutate()
        cup?.setTint(neon)
        header.addView(ImageView(this).apply {
            setImageDrawable(cup)
            layoutParams = LinearLayout.LayoutParams(dp(20), dp(20))
        })
        header.addView(TextView(this).apply {
            text = " 支付验证 / AUTH"
            typeface = mono
            textSize = 11f
            setTextColor(neon)
            setSingleLine(true)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        val statusView = TextView(this).apply {
            text = "●"
            textSize = 12f
            setTextColor(neon)
        }
        statusView.startAnimation(AlphaAnimation(0.35f, 1f).apply {
            duration = 1100
            repeatMode = Animation.REVERSE
            repeatCount = Animation.INFINITE
        })
        header.addView(statusView)
        card.addView(header)

        card.addView(divider(dp, neonDim))

        // 倒计时：每秒刷新，180 秒归零后自动关闭
        val timerView = TextView(this).apply {
            text = "> 倒计时 / TIMEOUT  ${COUNTDOWN_SECONDS}s"
            typeface = mono
            textSize = 10f
            setTextColor(neon)
            setPadding(0, 0, 0, dp(6))
        }
        countdownView = timerView
        card.addView(timerView)

        card.addView(TextView(this).apply {
            text = "> 会话 / SESSION  ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())}"
            typeface = mono
            textSize = 9f
            setTextColor(secondary)
            setPadding(0, 0, 0, dp(10))
        })

        fun addInfo(cn: String, key: String, value: String) {
            if (value.isBlank()) return
            card.addView(TextView(this).apply {
                text = "$cn / $key: $value"
                typeface = mono
                textSize = 10f
                setTextColor(primary)
                setPadding(0, 0, 0, dp(4))
            })
        }
        addInfo("订单号", "ORDER_ID", orderId)
        addInfo("金额", "AMOUNT", "¥$orderMoney")
        addInfo("商品", "PRODUCT", productName)
        addInfo("商品ID", "PRODUCT_ID", productId)
        addInfo("扫码", "SCAN_CODE", scanCode)

        card.addView(divider(dp, neonDim))

        card.addView(TextView(this).apply {
            text = "访问密钥 / ACCESS KEY"
            typeface = mono
            textSize = 10f
            setTextColor(neon)
            setPadding(0, 0, 0, dp(8))
        })

        // 仅保留密码输入框，hint 双语提示当前默认密码
        val pwdEt = EditText(this).apply {
            hint = "密码 / PWD ${PayConfig.getDefaultPassword(this@PayKeepAliveService)}"
            setHintTextColor(secondary)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            typeface = mono
            textSize = 12f
            setTextColor(primary)
            setSingleLine(true)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = GradientDrawable().apply {
                setColor(field)
                cornerRadius = dp(2).toFloat()
                setStroke(dp(1), neon)
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        card.addView(pwdEt)

        // 竖排按钮，保证双语文案在半屏宽内完整显示
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(12), 0, 0)
        }
        val okBtn = Button(this).apply {
            text = "确认支付 / AUTHORIZE"
            typeface = mono
            textSize = 10f
            setAllCaps(false)
            setPadding(dp(4), 0, dp(4), 0)
            setTextColor(GeekPalette.ON_NEON)
            background = GradientDrawable().apply {
                setColor(neon)
                cornerRadius = dp(2).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(40)
            )
            setOnClickListener {
                val input = pwdEt.text.toString()
                if (input == PayConfig.getDefaultPassword(this@PayKeepAliveService)) {
                    Log.i(TAG, "Payment authorized. orderId=$orderId")
                    debugLog("> AUTHORIZE OK  ORDER_ID=$orderId")
                    sendResult(true, "password verified", orderMoney, input, orderId)
                } else {
                    Log.i(TAG, "Payment rejected: invalid key. orderId=$orderId")
                    debugLog("> AUTHORIZE DENIED  ORDER_ID=$orderId")
                    sendResult(false, "invalid password", orderMoney, "", orderId)
                }
                removeOverlay()
            }
        }
        val cancelBtn = Button(this).apply {
            text = "取消 / CANCEL"
            typeface = mono
            textSize = 10f
            setAllCaps(false)
            setPadding(dp(4), 0, dp(4), 0)
            setTextColor(danger)
            background = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                cornerRadius = dp(2).toFloat()
                setStroke(dp(1), danger)
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(40)
            ).apply { setMargins(0, dp(8), 0, 0) }
            setOnClickListener {
                sendResult(false, "user cancel", orderMoney, "", orderId)
                removeOverlay()
            }
        }
        actions.addView(okBtn)
        actions.addView(cancelBtn)
        card.addView(actions)

        card.addView(TextView(this).apply {
            text = "// 默认密钥 / DEFAULT KEY"
            typeface = mono
            textSize = 9f
            setTextColor(secondary)
            setPadding(0, dp(10), 0, 0)
        })

        root.addView(card)
        return root
    }

    private fun divider(dp: (Int) -> Int, color: Int): View =
        View(this).apply {
            setBackgroundColor(color)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                maxOf(1, dp(1))
            ).apply { setMargins(0, dp(8), 0, dp(8)) }
        }

    /**
     * 180 秒倒计时；超时自动关闭悬浮窗并回传失败结果，避免宿主一直等待。
     */
    private fun startCountdown(orderMoney: String, orderId: String) {
        countdownTimer?.cancel()
        countdownTimer = object : CountDownTimer(COUNTDOWN_MILLIS, 1_000L) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = ((millisUntilFinished + 999L) / 1_000L).toInt()
                countdownView?.setText("> 倒计时 / TIMEOUT  ${seconds}s")
            }

            override fun onFinish() {
                if (overlayView == null) return
                countdownView?.setText("> 倒计时 / TIMEOUT  0s")
                debugLog("> TIMEOUT  ORDER_ID=$orderId")
                sendResult(false, "timeout", orderMoney, "", orderId)
                removeOverlay()
            }
        }.start()
    }

    private fun removeOverlay() {
        countdownTimer?.cancel()
        countdownTimer = null
        countdownView = null
        overlayView?.let {
            try {
                windowManager?.removeView(it)
            } catch (_: Throwable) {
            }
            overlayView = null
        }
    }

    private fun debugLog(msg: String) {
        sendBroadcast(Intent(LOG_ACTION).putExtra(LOG_MESSAGE_KEY, msg))
    }

    private fun sendResult(
        success: Boolean,
        message: String,
        money: String,
        password: String,
        orderId: String
    ) {
        val state = if (success) "success" else "fail"
        val out = Intent(StartPayReceiver.RESULT_ACTION)
            .putExtra("STATE", state)
            .putExtra("MESSAGE", message)
            .putExtra("MONEY", money)
            .putExtra("PASSWORD", password)
            .putExtra("ORDER_ID", orderId)
        Log.i(TAG, "Sending PAY_STATE_ACTION: state=$state, message=$message, orderId=$orderId")
        debugLog("< PAY_STATE_ACTION  STATE=$state  ORDER_ID=$orderId  MSG=$message")
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
            .setSmallIcon(R.drawable.ic_drink_cup)
            .setColor(GeekPalette.NEON)
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
        const val COUNTDOWN_SECONDS = 180
        private const val COUNTDOWN_MILLIS = COUNTDOWN_SECONDS * 1000L
    }
}
