package com.gs.payment.plugin.receiver

import android.content.Context
import android.content.Intent
import com.gs.payment.plugin.mdb.MdbCardPaymentManager
import com.gs.payment.plugin.utils.Logger

class FeedbackPayReceiver : BaseBroadReceiver() {

    companion object {
        private const val TAG = "PaymentPlugin.FeedbackPayReceiver"

        const val ACTION = "com.coffeeji.payment.plugin.MAKE_STATE_ACTION"
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        Logger.i(TAG, "Received intent action: ${intent.action}")
        log("Received intent action: ${intent.action}")
        if (intent.action != ACTION) return

        val orderId = intent.getStringExtra("ORDER_ID")
        val orderMoney = intent.getStringExtra("ORDER_MONEY")
        val state = intent.getStringExtra("STATE")
        Logger.i(TAG, "MAKE_STATE_ACTION received. ORDER_ID=$orderId")
        Logger.i(TAG, "MAKE_STATE_ACTION received. ORDER_MONEY=$orderMoney")
        Logger.i(TAG, "MAKE_STATE_ACTION received. STATE=$state")
        log("MAKE_STATE_ACTION received. ORDER_ID=$orderId")
        log("MAKE_STATE_ACTION received. ORDER_MONEY=$orderMoney")
        log("MAKE_STATE_ACTION received. STATE=$state")

        val stateValue = state?.lowercase()
        if (stateValue != "success" && stateValue != "fail") {
            Logger.w(TAG, "忽略无效的制作反馈状态: $state")
            log("忽略无效的制作反馈状态: $state")
            return
        }

        val success = stateValue == "success"
        MdbCardPaymentManager.finishCardPayment(success) { ok, message ->
            Logger.i(TAG, "MDB 制作反馈处理结果: ok=$ok, message=$message")
            log("MDB 制作反馈处理结果: ok=$ok, message=$message")
        }
    }
}
