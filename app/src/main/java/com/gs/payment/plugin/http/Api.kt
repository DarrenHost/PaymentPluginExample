package com.gs.payment.plugin.http

/**
 * Created by LiBaoZhi
 * on 2025/7/3
 * 接口
 */
object Api {

    //IC卡服务器地址
    const val IC_SERVER_URL: String = "https://tjgjzxmt.com"

    //OAuth 获取token
    const val tokenUrl: String = "$IC_SERVER_URL/api/oauth/token?grant_type=client_credentials"

    //OAuth 客户端凭证
    const val TOKEN_USERNAME: String = "terminal"
    const val TOKEN_PASSWORD: String = "5804884314b6db08a07f8072e543a55c"

    //堂食下单
    const val orderAddUrl: String = "$IC_SERVER_URL/api/api/open/order/add"

    //堂食退单
    var refundOrderUrl: String = "$IC_SERVER_URL/api/api/open/order/refund"

    ///设备配置
    var deviceSettingUrl: String = "$IC_SERVER_URL/api/api/open/device/setting"

}