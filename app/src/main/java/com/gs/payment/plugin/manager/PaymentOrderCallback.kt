package com.gs.payment.plugin.manager

/**
 * Created by LiBaoZhi
 * on 2026/2/24
 */
/**
 * 支付订单请求回调接口
 */
interface PaymentOrderCallback {
    /**
     * 请求成功回调
     * @param statusCode HTTP状态码
     * @param responseBody 响应体内容
     */
    fun onSuccess(statusCode: Int, responseBody: String)

    /**
     * 请求失败回调
     * @param errorMessage 错误信息
     */
    fun onFailure(errorMessage: String)
}