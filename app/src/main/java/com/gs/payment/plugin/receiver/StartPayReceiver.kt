package com.gs.payment.plugin.receiver

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.gs.payment.plugin.domain.CommandBuilder
import com.gs.payment.plugin.domain.SerialPortManager
import com.gs.payment.plugin.utils.Logger

class StartPayReceiver : BaseBroadReceiver() {

    companion object {
        private const val TAG = "PaymentPlugin.StartPayReceiver"

        const val ACTION = "com.coffeeji.payment.plugin.PAY_ACTION"

        const val RESULT_ACTION = "com.coffeeji.payment.plugin.PAY_STATE_ACTION"
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        Logger.i(TAG, "Received intent action: ${intent.action}")
        log("Received intent action: ${intent.action}")
        if (intent.action != ACTION) return

        val orderId = intent.getStringExtra("ORDER_ID")
        val orderMoney = intent.getStringExtra("ORDER_MONEY")
        val productId = intent.getStringExtra("PRODUCT_ID")
        val productName = intent.getStringExtra("PRODUCT_NAME")
        val scanCode = intent.getStringExtra("SCAN_CODE")
        Logger.i(TAG, "PAY_ACTION received. ORDER_ID=${orderId}")
        Logger.i(TAG, "PAY_ACTION received. ORDER_MONEY=${orderMoney}")
        Logger.i(TAG, "PAY_ACTION received. PRODUCT_ID=${productId}")
        Logger.i(TAG, "PAY_ACTION received. PRODUCT_NAME=${productName}")
        Logger.i(TAG, "PAY_ACTION received. SCAN_CODE=${scanCode}")

        log("PAY_ACTION received. ORDER_ID=${orderId}")
        log("PAY_ACTION received. ORDER_MONEY=${orderMoney}")
        log("PAY_ACTION received. PRODUCT_ID=${productId}")
        log("PAY_ACTION received. PRODUCT_NAME=${productName}")
        log("PAY_ACTION received. SCAN_CODE=${scanCode}")

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
        if (!SerialPortManager.isConnected()) {
            Logger.w(TAG, "串口未连接，无法发送支付指令")
            log("串口未连接，无法发送支付指令")
            sendResult(context, false, "串口未连接", "")
            return
        }

        // 转换金额为分（整数）
        val amount = (money * 100).toInt()

        // 格式化流水号：确保为16字节ASCII字符串
        val serialNumber = formatSerialNumber(orderId)

        Logger.i(TAG, "准备发送支付指令: 流水号=$serialNumber, 金额=${amount}分, 超时=30秒")
        log("准备发送支付指令: 流水号=$serialNumber, 金额=${amount}分, 超时=30秒")

        // 构建并发送支付指令
        val request = CommandBuilder.buildPaymentCommand(
            serialNumber = serialNumber,
            amount = amount,
            timeout = 60,
            action = { success, message ->
                if (success) {
                    Logger.e(TAG, "等待支付结果")
                    log("等待支付结果")
                    CommandBuilder.waitPayResult { isSuccess, msg ->
                        Logger.i(TAG, "收到支付结果: $isSuccess")
                        log("收到支付结果: $isSuccess")
                        if (isSuccess) {
                            sendResult(context, true, "支付成功", orderMoney)
                        } else {
                            sendResult(context, false, msg ?: "支付失败", orderMoney)
                        }
                    }
                } else {
                    val errorMessage = message ?: "支付指令发送失败"
                    Logger.e(TAG, "支付指令发送失败: $errorMessage")
                    log("支付指令发送失败: $errorMessage")
                    sendResult(context, false, errorMessage, orderMoney)
                }
            }
        )

        try {
            SerialPortManager.send(request)
        } catch (e: Exception) {
            Logger.e(TAG, "发送支付指令异常", e)
            log("发送支付指令异常: ${e.message}")
            sendResult(context, false, "发送指令异常: ${e.message}", "")
        }
    }

    private fun sendResult(
        ctx: Context,
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
        ctx.sendBroadcast(out)
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
}
