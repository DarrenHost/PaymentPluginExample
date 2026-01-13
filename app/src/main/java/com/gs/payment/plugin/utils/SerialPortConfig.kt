package com.gs.payment.plugin.utils

import android.content.Context
import android.content.SharedPreferences

/**
 * 串口配置管理工具类
 * @author chyi
 * @date 2026/1/4
 */
object SerialPortConfig {
    private const val PREF_NAME = "serial_port_config"
    private const val KEY_DEVICE_PATH = "device_path"
    private const val DEFAULT_DEVICE_PATH = "/dev/ttyS7"

    /**
     * 获取 SharedPreferences
     */
    private fun getSharedPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    /**
     * 保存串口路径
     */
    fun saveDevicePath(context: Context, devicePath: String) {
        getSharedPreferences(context)
            .edit()
            .putString(KEY_DEVICE_PATH, devicePath)
            .apply()
    }

    /**
     * 读取串口路径
     */
    fun getDevicePath(context: Context): String {
        return getSharedPreferences(context)
            .getString(KEY_DEVICE_PATH, DEFAULT_DEVICE_PATH)
            ?: DEFAULT_DEVICE_PATH
    }

    /**
     * 获取默认串口路径
     */
    fun getDefaultDevicePath(): String = DEFAULT_DEVICE_PATH
}
