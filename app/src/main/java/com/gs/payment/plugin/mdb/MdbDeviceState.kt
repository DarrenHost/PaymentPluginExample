package com.gs.payment.plugin.mdb

/** 刷卡器设备状态（仅保留当前刷卡支付使用到的状态）。 */
enum class MdbDeviceState {
    UNKNOWN,
    INITIALIZING,
    READY,
    ENABLED,
    ERROR,
    OFFLINE
}