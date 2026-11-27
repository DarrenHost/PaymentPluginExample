package com.gs.payment.plugin.receiver

import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager
import com.blankj.utilcode.util.ThreadUtils
import com.google.gson.Gson
import com.gs.payment.plugin.bean.IcDeviceInfoBean
import com.gs.payment.plugin.bean.IcOrderBean
import com.gs.payment.plugin.manager.ByteUtils
import com.gs.payment.plugin.manager.CommandUtils
import com.gs.payment.plugin.manager.Constants
import com.gs.payment.plugin.manager.PaymentOrderCallback
import com.gs.payment.plugin.utils.Logger
import com.hc.reader.AndroidUSB
import com.hc.reader.Card
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class StartPayReceiver : BaseBroadReceiver() {

    private var context: Context? = null

    companion object {
        private const val TAG = "PaymentPlugin.StartPayReceiver"

        const val ACTION = "com.coffeeji.payment.plugin.PAY_ACTION"

        const val RESULT_ACTION = "com.coffeeji.payment.plugin.PAY_STATE_ACTION"

        // 当前定时读卡任务
        @Volatile
        private var readCardTask: ThreadUtils.Task<String>? = null

        /**
         * 供 CancelPayReceiver 调用：停止定时读卡任务
         */
        fun stopReadCardTask() {
            readCardTask?.let { ThreadUtils.cancel(it) }
            readCardTask = null
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        this.context = context
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

        // 步骤1：启动定时读卡任务，每秒读取一次 IC 卡
        startReadCardTask(context, orderMoney.toString())
    }

    /**
     * 启动定时读卡任务：每秒读取一次 IC 卡，读到卡后停止任务并继续支付流程
     */
    private fun startReadCardTask(context: Context, orderMoney: String) {
        stopReadCardTask()
        Logger.i(TAG, "启动定时读卡任务，每秒读取一次IC卡")
        val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager
        val card: Card = AndroidUSB(context, usbManager)
        val usbDevice = card.GetUsbReader()
        if (usbDevice == null) {
            Logger.w(TAG, "未找到USB读卡器（库匹配失败）")
            return
        }
        val openResult = card.OpenReader(usbDevice)
        if (openResult < 0) {
            Logger.w(TAG, "读取 IC 卡数据失败, 返回值=$openResult")
            return
        }
        val task = object : ThreadUtils.SimpleTask<String>() {
            override fun doInBackground(): String {
                val data = ByteArray(16)
                val readResult = card.rf_scard("4".toByte(), data)
                return readIcCardCode(readResult, data)
            }

            override fun onSuccess(result: String) {
                if (result.isBlank()) {
                    // 未读到卡，等待下一次定时读取（每秒一次，避免刷屏可按需注释）
                    Logger.i(TAG, "本次未读到IC卡，继续等待")
                    return
                }
                // 读到卡后停止定时任务，避免重复读取
                readCardTask?.let { ThreadUtils.cancel(it) }
                readCardTask = null
                Logger.i(TAG, "读取到IC卡号: $result")
                // 流程：获取设备配置 → 堂食下单
                queryDeviceConfig(context, orderMoney, result)
            }

            override fun onFail(t: Throwable) {
                Logger.e(TAG, "定时读卡异常", t)
            }
        }
        ThreadUtils.executeBySingleAtFixRate(task, 1, TimeUnit.SECONDS)
        readCardTask = task
    }

    /**
     * 获取设备配置：使用 IcDeviceInfoBean 解析响应
     */
    private fun queryDeviceConfig(context: Context, orderMoney: String, cardCode: String) {
        try {
            CommandUtils.deviceInfoQuery(cardCode, object : PaymentOrderCallback {
                override fun onSuccess(statusCode: Int, responseBody: String) {
                    try {
                        Logger.i(TAG, "获取设备配置成功: $responseBody")
                        val rootObj = JSONObject(responseBody)
                        val configBody = rootObj.optJSONObject("data")?.toString() ?: responseBody
                        val deviceInfo = Gson().fromJson(configBody, IcDeviceInfoBean::class.java)
                        Logger.i(
                            TAG,
                            "设备配置解析: deviceId=${deviceInfo.deviceId}, " +
                                    "enableFast=${deviceInfo.isEnableFast}, enableFixed=${deviceInfo.isEnableFixed}"
                        )
                        // 设备配置解析完成，进行堂食下单
                        submitDineInOrder(context, orderMoney, cardCode)
                    } catch (e: Exception) {
                        Logger.e(TAG, "处理设备配置响应异常", e)
                        sendResult(context, false, "处理设备配置异常: ${e.message}", orderMoney)
                    }
                }

                override fun onFailure(errorMessage: String) {
                    Logger.w(TAG, "获取设备配置失败: $errorMessage")
                    sendResult(context, false, "获取设备配置失败: $errorMessage", orderMoney)
                }
            })
        } catch (e: Exception) {
            Logger.e(TAG, "获取设备配置异常", e)
            sendResult(context, false, "获取设备配置异常: ${e.message}", orderMoney)
        }
    }

    /**
     * 堂食下单：设备配置获取成功后调用
     */
    private fun submitDineInOrder(
        context: Context,
        amount: String,
        cardCode: String
    ) {
        try {
            CommandUtils.sendPaymentOrder(amount, cardCode, object :
                PaymentOrderCallback {
                override fun onSuccess(statusCode: Int, responseBody: String) {
                    try {
                        Logger.i(TAG, "堂食下单成功，响应: $responseBody")
                        val jsonObj = JSONObject(responseBody)
                        // 服务端返回 status/message，旧接口可能返回 code/msg，这里都兼容
                        val businessCode = when {
                            jsonObj.has("status") -> jsonObj.optInt("status", -1)
                            jsonObj.has("code") -> jsonObj.optInt("code", -1)
                            else -> -1
                        }
                        if (businessCode != 200) {
                            val errorMessage = jsonObj.optString("message")
                                .takeIf { it.isNotBlank() && it != "null" }
                                ?: jsonObj.optString("msg").takeIf { it.isNotBlank() && it != "null" }
                                ?: "未知错误"
                            sendResult(context, false, "下单失败: $errorMessage", amount)
                            return
                        }
                        // 解析 IcOrderBean（响应 data 字段）
                        val dataObj = jsonObj.optJSONObject("data")
                        val orderBean = if (dataObj != null) {
                            Gson().fromJson(dataObj.toString(), IcOrderBean::class.java)
                        } else {
                            Gson().fromJson(responseBody, IcOrderBean::class.java)
                        }
                        val details = orderBean.detailsItem
                        if (details.isNullOrEmpty()) {
                            sendResult(context, false, "下单失败: 无订单明细", amount)
                            return
                        }
                        // 取首个订单明细的订单号保存，供退款使用
                        Constants.icCardOrderNo = details[0].orderNo.orEmpty()
                        Logger.i(TAG, "堂食下单订单号: ${Constants.icCardOrderNo}")
                        sendResult(context, true, "支付成功", amount)
                    } catch (e: Exception) {
                        Logger.e(TAG, "解析下单响应异常", e)
                        sendResult(context, false, "解析下单响应异常: ${e.message}", amount)
                    }
                }

                override fun onFailure(errorMessage: String) {
                    Logger.i(TAG, "堂食下单失败: $errorMessage")
                    sendResult(context, false, errorMessage, amount)
                }
            })
        } catch (e: Exception) {
            Logger.e(TAG, "发送堂食下单指令异常", e)
            log("发送堂食下单指令异常: ${e.message}")
            sendResult(context, false, "发送下单指令异常: ${e.message}", "")
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
    }

    /**
     * 读取 IC 卡号作为 cardCode，供设备配置和下单使用。
     * 读取失败返回空串。
     */
    private fun readIcCardCode(readResult: Short, data: ByteArray): String {
        if (readResult < 0) {
            Logger.w(TAG, "读取IC卡失败，读卡库返回错误码=$readResult")
            return ""
        }
        if (readResult.toInt() > data.size) {
            Logger.w(TAG, "读卡库返回长度异常: readResult=$readResult, data.size=${data.size}")
            return ""
        }
        return try {
            val bytes: ByteArray = ByteUtils.subBytes(data, 0, readResult.toInt())
            ByteUtils.reverse(bytes)
            val result = ByteUtils.bytesToHex(bytes).uppercase()
            Logger.i(TAG, "读取IC卡数据: $result")
            result
        } catch (e: Exception) {
            Logger.e(TAG, "读取IC卡异常（抛出至onFail）", e)
            throw e // 冒泡到 ThreadUtils.SimpleTask.onFail
        }
    }
}