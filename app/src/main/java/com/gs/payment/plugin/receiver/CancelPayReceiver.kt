package com.gs.payment.plugin.receiver

import android.content.Context
import android.content.Intent
import com.gs.payment.plugin.mdb.MdbCardPaymentManager
import com.gs.payment.plugin.utils.Logger

class CancelPayReceiver : BaseBroadReceiver() {

    companion object {
        private const val TAG = "PaymentPlugin.CancelPayReceiver"
        const val ACTION = "com.coffeeji.payment.plugin.PAY_CANCEL_ACTION"
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        Logger.i(TAG, "Received intent action: ${intent.action}")
        log("Received intent action: ${intent.action}")
        if (intent.action != ACTION) return

        val orderId = intent.getStringExtra("ORDER_ID")
        val orderMoney = intent.getStringExtra("ORDER_MONEY")
        Logger.i(TAG, "PAY_CANCEL_ACTION received. ORDER_ID=$orderId")
        Logger.i(TAG, "PAY_CANCEL_ACTION received. ORDER_MONEY=$orderMoney")
        log("PAY_CANCEL_ACTION received. ORDER_ID=$orderId")
        log("PAY_CANCEL_ACTION received. ORDER_MONEY=$orderMoney")

        // MDB 刷卡：撤销等待批准中的 Vend，并结束当前会话。
        MdbCardPaymentManager.cancelCardPayment()
        log("已请求取消MDB刷卡支付")
    }
}
