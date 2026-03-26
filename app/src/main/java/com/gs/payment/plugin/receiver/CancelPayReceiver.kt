package com.gs.payment.plugin.receiver

import android.content.Context
import android.content.Intent
import android.util.Log

class CancelPayReceiver : BaseBroadReceiver() {

    companion object {
        private const val TAG = "PaymentPlugin.CancelPayReceiver"
        const val ACTION = "com.coffeeji.payment.plugin.PAY_CANCEL_ACTION"
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        Log.i(TAG, "Received intent action: ${intent.action}")
        log("Received intent action: ${intent.action}")
        if (intent.action != ACTION) return

        val orderId = intent.getStringExtra("ORDER_ID")
        val orderMoney = intent.getStringExtra("ORDER_MONEY")
        Log.i(TAG, "PAY_CANCEL_ACTION received. ORDER_ID=${orderId}")
        Log.i(TAG, "PAY_CANCEL_ACTION received. ORDER_MONEY=${orderMoney}")

        log("PAY_CANCEL_ACTION received. ORDER_ID=${orderId}")
        log("PAY_CANCEL_ACTION received. ORDER_MONEY=${orderMoney}")
    }
}
