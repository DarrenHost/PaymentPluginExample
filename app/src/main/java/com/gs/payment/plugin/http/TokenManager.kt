package com.gs.payment.plugin.http

import com.gs.payment.plugin.utils.Logger
import okhttp3.Callback
import okhttp3.Credentials
import okhttp3.FormBody
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

/**
 * OAuth token 管理：通过 client_credentials 获取 access_token 并缓存。
 * 设备配置、堂食下单等接口通过 getToken 拿到 token 后加 Authorization: Bearer 头。
 */
object TokenManager {

    private const val TAG = "TokenManager"

    @Volatile
    private var cachedToken: String? = null

    /**
     * 获取 token（已有缓存直接返回，否则请求接口）
     */
    fun getToken(callback: TokenCallback) {
        cachedToken?.let {
            callback.onSuccess(it)
            return
        }
        requestToken(callback)
    }

    /**
     * 主动清缓存（token 失效时可强制刷新）
     */
    fun clearToken() {
        cachedToken = null
    }

    private fun requestToken(callback: TokenCallback) {
        try {
            val credential = Credentials.basic(Api.TOKEN_USERNAME, Api.TOKEN_PASSWORD)
            val requestBody = FormBody.Builder().build()
            val request = Request.Builder()
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .addHeader("Authorization", credential)
                .url(Api.tokenUrl)
                .post(requestBody)
                .build()

            HttpUtil.getClientInstance().newCall(request).enqueue(object : Callback {
                override fun onFailure(call: okhttp3.Call, e: IOException) {
                    Logger.e(TAG, "获取token失败: ${e.message}")
                    callback.onFailure(e.message ?: "获取token失败")
                }

                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    val body = response.body?.string()
                    Logger.i(TAG, "获取token响应: $body")
                    if (!response.isSuccessful) {
                        callback.onFailure("获取token失败，状态码: ${response.code}")
                        return
                    }
                    try {
                        val json = JSONObject(body ?: "")
                        // 兼容 access_token / token 两种字段名
                        val token = json.optString("access_token", "").ifBlank {
                            json.optString("token", "")
                        }
                        if (token.isBlank()) {
                            callback.onFailure("响应中无 token")
                            return
                        }
                        cachedToken = token
                        callback.onSuccess(token)
                    } catch (e: Exception) {
                        Logger.e(TAG, "解析token异常", e)
                        callback.onFailure("解析token异常: ${e.message}")
                    }
                }
            })
        } catch (e: Exception) {
            Logger.e(TAG, "构建token请求异常", e)
            callback.onFailure("构建token请求异常: ${e.message}")
        }
    }

    interface TokenCallback {
        fun onSuccess(token: String)
        fun onFailure(errorMessage: String)
    }
}
