package com.gs.payment.plugin.manager

import android.util.Log
import com.gs.payment.plugin.http.Api
import com.gs.payment.plugin.http.HttpUtil
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.Calendar

object CommandUtils {

    //Http。发起支付订单
    fun sendPaymentOrder(
        orderId: String,
        orderMoney: Int,
        productId: String?,
        productName: String?,
        attach: String?,
        callback: PaymentOrderCallback
    ) {
        val jsonObject = JSONObject()
        jsonObject.put("orderNo", orderId)
        jsonObject.put("totalAmount", orderMoney)
        jsonObject.put("objectId", productId)
        jsonObject.put("subject", productName)
        jsonObject.put("attach", attach)
        jsonObject.put("notifyUrl", "https://tryml.free.beeceptor.com")

        Log.d("CommandUtils", "发送支付订单请求: ${jsonObject.toString()}")

        HttpUtil.asyncPostJson(Api.selectUrl, null, jsonObject.toString(), object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("CommandUtils", "支付订单请求失败: ${e.message}")
                callback.onFailure(e.message ?: "未知错误")
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                Log.d("CommandUtils", "支付订单响应: $responseBody")

                if (response.isSuccessful) {
                    callback.onSuccess(response.code, responseBody ?: "")
                } else {
                    Log.e("CommandUtils", "支付订单请求失败，状态码: ${response.code}")
                    callback.onFailure("请求失败，状态码: ${response.code}")
                }
            }
        })
    }

    //Http。查询订单
    fun queryOrder(orderId: String, callback: PaymentOrderCallback) {
        val jsonObject = JSONObject()
        jsonObject.put("orderNo", orderId)
        jsonObject.put("thirdOrderNo", Constants.THIRD_ORDER_NO)
        Log.d("CommandUtils", "发送订单查询请求: ${jsonObject.toString()}")

        HttpUtil.asyncPostJson(Api.orderStatusUrl, null, jsonObject.toString(), object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("CommandUtils", "查询订单请求失败: ${e.message}")
                callback.onFailure(e.message ?: "未知错误")
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                Log.d("CommandUtils", "查询订单响应: $responseBody")

                if (response.isSuccessful) {
                    callback.onSuccess(response.code, responseBody ?: "")
                } else {
                    Log.e("CommandUtils", "查询订单请求失败，状态码: ${response.code}")
                    callback.onFailure("请求失败，状态码: ${response.code}")
                }
            }
        })

    }

    //退款订单
    fun refundOrder(
        orderId: String, orderMoney: String,
        state: String,
        callback: PaymentOrderCallback
    ) {
        val jsonObject = JSONObject()
        jsonObject.put("orderNo", orderId)
        //移除订单号前缀A
        val refundNo = if (orderId.startsWith("A")) orderId.substring(1) else orderId
        jsonObject.put("refundNo", "RD$refundNo")
        jsonObject.put("thirdOrderNo", Constants.THIRD_ORDER_NO)
        jsonObject.put("refundAmount", orderMoney)
        jsonObject.put("refundReason", "Production failed")
        jsonObject.put("refundNotifyUrl", "https://tryml.free.beeceptor.com")
        Log.d("CommandUtils", "发送订单退款请求: ${jsonObject.toString()}")
        HttpUtil.asyncPostJson(Api.refundUrl, null, jsonObject.toString(), object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("CommandUtils", "退款订单请求失败: ${e.message}")
                callback.onFailure(e.message ?: "未知错误")
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                Log.d("CommandUtils", "退款订单响应: $responseBody")
            }

        })
    }

    //订单完成
    fun orderComplete(
        orderId: String,
        callback: PaymentOrderCallback
    ) {
        val jsonObject = JSONObject()
        jsonObject.put("orderNo", orderId)
        jsonObject.put("thirdOrderNo", Constants.THIRD_ORDER_NO)
        jsonObject.put("success", true)
        jsonObject.put("orderStatus", "2")
        jsonObject.put("outStockStatus", "2")
        jsonObject.put("outStockTime", getTime())
        Log.d("CommandUtils", "发送订单完成请求: ${jsonObject.toString()}")
        HttpUtil.asyncPostJson(Api.completeUrl, null, jsonObject.toString(), object : Callback {

            override fun onFailure(call: Call, e: IOException) {
                Log.e("CommandUtils", "订单完成请求失败: ${e.message}")
                callback.onFailure(e.message ?: "未知错误")
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                Log.d("CommandUtils", "订单完成响应: $responseBody")
            }
        })
    }

    //获取年月日时分秒 2026-02-24 23:48:55
    fun getTime(): String {
        val c = Calendar.getInstance()
        val year = c.get(Calendar.YEAR)
        val month = String.format("%02d", c.get(Calendar.MONTH) + 1)
        val day = String.format("%02d", c.get(Calendar.DAY_OF_MONTH))
        val hour = String.format("%02d", c.get(Calendar.HOUR_OF_DAY))
        val minute = String.format("%02d", c.get(Calendar.MINUTE))
        val second = String.format("%02d", c.get(Calendar.SECOND))
        return "$year-$month-$day $hour:$minute:$second"
    }

}