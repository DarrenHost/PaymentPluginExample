package com.gs.payment.plugin.utils

import com.elvishew.xlog.XLog

/**
 * 日志工具类
 * 封装XLog，提供统一的日志API
 * @author chyi
 * @date 2026/1/4
 */
object Logger {

    /**
     * Debug级别日志
     */
    fun d(tag: String, message: String) {
        XLog.d(tag, message)
    }

    /**
     * Info级别日志
     */
    fun i(tag: String, message: String) {
        XLog.i(tag, message)
    }

    /**
     * Warning级别日志
     */
    fun w(tag: String, message: String) {
        XLog.w(tag, message)
    }

    /**
     * Error级别日志
     */
    fun e(tag: String, message: String) {
        XLog.e(tag, message)
    }

    /**
     * Error级别日志（带异常）
     */
    fun e(tag: String, message: String, throwable: Throwable) {
        XLog.e(tag, message, throwable)
    }

    /**
     * Error级别日志（仅异常）
     */
    fun e(tag: String, throwable: Throwable) {
        XLog.e(tag, throwable)
    }

    /**
     * Verbose级别日志
     */
    fun v(tag: String, message: String) {
        XLog.v(tag, message)
    }
}

