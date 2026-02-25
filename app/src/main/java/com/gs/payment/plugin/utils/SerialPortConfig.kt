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
    private const val KEY_SOCKET_PORT = "socket_port"
    //socket端口
    private const val DEFAULT_SOCKET_IP = "127.0.0.1"
    private const val DEFAULT_SOCKET_PORT = 4444

    /**
     * 获取 SharedPreferences
     */
    private fun getSharedPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    /**
     * 保存socket路径
     */
    fun saveSocketIp(context: Context, socketIp: String) {
        getSharedPreferences(context)
            .edit()
            .putString(KEY_DEVICE_PATH, socketIp)
            .apply()
    }

    //socket端口
    fun getSocketIp(context: Context): String {
        return getSharedPreferences(context)
            .getString(KEY_DEVICE_PATH, DEFAULT_SOCKET_IP)
            ?: DEFAULT_SOCKET_IP
    }

}