package com.gs.payment.plugin.manager

import com.gs.payment.plugin.http.Api
import com.gs.payment.plugin.http.HttpUtil
import com.gs.payment.plugin.http.TokenManager
import com.gs.payment.plugin.utils.DeviceUtils
import com.gs.payment.plugin.utils.Logger
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException

object CommandUtils {

    //Http。发起支付订单
    fun sendPaymentOrder(
        orderMoney: String,
        cardCode: String,
        callback: PaymentOrderCallback
    ) {
        try {
            val jsonObject = JSONObject()
            jsonObject.put("deviceId", DeviceUtils.uniqueCode())
            jsonObject.put("uniqueNo", "un" + System.currentTimeMillis())
            jsonObject.put("amountMode", 1)
            jsonObject.put("orderPrice", orderMoney)
            jsonObject.put("payMethod", 1)
            jsonObject.put("code", cardCode)

            Logger.i("CommandUtils", "发送支付订单请求: ${jsonObject.toString()}")

            TokenManager.getToken(object : TokenManager.TokenCallback {
                override fun onSuccess(token: String) {
                    val headers = mapOf("Authorization" to "Bearer $token")
                    HttpUtil.asyncPostJson(Api.orderAddUrl, headers, jsonObject.toString(), object : Callback {
                        override fun onFailure(call: Call, e: IOException) {
                            Logger.e("CommandUtils", "支付订单请求失败: ${e.message}")
                            callback.onFailure(e.message ?: "未知错误")
                        }

                        override fun onResponse(call: Call, response: Response) {
                            val responseBody = response.body?.string()
                            Logger.i("CommandUtils", "支付订单响应: $responseBody")

                            if (response.isSuccessful) {
                                callback.onSuccess(response.code, responseBody ?: "")
                            } else {
                                Logger.e("CommandUtils", "支付订单请求失败，状态码: ${response.code}")
                                callback.onFailure("请求失败，状态码: ${response.code}")
                            }
                        }
                    })
                }

                override fun onFailure(errorMessage: String) {
                    Logger.e("CommandUtils", "下单前获取token失败: $errorMessage")
                    callback.onFailure(errorMessage)
                }
            })
        } catch (e: Exception) {
            Logger.e("CommandUtils", "构建支付订单请求异常", e)
            callback.onFailure("构建请求异常: ${e.message}")
        }
    }

    //退款订单
    fun refundOrder(
        callback: PaymentOrderCallback
    ) {
        try {
            val jsonObject = JSONObject()
            jsonObject.put("deviceId", DeviceUtils.uniqueCode())
            jsonObject.put("uniqueNo", "un" + System.currentTimeMillis())
            jsonObject.put("orderNo", Constants.icCardOrderNo)
            Logger.i("CommandUtils", "发送订单退款请求: ${jsonObject.toString()}")
            HttpUtil.asyncPostJson(Api.refundOrderUrl, null, jsonObject.toString(), object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Logger.e("CommandUtils", "退款订单请求失败: ${e.message}")
                    callback.onFailure(e.message ?: "未知错误")
                }

                override fun onResponse(call: Call, response: Response) {
                    val responseBody = response.body?.string()
                    Logger.i("CommandUtils", "退款订单响应: $responseBody")
                }

            })
        } catch (e: Exception) {
            Logger.e("CommandUtils", "构建退款订单请求异常", e)
            callback.onFailure("构建请求异常: ${e.message}")
        }
    }

    //设备信息
    fun deviceInfoQuery(
        cardCode: String,
        callback: PaymentOrderCallback
    ) {
        try {
            val jsonObject = JSONObject()
            jsonObject.put("deviceId", DeviceUtils.uniqueCode())
            Logger.i("CommandUtils", "设备信息完成请求: ${jsonObject.toString()}")
            TokenManager.getToken(object : TokenManager.TokenCallback {
                override fun onSuccess(token: String) {
                    val headers = mapOf("Authorization" to "Bearer $token")
                    HttpUtil.asyncPostJson(Api.deviceSettingUrl, headers, jsonObject.toString(), object : Callback {

                        override fun onFailure(call: Call, e: IOException) {
                            Logger.e("CommandUtils", "设备配置请求失败: ${e.message}")
                            callback.onFailure(e.message ?: "未知错误")
                        }

                        override fun onResponse(call: Call, response: Response) {
                            val responseBody = response.body?.string()
                            Logger.i("CommandUtils", "设备配置响应: $responseBody")
                            if (response.isSuccessful) {
                                callback.onSuccess(response.code, responseBody ?: "")
                            } else {
                                Logger.e("CommandUtils", "设备配置请求失败，状态码: ${response.code}")
                                callback.onFailure("请求失败，状态码: ${response.code}")
                            }
                        }
                    })
                }

                override fun onFailure(errorMessage: String) {
                    Logger.e("CommandUtils", "设备配置前获取token失败: $errorMessage")
                    callback.onFailure(errorMessage)
                }
            })
        } catch (e: Exception) {
            Logger.e("CommandUtils", "构建设备配置请求异常", e)
            callback.onFailure("构建请求异常: ${e.message}")
        }
    }

}