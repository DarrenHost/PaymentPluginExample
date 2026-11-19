package com.gs.payment.plugin.http

/**
 * Created by LiBaoZhi
 * on 2025/7/3
 * 接口
 */
object Api {

    //IC卡服务器地址
    const val IC_SERVER_URL: String = "https://testxcxst.keyijievip.com"

    //堂食下单
    const val orderAddUrl: String = "$IC_SERVER_URL/api/api/open/order/add"

    //堂食退单
    var refundOrderUrl: String = "$IC_SERVER_URL/api/api/open/order/refund"

    ///设备配置
    var deviceSettingUrl: String = "$IC_SERVER_URL/api/api/open/device/setting"

}