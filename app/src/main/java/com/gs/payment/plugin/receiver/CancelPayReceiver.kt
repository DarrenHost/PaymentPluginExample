package com.gs.payment.plugin.receiver

import android.content.Context
import android.content.Intent
import com.gs.payment.plugin.domain.CommandBuilder
import com.gs.payment.plugin.domain.SerialPortManager
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

        CommandBuilder.removePayResult()
        val request = CommandBuilder.buildCancelPaymentCommand { success, message ->
            if (success) {
                val resultMessage = message ?: "取消支付指令发送成功"
                Logger.i(TAG, "取消支付指令发送成功: $resultMessage")
                log("取消支付指令发送成功: $resultMessage")
            } else {
                val errorMessage = message ?: "取消支付指令发送失败"
                Logger.e(TAG, "取消支付指令发送失败: $errorMessage")
                log("取消支付指令发送失败: $errorMessage")
            }
        }

        try {
            SerialPortManager.send(request)
            Logger.i(TAG, "取消支付指令已发送到串口")
            log("取消支付指令已发送到串口")
        } catch (e: Exception) {
            Logger.e(TAG, "取消发送支付指令异常", e)
            log("取消发送支付指令异常: ${e.message}")
        }

        log("PAY_CANCEL_ACTON received. ORDER_ID=${orderId}")
        log("PAY_CANCEL_ACTON received. ORDER_MONEY=${orderMoney}")
    }
}
