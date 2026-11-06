package com.gs.payment.plugin.receiver

import android.content.Context
import android.content.Intent
import android.util.Log
import com.gs.payment.plugin.PayContract
import com.gs.payment.plugin.PayKeepAliveService

class StartPayReceiver : BaseBroadReceiver() {

    companion object {
        private const val TAG = "PaymentPlugin.StartPayReceiver"

        const val ACTION = "com.coffeeji.payment.plugin.PAY_ACTION"

        const val RESULT_ACTION = "com.coffeeji.payment.plugin.PAY_STATE_ACTION"
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        Log.i(TAG, "Received intent action: ${intent.action}")
        log("Received intent action: ${intent.action}")
        if (intent.action != ACTION) return

        val orderId = intent.getStringExtra("ORDER_ID")
        val orderMoney = intent.getStringExtra("ORDER_MONEY")
        val productId = intent.getStringExtra("PRODUCT_ID")
        val productName = intent.getStringExtra("PRODUCT_NAME")
        val scanCode = intent.getStringExtra("SCAN_CODE")
        Log.i(TAG, "PAY_ACTION received. ORDER_ID=${orderId}")
        Log.i(TAG, "PAY_ACTION received. ORDER_MONEY=${orderMoney}")
        Log.i(TAG, "PAY_ACTION received. PRODUCT_ID=${productId}")
        Log.i(TAG, "PAY_ACTION received. PRODUCT_NAME=${productName}")
        Log.i(TAG, "PAY_ACTION received. SCAN_CODE=${scanCode}")

        log("PAY_ACTION received. ORDER_ID=${orderId}")
        log("PAY_ACTION received. ORDER_MONEY=${orderMoney}")
        log("PAY_ACTION received. PRODUCT_ID=${productId}")
        log("PAY_ACTION received. PRODUCT_NAME=${productName}")
        log("PAY_ACTION received. SCAN_CODE=${scanCode}")

        if (orderId.isNullOrBlank()) {
            sendResult(context, false, "invalid orderId", "", "", "")
            return
        }

        // 功能验证：在后台收到支付广播后，交由常驻前台服务用 TYPE_APPLICATION_OVERLAY
        try {
            val showIntent = Intent(PayKeepAliveService.SHOW_INPUT_ACTION).apply {
                putExtra(PayContract.EXTRA_ORDER_ID, orderId)
                putExtra(PayContract.EXTRA_ORDER_MONEY, orderMoney)
                putExtra(PayContract.EXTRA_PRODUCT_ID, productId)
                putExtra(PayContract.EXTRA_PRODUCT_NAME, productName)
                putExtra(PayContract.EXTRA_SCAN_CODE, scanCode)
            }
            context.sendBroadcast(showIntent)
            log("Forwarded SHOW_INPUT_ACTION to PayKeepAliveService (overlay)")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to forward SHOW_INPUT_ACTION", t)
            log("Failed to forward SHOW_INPUT_ACTION: ${t.message}")
            sendResult(context, false, "forward failed", orderMoney ?: "", "", orderId)
        }
    }

    private fun sendResult(
        ctx: Context,
        success: Boolean,
        message: String,
        money: String,
        password: String,
        orderId: String
    ) {
        val state = if (success) "success" else "fail"
        val out = Intent(RESULT_ACTION)
            .putExtra("STATE", state)
            .putExtra("MESSAGE", message)
            .putExtra("MONEY", money)
            .putExtra("PASSWORD", password)
            .putExtra("ORDER_ID", orderId)
        Log.i(TAG, "Sending PAY_STATE_ACTION: state=$state, message=$message, orderId=$orderId")
        log("Sending PAY_STATE_ACTION: state=$state, message=$message, orderId=$orderId")
        ctx.sendBroadcast(out)
    }
}
