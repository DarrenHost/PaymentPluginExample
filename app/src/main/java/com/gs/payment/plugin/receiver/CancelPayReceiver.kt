package com.gs.payment.plugin.receiver

import android.content.Context
import android.content.Intent
import com.gs.payment.plugin.manager.CommandUtils
import com.gs.payment.plugin.manager.PaymentOrderCallback
import com.gs.payment.plugin.utils.Logger

class CancelPayReceiver : BaseBroadReceiver() {

    companion object {
        private const val TAG = "PaymentPlugin.CancelPayReceiver"
        const val ACTION = "com.coffeeji.payment.plugin.PAY_CANCEL_ACTON"
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        Logger.i(TAG, "Received intent action: ${intent.action}")
        log("Received intent action: ${intent.action}")
        if (intent.action != ACTION) return

        val orderId = intent.getStringExtra("ORDER_ID")
        val orderMoney = intent.getStringExtra("ORDER_MONEY")
        Logger.i(TAG, "PAY_CANCEL_ACTON received. ORDER_ID=${orderId}")
        Logger.i(TAG, "PAY_CANCEL_ACTON received. ORDER_MONEY=${orderMoney}")

        try {
            CommandUtils.queryOrder(orderId.toString(), object : PaymentOrderCallback {
                override fun onSuccess(code: Int, data: String) {
                    Logger.i(TAG, "查询订单成功: $data")
                }

                override fun onFailure(errorMessage: String) {
                    Logger.e(TAG, "查询订单失败: $errorMessage")
                }
            })
            Logger.i(TAG, "取消支付指令已发送")
        } catch (e: Exception) {
            Logger.e(TAG, "取消发送支付指令异常: ${e.message}")
        }

        log("PAY_CANCEL_ACTON received. ORDER_ID=${orderId}")
        log("PAY_CANCEL_ACTON received. ORDER_MONEY=${orderMoney}")
    }
}
