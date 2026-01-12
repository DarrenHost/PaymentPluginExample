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
     * Info级别日志
     */
    fun i(tag: String, message: String) {
        XLog.tag(tag).i(message)
    }

    /**
     * Warning级别日志
     */
    fun w(tag: String, message: String) {
        XLog.tag(tag).w(message)
    }

    /**
     * Error级别日志
     */
    fun e(tag: String, message: String) {
        XLog.e(message)
    }

    /**
     * Error级别日志（带异常）
     */
    fun e(tag: String, message: String, throwable: Throwable) {
        XLog.e(message, throwable)
    }

    /**
     * Error级别日志（仅异常）
     */
    fun e(tag: String, throwable: Throwable) {
        XLog.e(throwable)
    }

    /**
     * Verbose级别日志
     */
    fun v(tag: String, message: String) {
        XLog.v(message)
    }
}

