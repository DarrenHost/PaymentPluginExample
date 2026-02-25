package com.gs.payment.plugin.receiver

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.Message
import com.google.gson.Gson
import com.gs.payment.plugin.bean.OrderBean
import com.gs.payment.plugin.manager.Constants
import com.gs.payment.plugin.manager.PaymentOrderCallback
import com.gs.payment.plugin.utils.Logger
import com.gs.payment.plugin.manager.CommandUtils
import java.lang.ref.WeakReference

class StartPayReceiver : BaseBroadReceiver() {

    private var context: Context? = null
    private var handler: Handler? = null
    private var orderId: String? = null
    private var amount: Int = 0
    private var pollingRunnable: Runnable? = null
    private var maxPollingCount: Int = Constants.MAX_POLLING_COUNT // 最多轮询次数

    companion object {
        private const val TAG = "PaymentPlugin.StartPayReceiver"

        const val ACTION = "com.coffeeji.payment.plugin.PAY_ACTON"

        const val RESULT_ACTION = "com.coffeeji.payment.plugin.PAY_STATE_ACTION"
    }

    // 静态Handler内部类，使用WeakReference避免内存泄漏
    private class StaticHandler(private val weakReference: WeakReference<StartPayReceiver>) : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)
            val receiver = weakReference.get() ?: return
            // 处理消息
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        this.context = context
        this.handler = StaticHandler(WeakReference(this))
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
        
        // 验证并转换金额
        val amount = validateAndConvertAmount(orderMoney) ?: run {
            sendResult(context, false, "invalid orderMoney", "")
            return
        }

        // 保存订单信息用于轮询
        this.orderId = orderId
        this.amount = amount

        // 构建并发送支付指令,得到支付结果
        try {
            CommandUtils.sendPaymentOrder(orderId, amount, productId, productName, attach, object :
                PaymentOrderCallback {
                override fun onSuccess(statusCode: Int, responseBody: String) {
                    try {
                        Logger.i(TAG, "支付订单提交成功，响应: $responseBody")
                        val orderBean = Gson().fromJson(responseBody, OrderBean::class.java)
                        if (orderBean.code != 200) {
                            sendResult(context, false, "支付失败", amount.toString())
                        } else {
                            if (orderBean.data != null) {
                                Constants.THIRD_ORDER_NO = orderBean.data.thirdOrderNo
                            }
                            // 支付订单提交成功，开始轮询查询支付结果
                            startPollingPaymentResult()
                        }
                    } catch (e: Exception) {
                        Logger.e(TAG, "解析支付订单响应异常", e)
                        sendResult(context, false, "解析响应异常: ${e.message}", amount.toString())
                    }
                }

                override fun onFailure(errorMessage: String) {
                    Logger.i(TAG, "支付订单提交失败: $errorMessage")
                    sendResult(context, false, errorMessage, amount.toString())
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
        val context = ctx ?: this.context ?: return
        val out = Intent(RESULT_ACTION)
            .putExtra("STATE", if (success) "success" else "fail")
            .putExtra("MESSAGE", message)
            .putExtra("MONEY", money)
        Logger.i(
            TAG,
            "Sending PAY_STATE_ACTION: status=${if (success) "success" else "fail"}, message=$message, money=$money"
        )
        log("Sending PAY_STATE_ACTION: status=${if (success) "success" else "fail"}, message=$message, money=$money")
        context.sendBroadcast(out)

        // 停止轮询
        stopPolling()
    }

    // 开始轮询查询支付结果
    private fun startPollingPaymentResult() {
        val orderId = this.orderId ?: return
        val handler = this.handler ?: return
        var pollingCount = 0

        // 创建轮询任务
        pollingRunnable = object : Runnable {
            override fun run() {
                if (pollingCount >= maxPollingCount) {
                    // 超过最大轮询次数，认为支付失败
                    sendResult(context, false, "支付超时", amount.toString())
                    return
                }

                pollingCount++
                Logger.i(TAG, "轮询查询支付结果，第 $pollingCount 次")

                // 查询订单状态
                CommandUtils.queryOrder(orderId, object : PaymentOrderCallback {
                    override fun onSuccess(statusCode: Int, responseBody: String) {
                        try {
                            Logger.i(TAG, "查询订单状态响应: $responseBody")
                            val orderBean = Gson().fromJson(responseBody, OrderBean::class.java)

                            if (orderBean.code == 200 && orderBean.data != null) {
                                val orderStatus = orderBean.data.orderStatus
                                Logger.i(TAG, "订单状态: $orderStatus")

                                //2 支付成功,其他状态继续轮询
                                when (orderStatus) {
                                    2 -> {
                                        // 支付成功
                                        sendResult(context, true, "支付成功", amount.toString())
                                    }
                                    else -> {
                                        // 未知状态，继续轮询
                                        Logger.w(TAG, "订单状态: $orderStatus，继续轮询")
                                        handler.postDelayed(pollingRunnable!!, Constants.POLLING_INTERVAL)
                                    }
                                }
                            } else {
                                Logger.w(TAG, "查询订单状态返回异常: ${orderBean.msg}，继续轮询")
                                handler.postDelayed(pollingRunnable!!, Constants.POLLING_INTERVAL)
                            }
                        } catch (e: Exception) {
                            Logger.e(TAG, "解析订单状态响应异常", e)
                            handler.postDelayed(pollingRunnable!!, Constants.POLLING_INTERVAL)
                        }
                    }

                    override fun onFailure(errorMessage: String) {
                        Logger.w(TAG, "查询订单状态失败: $errorMessage，继续轮询")
                        handler.postDelayed(pollingRunnable!!, Constants.POLLING_INTERVAL)
                    }
                })
            }
        }

        // 开始第一次轮询
        handler.post(pollingRunnable!!)
    }

    // 停止轮询
    private fun stopPolling() {
        handler?.removeCallbacks(pollingRunnable ?: return)
        Logger.i(TAG, "停止轮询")
    }

    // 验证并转换金额为分
    private fun validateAndConvertAmount(orderMoney: String?): Int? {
        if (orderMoney.isNullOrBlank()) return null
        
        val money = try {
            orderMoney.toDoubleOrNull() ?: 0.0
        } catch (e: Exception) {
            return null
        }
        
        if (money <= 0) return null
        
        // 转换金额为分（整数）
        return (money * 100).toInt()
    }
}