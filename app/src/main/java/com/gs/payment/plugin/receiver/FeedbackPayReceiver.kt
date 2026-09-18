package com.gs.payment.plugin.receiver

import android.content.Context
import android.content.Intent
import android.util.Log
import com.gs.payment.plugin.OrderFailReasonEnum


class FeedbackPayReceiver : BaseBroadReceiver() {

    companion object {
        private const val TAG = "PaymentPlugin.FeedbackPayReceiver"

        const val ACTION = "com.coffeeji.payment.plugin.MAKE_STATE_ACTION"
    }

    var ORDER_FAIL_REASON_PRE: String = "A55A061F"

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        Log.i(TAG, "Received intent action: ${intent.action}")
        log("Received intent action: ${intent.action}")
        if (intent.action != ACTION) return

        val orderId = intent.getStringExtra("ORDER_ID")
        val orderMoney = intent.getStringExtra("ORDER_MONEY")
        val state = intent.getStringExtra("STATE")
        val remake = intent.getStringExtra("REMAKE")
        val productName = intent.getStringExtra("PRODUCT_NAME")
        Log.i(TAG, "MAKE_STATE_ACTION received. PRODUCT_NAME=${productName}")
        Log.i(TAG, "MAKE_STATE_ACTION received. ORDER_ID=${orderId}")
        Log.i(TAG, "MAKE_STATE_ACTION received. ORDER_MONEY=${orderMoney}")
        Log.i(TAG, "MAKE_STATE_ACTION received. STATE=${state}")
        Log.i(TAG, "MAKE_STATE_ACTION received. REMAKE=${remake}")
        if (remake != null) {
            if (remake.isNotEmpty()){
                try {
                    if (remake.startsWith(ORDER_FAIL_REASON_PRE)) {
                        val code = getRemake(remake.replace(ORDER_FAIL_REASON_PRE, "").substring(0, 2))
                        val reason = getEnNameByType(code)
                        log("Order fail reason: $reason")
                    } else {
                        log("Order fail reason: $remake")
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "Invalid REMAKE format: $remake", e)
                }
            }
        }

    }

    private fun getEnNameByType(type: Int): String {
        return OrderFailReasonEnum.getEnNameByType(type)
    }

    private fun getRemake(remake: String): Int {
        return try {
            remake.toInt(16)
        } catch (e: NumberFormatException) {
            Log.e(TAG, "Failed to parse hex string: $remake", e)
            -1 // 返回一个默认值，表示无效的失败原因
        }
    }

}
