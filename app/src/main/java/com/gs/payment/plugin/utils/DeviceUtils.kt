package com.gs.payment.plugin.utils

import android.annotation.SuppressLint
import android.app.Activity
import com.elvishew.xlog.XLog
import com.gs.lib.board.VendorFactory
import java.io.File


/**
 * 设备信息工具类
 * @author Leyi
 * @date 2024/11/9 14:29
 */
object DeviceUtils {

    fun uniqueCode(): String {
        return VendorFactory.getInstance().uniqueCode()
    }

    fun setNavigationBar(activity: Activity, isShow: Boolean) {
        VendorFactory.getInstance().setNavBarState(isShow)
    }

    fun reboot() {
        VendorFactory.getInstance().reboot()
    }

    /**
     * 获取4g模块版本
     * @return String
     */
    fun get4gVersion(): String {
        // TODO: 待完善
        return "未知"
    }

    /**
     * 获取Imei
     * @return String
     */
    @SuppressLint("HardwareIds")
    fun getImei(): String {
        try {
            return VendorFactory.getInstance().imei
        } catch (e:Exception) {
            XLog.i("获取设备IMEI错误：${e.message}")
            return "未知"
        }
    }

    /**
     * 安装app
     */
    fun installApp(file: File) {
        VendorFactory.getInstance().installApk(file)
    }

}

data class MemoryInfo(
    val total: Long,
    val avail: Long,
    val isLowMemory: Boolean
)