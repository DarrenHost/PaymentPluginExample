package com.gs.payment.plugin.domain

import com.ok.serialport.data.Request
import com.ok.serialport.data.Response
import com.ok.serialport.listener.OnResponseListener

/**
 * 支付指令构建器
 * 用于构建各种串口通信命令
 *
 * @author chyi
 * @date 2026/1/4
 */
object CommandBuilder {

    // 包头
    private const val PACKET_HEADER = 0xAA.toByte()

    // 命令码
    private const val CMD_UPLOAD_PAYMENT = 0x00.toByte()      // 上传支付金额
    private const val CMD_CANCEL_PAYMENT = 0x01.toByte()      // 取消支付
    private const val CMD_PAYMENT_RESULT = 0x80.toByte()      // 支付结果（接收）
    private const val CMD_RECEIVE_RESPONSE = 0xE0.toByte()    // 接收响应（接收）
    private const val CMD_TEST = 0xF0.toByte()                // 指令测试

    // 数据长度常量
    private const val DATA_LENGTH_UPLOAD_PAYMENT = 0x18        // 24字节
    private const val DATA_LENGTH_CANCEL_PAYMENT = 0x00        // 0字节
    private const val DATA_LENGTH_TEST = 0x00                  // 0字节
    private const val DATA_LENGTH_RECEIVE_RESPONSE = 0x01      // 1字节

    // 流水号长度
    private const val SERIAL_NUMBER_LENGTH = 16

    // 金额字节长度
    private const val AMOUNT_BYTE_LENGTH = 4

    // 超时时间字节长度
    private const val TIMEOUT_BYTE_LENGTH = 4

    /**
     * 构建上传支付金额命令（0x00）
     *
     * @param serialNumber 流水号，16字节ASCII字符串，例如：2022021815083000
     * @param amount 金额，单位：分，例如：100表示1元
     * @param timeout 超时时间，单位：秒，例如：30表示30秒
     * @return Request 对象
     */
    fun buildPaymentCommand(
        serialNumber: String,
        amount: Int,
        timeout: Int,
        action: ((Boolean, String?) -> Unit)
    ): Request {
        // 验证参数
        require(serialNumber.isNotBlank()) { "流水号不能为空" }
        require(amount > 0) { "金额必须大于0" }
        require(timeout > 0) { "超时时间必须大于0" }

        // 构建数据包
        val packet =
            ByteArray(4 + DATA_LENGTH_UPLOAD_PAYMENT + 1) // 包头(1) + 命令码(1) + 数据长度(1) + 数据(24) + 校验和(1) = 28

        var index = 0

        // 包头
        packet[index++] = PACKET_HEADER

        // 命令码
        packet[index++] = CMD_UPLOAD_PAYMENT

        // 数据长度
        packet[index++] = DATA_LENGTH_UPLOAD_PAYMENT.toByte()

        // 流水号（16字节ASCII）
        val serialBytes = asciiToBytes(serialNumber, SERIAL_NUMBER_LENGTH)
        System.arraycopy(serialBytes, 0, packet, index, SERIAL_NUMBER_LENGTH)
        index += SERIAL_NUMBER_LENGTH

        // 金额（4字节，大端序）
        val amountBytes = intToHexBytes(amount, AMOUNT_BYTE_LENGTH)
        System.arraycopy(amountBytes, 0, packet, index, AMOUNT_BYTE_LENGTH)
        index += AMOUNT_BYTE_LENGTH

        // 超时时间（4字节，大端序）
        val timeoutBytes = intToHexBytes(timeout, TIMEOUT_BYTE_LENGTH)
        System.arraycopy(timeoutBytes, 0, packet, index, TIMEOUT_BYTE_LENGTH)
        index += TIMEOUT_BYTE_LENGTH

        // 计算并设置校验和
        packet[index] = calculateChecksum(packet, 0, index)

        return buildRequest(packet, action)
    }

    /**
     * 构建取消支付命令（0x01）
     *
     * @return Request 对象
     */
    fun buildCancelPaymentCommand(action: ((Boolean, String?) -> Unit)): Request {
        // 构建数据包：包头(1) + 命令码(1) + 数据长度(1) + 校验和(1) = 4
        val packet = ByteArray(4)

        var index = 0

        // 包头
        packet[index++] = PACKET_HEADER

        // 命令码
        packet[index++] = CMD_CANCEL_PAYMENT

        // 数据长度
        packet[index++] = DATA_LENGTH_CANCEL_PAYMENT.toByte()

        // 计算并设置校验和
        packet[index] = calculateChecksum(packet, 0, index)

        return buildRequest(packet, action)
    }

    /**
     * 构建指令测试命令（0xF0）
     *
     * @return Request 对象
     */
    fun buildTestCommand(action: ((Boolean, String?) -> Unit)): Request {
        // 构建数据包：包头(1) + 命令码(1) + 数据长度(1) + 校验和(1) = 4
        val packet = ByteArray(4)

        var index = 0

        // 包头
        packet[index++] = PACKET_HEADER

        // 命令码
        packet[index++] = CMD_TEST

        // 数据长度
        packet[index++] = DATA_LENGTH_TEST.toByte()

        // 计算并设置校验和
        packet[index] = calculateChecksum(packet, 0, index)

        return buildRequest(packet, action)
    }

    private fun buildRequest(
        packet: ByteArray,
        action: ((Boolean, String?) -> Unit)
    ): Request {
        return Request(packet)
            .timeout(500)
            .timeoutRetry(2)
            .onResponseListener(object : OnResponseListener {
                override fun onFailure(request: Request?, e: Exception) {
                    action.invoke(false, e.message)
                }

                override fun onResponse(response: Response) {
                    when (response.data[3]) {
                        0x00.toByte() -> action.invoke(true, null)
                        0x01.toByte() -> action.invoke(true, "设备忙")
                        0x02.toByte() -> action.invoke(true, "参数错误")
                        else -> action.invoke(true, "未知异常")
                    }
                }

            })
    }

    /**
     * 计算校验和
     * 累加指定范围内的所有字节，取低字节
     *
     * @param data 数据数组
     * @param start 起始位置（包含）
     * @param end 结束位置（不包含）
     * @return 校验和字节
     */
    private fun calculateChecksum(data: ByteArray, start: Int, end: Int): Byte {
        var checksum = 0
        for (i in start until end) {
            checksum += data[i].toInt() and 0xFF
        }
        return (checksum and 0xFF).toByte()
    }

    /**
     * 将整数转换为指定长度的十六进制字节数组（大端序）
     *
     * @param value 整数值
     * @param length 字节长度
     * @return 字节数组
     */
    private fun intToHexBytes(value: Int, length: Int): ByteArray {
        val bytes = ByteArray(length)
        for (i in length - 1 downTo 0) {
            bytes[i] = ((value shr ((length - 1 - i) * 8)) and 0xFF).toByte()
        }
        return bytes
    }

    /**
     * 将ASCII字符串转换为指定长度的字节数组
     * 如果字符串长度不足，右侧补0x00
     *
     * @param str ASCII字符串
     * @param length 目标长度
     * @return 字节数组
     */
    private fun asciiToBytes(str: String, length: Int): ByteArray {
        val bytes = ByteArray(length)
        val strBytes = str.toByteArray(Charsets.US_ASCII)
        val copyLength = minOf(strBytes.size, length)
        System.arraycopy(strBytes, 0, bytes, 0, copyLength)
        // 剩余部分已经是0x00，无需额外处理
        return bytes
    }
}

