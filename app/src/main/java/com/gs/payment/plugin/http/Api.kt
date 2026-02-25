package com.gs.payment.plugin.http

/**
 * Created by LiBaoZhi
 * on 2025/7/3
 * 接口
 */
object Api {

    //正式环境
    var url: String = "https://services.saltovending.igniterz.dev"

    //创建订单信息
    var selectUrl: String = "$url/order/rcard"

    //查询订单
    var orderStatusUrl: String = "$url/order/status"

    //退款
    var refundUrl: String = "$url/order/refund"

    //订单完成
    var completeUrl: String = "$url/order/complete"

}