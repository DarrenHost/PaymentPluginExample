package com.gs.payment.plugin.utils

import android.annotation.SuppressLint
import android.app.reecam.ReecamManager


/**
 * 设备信息工具类
 * @author Leyi
 * @date 2024/11/9 14:29
 */
object DeviceUtils {

    @Volatile
    private var cachedUniqueCode: String? = null

    fun uniqueCode(): String {


        //移远
        @SuppressLint("WrongConstant") val mManager =
            com.gs.payment.plugin.PaymentApplication.appContext.getSystemService("reecam") as ReecamManager
        return mManager.serial.replace("\\s*".toRegex(), "")
    }


    /**
     * 获取4g模块版本
     * @return String
     */
    fun get4gVersion(): String {
        // TODO: 待完善
        return "未知"
    }


}

data class MemoryInfo(
    val total: Long,
    val avail: Long,
    val isLowMemory: Boolean
)