package com.gs.payment.plugin.receiver

import android.content.Context
import android.content.Intent
import com.gs.payment.plugin.manager.CommandUtils
import com.gs.payment.plugin.manager.PaymentOrderCallback
import com.gs.payment.plugin.utils.Logger

class FeedbackPayReceiver : BaseBroadReceiver() {

    companion object {
        private const val TAG = "PaymentPlugin.FeedbackPayReceiver"

        const val ACTION = "com.coffeeji.payment.plugin.MAKE_STATE_ACTION"
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        Logger.i(TAG, "Received intent action: ${intent.action}")
        log( "Received intent action: ${intent.action}")
        if (intent.action != ACTION) return

        val orderId = intent.getStringExtra("ORDER_ID")
        val orderMoney = intent.getStringExtra("ORDER_MONEY")
        val state = intent.getStringExtra("STATE")
        Logger.i(TAG, "MAKE_STATE_ACTION received. ORDER_ID=${orderId}")
        Logger.i(TAG, "MAKE_STATE_ACTION received. ORDER_MONEY=${orderMoney}")
        Logger.i(TAG, "MAKE_STATE_ACTION received. STATE=${state}")

        if (state == "success"){
            //订单完成
            CommandUtils.orderComplete(orderId.toString(), object : PaymentOrderCallback {
                override fun onSuccess(code: Int, data: String) {
                    Logger.i(TAG, "订单完成: $data")
                }

                override fun onFailure(errorMessage: String) {
                    Logger.i(TAG, "订单失败: $errorMessage")
                }
            })
        }else{
            // 发起退款订单
            CommandUtils.refundOrder(orderId.toString(), orderMoney.toString(), state.toString(), object :
                PaymentOrderCallback {
                override fun onSuccess(code: Int, data: String) {
                    Logger.i(TAG, "退款订单成功: $data")
                }

                override fun onFailure(errorMessage: String) {
                    Logger.e(TAG, "退款订单失败: $errorMessage")
                }
            })
        }
    }
}
