package com.gs.payment.plugin.utils

object ByteUtil {


    /**
     * 将字节数组转换为十六进制字符串，用于日志输出
     * @param bytes 字节数组
     * @return 十六进制字符串，格式如 "AA 01 02 03"
     */
    fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
    }


    /**
     *  将2位或4位byte转换为int 高位在前
     *  @param offset  起始位下标
     *  @param length  长度
     *
     */
    fun bytesToInt(bytes: ByteArray, offset: Int, length: Int): Int {
        var result = 0
        for (i in 0 until length) {
            val shift = (length - 1 - i) * 8 // 高位在前，左移位数递减
            result = result or ((bytes[offset + i].toInt() and 0xFF) shl shift)
        }
        return result
    }

    /**
     * 将整数转换为指定长度的十六进制字节数组（大端序）
     *
     * @param value 整数值
     * @param length 字节长度
     * @return 字节数组
     */
    fun intToBytes(value: Int, length: Int): ByteArray {
        val bytes = ByteArray(length)
        for (i in length - 1 downTo 0) {
            bytes[i] = ((value shr ((length - 1 - i) * 8)) and 0xFF).toByte()
        }
        return bytes
    }
}