package com.gs.payment.plugin.receiver

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.gs.payment.plugin.utils.Logger

class StartPayReceiver : BaseBroadReceiver() {

    companion object {
        private const val TAG = "PaymentPlugin.StartPayReceiver"

        const val ACTION = "com.coffeeji.payment.plugin.PAY_ACTON"

        const val RESULT_ACTION = "com.coffeeji.payment.plugin.PAY_STATE_ACTION"
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        Logger.i(TAG, "Received intent action: ${intent.action}")
        log("Received intent action: ${intent.action}")
        if (intent.action != ACTION) return

        val orderId = intent.getStringExtra("ORDER_ID")
        val orderMoney = intent.getStringExtra("ORDER_MONEY")
        val productId = intent.getStringExtra("PRODUCT_ID")
        val productName = intent.getStringExtra("PRODUCT_NAME")
        val scanCode = intent.getStringExtra("SCAN_CODE")
        Logger.i(TAG, "PAY_ACTON received. ORDER_ID=${orderId}")
        Logger.i(TAG, "PAY_ACTON received. ORDER_MONEY=${orderMoney}")
        Logger.i(TAG, "PAY_ACTON received. PRODUCT_ID=${productId}")
        Logger.i(TAG, "PAY_ACTON received. PRODUCT_NAME=${productName}")
        Logger.i(TAG, "PAY_ACTON received. SCAN_CODE=${scanCode}")

        log("PAY_ACTON received. ORDER_ID=${orderId}")
        log("PAY_ACTON received. ORDER_MONEY=${orderMoney}")
        log("PAY_ACTON received. PRODUCT_ID=${productId}")
        log("PAY_ACTON received. PRODUCT_NAME=${productName}")
        log("PAY_ACTON received. SCAN_CODE=${scanCode}")

        if (orderId.isNullOrBlank()) {
            sendResult(context, false, "invalid orderId", "")
            return
        }

        Handler(Looper.getMainLooper())
            .postDelayed({
                try {
                    sendResult(context, true, "", "11.0")
                } catch (t: Throwable) {
                    Logger.e(TAG, "Error while sending PAY_STATE_ACTION", t)
                    log("Error while sending PAY_STATE_ACTION:${t.message}")
                    sendResult(context, false, "internal error", "11.0")
                }
            }, 5 * 1000)
    }

    private fun sendResult(
        ctx: Context,
        success: Boolean,
        message: String,
        money: String
    ) {
        val out = Intent(RESULT_ACTION)
            .putExtra("STATE", if (success) "success" else "fail")
            .putExtra("MESSAGE", message)
            .putExtra("MONEY", money)
        Logger.i(
            TAG,
            "Sending PAY_STATE_ACTION: status=${if (success) "success" else "fail"}, message=$message, money=$money"
        )
        log("Sending PAY_STATE_ACTION: status=${if (success) "success" else "fail"}, message=$message, money=$money")
        ctx.sendBroadcast(out)
    }
}
