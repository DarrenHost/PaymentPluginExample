package com.mg.switchgpio.manager

/**
 * Created by LiBaoZhi
 * on 2026/1/10
 */
enum class CommandEnum(
    private val code: String,
    private val description: String
) {
    // 销售命令
    VENTA("001003001", "销售"),

    // 退货命令
    DEVOLUCION("001003004", "退货"),

    // 查询命令
    CONSULTA("001003010", "查询"),

    // 撤销命令
    ANULACION("001003003", "撤销"),

    // 确认命令
    CONFIRMACION("001003002", "确认"),

    // 支付验证
    VERIFICATION("001003101", "支付验证");

    fun getCode(): String {
        return code
    }

    fun getDescription(): String {
        return description
    }

    companion object {
        fun fromCode(code: String): CommandEnum? {
            for (command in entries) {
                if (command.code == code) {
                    return command
                }
            }
            return null
        }
    }
}