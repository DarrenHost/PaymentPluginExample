package com.gs.payment.plugin.bean

data class OrderBean(
    val code: Int,
    val `data`: orderData?,
    val msg: String,
)

data class orderData(
    val orderStatus: Int,
    val thirdOrderNo: String,
)