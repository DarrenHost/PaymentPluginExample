package com.gs.payment.plugin

/**
 * 支付验证涉及的跨组件常量（广播/悬浮窗/结果回传之间共享的 Extra key）。
 * 原 PayInputActivity 被移除后，常量集中在此，避免删除 Activity 造成引用断裂。
 */
object PayContract {
    const val EXTRA_ORDER_ID = "ORDER_ID"
    const val EXTRA_ORDER_MONEY = "ORDER_MONEY"
    const val EXTRA_PRODUCT_ID = "PRODUCT_ID"
    const val EXTRA_PRODUCT_NAME = "PRODUCT_NAME"
    const val EXTRA_SCAN_CODE = "SCAN_CODE"
}
