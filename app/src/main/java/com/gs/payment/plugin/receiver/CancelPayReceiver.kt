package com.gs.payment.plugin.receiver

import android.content.Context
import android.content.Intent
import com.gs.payment.plugin.manager.AsciiSocketManager
import com.gs.payment.plugin.manager.Constants
import com.gs.payment.plugin.utils.Logger
import com.mg.switchgpio.manager.CommandEnum
import com.mg.switchgpio.manager.CommandUtils

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
            CommandUtils.sendCommand(AsciiSocketManager, CommandEnum.ANULACION, Constants.TRANSACTION_AMOUNT)

            Thread.sleep(5000)
            //撤销确认支付
            CommandUtils.sendCommand(AsciiSocketManager, CommandEnum.DEVOLUCION, Constants.TRANSACTION_AMOUNT)
            Logger.i(TAG, "取消支付指令已发送")
        } catch (e: Exception) {
            Logger.e(TAG,"取消发送支付指令异常: ${e.message}")
        }

        log("PAY_CANCEL_ACTON received. ORDER_ID=${orderId}")
        log("PAY_CANCEL_ACTON received. ORDER_MONEY=${orderMoney}")
    }
}
