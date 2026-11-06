package com.gs.payment.plugin

import android.content.Context

/**
 * 悬浮窗验证用的默认密码配置。
 * MainActivity 负责修改，PayKeepAliveService 负责读取，通过 SharedPreferences 跨组件共享。
 */
object PayConfig {

    const val DEFAULT_PASSWORD = "123456"

    private const val PREFS_NAME = "pay_plugin_config"
    private const val KEY_DEFAULT_PASSWORD = "default_password"

    fun getDefaultPassword(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_DEFAULT_PASSWORD, DEFAULT_PASSWORD) ?: DEFAULT_PASSWORD

    fun setDefaultPassword(context: Context, password: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DEFAULT_PASSWORD, password)
            .apply()
    }
}
