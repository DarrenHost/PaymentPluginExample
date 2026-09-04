package com.gs.payment.plugin.mdb

/**
 * MDB 十六进制编解码工具。
 * - [decode]：十六进制字符串转字节（忽略空白，与旧 Tools.strToByte 行为一致）
 * - [encode]：字节转空格分隔大写 hex（如 `13 00 00 01 00 01`），与 Payment 串口日志风格一致
 */
object MdbHex {
    fun decode(hex: String): ByteArray {
        val s = hex.replace("\\s".toRegex(), "")
        require(s.length % 2 == 0) { "hex length must be even: $s" }
        return ByteArray(s.length / 2) { i ->
            s.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

    fun encode(bytes: ByteArray): String =
        bytes.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
}
