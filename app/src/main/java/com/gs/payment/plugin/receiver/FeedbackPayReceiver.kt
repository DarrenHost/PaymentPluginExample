package com.gs.payment.plugin.receiver

import android.content.Context
import android.content.Intent
import com.gs.payment.plugin.manager.AsciiSocketManager
import com.gs.payment.plugin.manager.Constants
import com.gs.payment.plugin.utils.Logger
import com.mg.switchgpio.manager.CommandEnum
import com.mg.switchgpio.manager.CommandUtils

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
            return
        }
        // 取消支付
        CommandUtils.sendCommand(AsciiSocketManager, CommandEnum.ANULACION, Constants.TRANSACTION_AMOUNT)
        //延迟5秒
        try {
            Thread.sleep(5000)
            //撤销确认支付
            CommandUtils.sendCommand(AsciiSocketManager, CommandEnum.DEVOLUCION, Constants.TRANSACTION_AMOUNT)
        }catch (e:Exception){
            Logger.e(TAG,"取消发送支付指令异常: ${e.message}")
        }
    }
}
