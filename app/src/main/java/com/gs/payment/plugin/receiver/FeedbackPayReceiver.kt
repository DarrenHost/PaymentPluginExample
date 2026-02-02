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

        CommandUtils.sendCommand(AsciiSocketManager, CommandEnum.ANULACION, Constants.TRANSACTION_AMOUNT)

    }
}
