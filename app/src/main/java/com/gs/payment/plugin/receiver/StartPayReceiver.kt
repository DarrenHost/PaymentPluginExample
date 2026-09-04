package com.gs.payment.plugin.receiver

import android.content.Context
import android.content.Intent
import com.gs.payment.plugin.mdb.MdbCardPaymentManager
import com.gs.payment.plugin.utils.Logger
import java.math.BigDecimal
import java.math.RoundingMode

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
        Logger.i(TAG, "PAY_ACTION received. ORDER_ID=$orderId")
        Logger.i(TAG, "PAY_ACTION received. ORDER_MONEY=$orderMoney")
        Logger.i(TAG, "PAY_ACTION received. PRODUCT_ID=$productId")
        Logger.i(TAG, "PAY_ACTION received. PRODUCT_NAME=$productName")
        Logger.i(TAG, "PAY_ACTION received. SCAN_CODE=$scanCode")

        log("PAY_ACTION received. ORDER_ID=$orderId")
        log("PAY_ACTION received. ORDER_MONEY=$orderMoney")
        log("PAY_ACTION received. PRODUCT_ID=$productId")
        log("PAY_ACTION received. PRODUCT_NAME=$productName")
        log("PAY_ACTION received. SCAN_CODE=$scanCode")

        if (orderId.isNullOrBlank()) {
            sendResult(context, false, "invalid orderId", "")
            return
        }
        if (orderMoney.isNullOrBlank()) {
            sendResult(context, false, "invalid orderMoney", "")
            return
        }

        val amountFen = parseAmountFen(orderMoney)
        if (amountFen <= 0) {
            sendResult(context, false, "invalid orderMoney", "")
            return
        }

        val appContext = context.applicationContext
        val itemId = toMdbItemId(productId)
        Logger.i(TAG, "准备发起MDB刷卡支付: orderId=$orderId, amount=${amountFen}分, itemId=$itemId")
        log("准备发起MDB刷卡支付: orderId=$orderId, amount=${amountFen}分, itemId=$itemId")

        MdbCardPaymentManager.startCardPayment(orderId, amountFen, itemId) { success, message ->
            sendResult(appContext, success, message, orderMoney)
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
        ctx.sendBroadcast(out)
    }

    /** 金额字符串转分，避免 Double 精度误差。 */
    private fun parseAmountFen(orderMoney: String): Long {
        return try {
            BigDecimal(orderMoney)
                .movePointRight(2)
                .setScale(0, RoundingMode.HALF_UP)
                .toLong()
        } catch (e: Exception) {
            Logger.w(TAG, "金额转换失败: ${orderMoney}, ${e.message}")
            0L
        }
    }

    /**
     * 与原版 lib-hw-payment PayParams.toMdbItemId 一致：productId 收窄到低 16 位，
     * 无效值或结果为 0 时使用 1。
     */
    private fun toMdbItemId(productId: String?): Int {
        val raw = productId?.toLongOrNull() ?: return 1
        if (raw <= 0L) return 1
        val masked = (raw and 0xFFFFL).toInt()
        return if (masked == 0) 1 else masked
    }
}
