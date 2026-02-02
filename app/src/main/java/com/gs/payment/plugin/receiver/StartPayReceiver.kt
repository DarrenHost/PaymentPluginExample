package com.gs.payment.plugin.receiver

import android.content.Context
import android.content.Intent
import android.util.Log
import com.gs.payment.plugin.domain.CommandBuilder
import com.gs.payment.plugin.manager.AsciiSocketManager
import com.gs.payment.plugin.manager.Constants
import com.gs.payment.plugin.manager.SocketCallback
import com.gs.payment.plugin.utils.Logger
import com.mg.switchgpio.manager.CommandEnum
import com.mg.switchgpio.manager.CommandUtils

class StartPayReceiver : BaseBroadReceiver(), SocketCallback {

    private var mContext: Context? = null
    companion object {
        private const val TAG = "PaymentPlugin.StartPayReceiver"

        const val ACTION = "com.coffeeji.payment.plugin.PAY_ACTON"

        const val RESULT_ACTION = "com.coffeeji.payment.plugin.PAY_STATE_ACTION"
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        mContext=context
        Logger.i(TAG, "Received intent action: ${intent.action}")
        if (intent.action != ACTION) return

        val orderId = intent.getStringExtra("ORDER_ID")
        val orderMoney = intent.getStringExtra("ORDER_MONEY")
        val productId = intent.getStringExtra("PRODUCT_ID")
        val productName = intent.getStringExtra("PRODUCT_NAME")
        val scanCode = intent.getStringExtra("SCAN_CODE")
        Logger.i(TAG, "PAY_ACTON received. ORDER_ID=${orderId}")
        Logger.i(TAG, "PAY_ACTON received. ORDER_MONEY=${orderMoney}")
        Logger.i(TAG, "PAY_ACTON received. PRODUCT_ID=${productId}")
        Logger.i(TAG, "PAY_ACTON received. PRODUCT_NAME=${productName}")
        Logger.i(TAG, "PAY_ACTON received. SCAN_CODE=${scanCode}")
        Constants.TRANSACTION_AMOUNT = orderMoney ?: ""

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

        // 检查串口是否连接
        if (!AsciiSocketManager.isConnected) {
            Logger.i(TAG, "socket未连接，无法发送支付指令")
            sendResult(context, false, "socket未连接", "")
            return
        }

        // 转换金额为分（整数）
        val amount = (money * 100).toInt()

        // 格式化流水号：确保为16字节ASCII字符串
        val serialNumber = formatSerialNumber(orderId)

        Logger.i(TAG, "准备发送支付指令: 流水号=$serialNumber, 金额=${amount}分, 超时=30秒")
        log("准备发送支付指令: 流水号=$serialNumber, 金额=${amount}分, 超时=30秒")

        // 构建并发送支付指令,得到支付结果
        try {
            // 使用新的支付结果回调方法
            CommandUtils.sendPaymentCommand(
                AsciiSocketManager, 
                CommandEnum.VENTA, 
                orderMoney,
                object : AsciiSocketManager.PaymentResultCallback {
                    override fun onPaymentSuccess(response: String) {
                        Logger.i(TAG, "支付成功，响应: $response")
                        log("支付成功: $response")
                        
                        // 支付成功，发送确认命令s
                        try {
                            //延时10秒
                            Thread.sleep(5000)
                            CommandUtils.sendCommand(AsciiSocketManager, CommandEnum.CONFIRMACION, Constants.TRANSACTION_AMOUNT)
                            sendResult(mContext, true, "支付成功", Constants.TRANSACTION_AMOUNT)
                        } catch (e: Exception) {
                            Logger.e(TAG, "发送确认命令异常", e)
                            sendResult(mContext, false, "支付成功但确认失败: ${e.message}", Constants.TRANSACTION_AMOUNT)
                        }
                    }

                    override fun onPaymentFailed(error: String) {
                        Logger.e(TAG, "支付失败: $error")
                        log("支付失败: $error")
                        sendResult(mContext, false, error, Constants.TRANSACTION_AMOUNT)
                    }
                }
            )
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

    /**
     * 格式化流水号，确保为16字节ASCII字符串
     * 如果长度不足16字节，右侧补空格；如果超过16字节，截取前16字节
     *
     * @param orderId 原始订单ID
     * @return 格式化后的16字节ASCII字符串
     */
    private fun formatSerialNumber(orderId: String): String {
        // 移除非ASCII字符，只保留可打印的ASCII字符
        val cleanId = orderId.filter { it.code in 32..126 }

        return when {
            cleanId.length == 16 -> cleanId
            cleanId.length > 16 -> cleanId.substring(0, 16)
            else -> cleanId.padEnd(16, ' ') // 右侧补空格到16字节
        }
    }

    override fun onConnectionStatusChanged(isConnected: Boolean) {
        Logger.i(TAG, "Socket连接状态变更: $isConnected")
    }

    override fun onMessageReceived(message: String) {
        if (message == null){
            Logger.i(TAG, "onMessageReceived: message is null")
            return
        }
        Logger.i(TAG, "onMessageReceived: $message")

        // 解析交易数据
        val parsedData = CommandUtils.parseTransactionData(message)
        //打印结果
        Logger.i(TAG, "parseTransactionData: $parsedData")
        
        // 支付结果的逻辑已经移到了PaymentResultCallback中，这里只记录日志
    }

    override fun onError(e: Exception) {
        Logger.e(TAG, "Socket连接错误", e)
        log("Socket连接错误: ${e.message}")
        sendResult(mContext, false, "Socket连接错误: ${e.message}", "")
    }
}
