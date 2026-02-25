package com.gs.payment.plugin.receiver

import android.content.Context
import android.content.Intent
import com.google.gson.Gson
import com.gs.payment.plugin.bean.OrderBean
import com.gs.payment.plugin.manager.Constants
import com.gs.payment.plugin.manager.PaymentOrderCallback
import com.gs.payment.plugin.utils.Logger
import com.gs.payment.plugin.manager.CommandUtils

class StartPayReceiver : BaseBroadReceiver() {

    private var mContext: Context? = null

    companion object {
        private const val TAG = "PaymentPlugin.StartPayReceiver"

        const val ACTION = "com.coffeeji.payment.plugin.PAY_ACTON"

        const val RESULT_ACTION = "com.coffeeji.payment.plugin.PAY_STATE_ACTION"
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        mContext = context
        Logger.i(TAG, "Received intent action: ${intent.action}")
        if (intent.action != ACTION) return

        val orderId = intent.getStringExtra("ORDER_ID")
        val orderMoney = intent.getStringExtra("ORDER_MONEY")
        val productId = intent.getStringExtra("PRODUCT_ID")
        val productName = intent.getStringExtra("PRODUCT_NAME")
        val scanCode = intent.getStringExtra("SCAN_CODE")
        val attach = intent.getStringExtra("ATTACH_CODE")
        Logger.i(TAG, "PAY_ACTON received. ORDER_ID=${orderId}")
        Logger.i(TAG, "PAY_ACTON received. ORDER_MONEY=${orderMoney}")
        Logger.i(TAG, "PAY_ACTON received. PRODUCT_ID=${productId}")
        Logger.i(TAG, "PAY_ACTON received. PRODUCT_NAME=${productName}")
        Logger.i(TAG, "PAY_ACTON received. SCAN_CODE=${scanCode}")
        Logger.i(TAG, "PAY_ACTON received. ATTACH_CODE=${attach}")

        if (orderId.isNullOrBlank()) {
            sendResult(context, false, "invalid orderId", "")
            return
        }
        if (orderMoney.isNullOrBlank()) {
            sendResult(context, false, "invalid orderMoney", "")
            return
        }
        val money = try {
            orderMoney.toDoubleOrNull() ?: 0.0
        } catch (e: Exception) {
            sendResult(context, false, "invalid orderMoney", "")
            return
        }

        if (money <= 0) {
            sendResult(context, false, "invalid orderMoney", "")
            return
        }
        // 转换金额为分（整数）
        val amount = (money * 100).toInt()
        // 构建并发送支付指令,得到支付结果
        try {
            CommandUtils.sendPaymentOrder(orderId, amount, productId, productName, attach, object :
                PaymentOrderCallback {
                override fun onSuccess(statusCode: Int, responseBody: String) {
                    Logger.i(TAG, "支付成功，响应: $responseBody")
                    val orderBean = Gson().fromJson(responseBody, OrderBean::class.java)
                    if (orderBean.code != 200) {
                        sendResult(mContext, false, "支付失败", amount.toString())
                    } else {
                        if (orderBean.data != null) {
                            Constants.THIRD_ORDER_NO = orderBean.data.thirdOrderNo
                        }
                        sendResult(mContext, true, "支付成功", amount.toString())
                    }
                }

                override fun onFailure(errorMessage: String) {
                    Logger.i(TAG, "支付失败: $errorMessage")
                    sendResult(mContext, false, errorMessage, amount.toString())
                }
            })
        } catch (e: Exception) {
            Logger.e(TAG, "发送支付指令异常", e)
            log("发送支付指令异常: ${e.message}")
            sendResult(context, false, "发送指令异常: ${e.message}", "")
        }
    }

    // 发送支付结果
    private fun sendResult(
        ctx: Context?,
        success: Boolean,
        message: String,
        money: String
    ) {
        val out = Intent(RESULT_ACTION)
            .putExtra("STATE", if (success) "success" else "fail")
            .putExtra("MESSAGE", message)
            .putExtra("MONEY", money)
        Logger.i(
            TAG,
            "Sending PAY_STATE_ACTION: status=${if (success) "success" else "fail"}, message=$message, money=$money"
        )
        log("Sending PAY_STATE_ACTION: status=${if (success) "success" else "fail"}, message=$message, money=$money")
        ctx?.sendBroadcast(out)
    }
}