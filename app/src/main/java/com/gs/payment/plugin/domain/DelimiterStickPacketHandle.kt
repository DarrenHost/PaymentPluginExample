package com.gs.payment.plugin.domain

import com.gs.payment.plugin.mdb.MdbFrameType
import com.gs.payment.plugin.mdb.MdbHex
import com.gs.payment.plugin.utils.Logger
import com.ok.serialport.stick.AbsStickPacketHandle
import java.io.InputStream
import java.nio.charset.StandardCharsets

/**
 * MDB 协议专用粘包处理器：
 * 1. 以 `\r\n`（0x0D 0x0A）作为帧结束标记进行拆包
 * 2. 将 ASCII 格式的十六进制字符串转换为真实 MDB 字节数据
 *    例如：原始数据 "30 32 20 31 39..."（即 "02 19..." 的 ASCII）→ 转换为 0x02 0x19...
 * 3. 按 [MdbFrameType] 分类后差异化校验：
 *    - ACK (0x00) / NAK (0xFF)：单字节响应，无需校验和
 *    - 主动上报帧（设备码 10/08/30 + 数据，CHK 不通过）：跳过校验，原样返回
 *    - 请求应答帧（数据 + 校验码）：末字节校验和验证
 * 4. 返回完整的 MDB 真实数据
 *
 * 数据流：原始byte流 → 按 0D0A 拆包 → ASCII字符串 → 转HEX → 帧类型分类 → 差异化校验 → MDB协议数据
 *
 * @param delimiter 分隔符字节数组，默认为 `\r\n`（0x0D 0x0A）
 * @param maxBufferSize 缓冲区最大大小，超过时会清空并打印丢弃日志
 * @param logger 日志接口，用于记录丢弃的字节和校验错误，默认为 [HwLog.create]
 */
class DelimiterStickPacketHandle(
    private val delimiter: ByteArray = byteArrayOf(0x0D, 0x0A),
    private val maxBufferSize: Int = 4096
) : AbsStickPacketHandle {

    private val buffer = mutableListOf<Byte>()

    override fun execute(inputStream: InputStream): ByteArray? {
        val packet = parseBuffer()
        if (packet != null) {
            return packet
        }
        val available = inputStream.available()
        if (available > 0) {
            val data = ByteArray(available)
            val size = inputStream.read(data)
            if (size > 0) {
                if (buffer.size + size > maxBufferSize) {
                    val dropped = buffer.toByteArray()
                    Logger.i("StickPacket","DelimiterStickPacketHandle 缓冲区溢出，丢弃 ${dropped.size} 字节: ${dropped.toHexString()}")
                    buffer.clear()
                }
                for (i in 0 until size) {
                    buffer.add(data[i])
                }
                return parseBuffer()
            }
        }
        return null
    }

    /**
     * 解析缓冲区中的数据：
     * 1. 查找分隔符确定一帧边界
     * 2. 提取 ASCII 字符串并转为十六进制字节
     * 3. 校验和验证
     * 4. 返回完整的 MDB 真实数据（包含校验和）
     */
    private fun parseBuffer(): ByteArray? {
        if (delimiter.size < 2 || buffer.size < delimiter.size) {
            return null
        }
        for (i in delimiter.lastIndex until buffer.size) {
            if (endsWithDelimiterAt(i)) {
                val frameEndIndex = i - delimiter.size + 1  // 分隔符前的位置
                if (frameEndIndex < 0) {
                    // 只有分隔符没有数据，跳过
                    buffer.subList(0, i + 1).clear()
                    return null
                }

                // 提取帧数据（去掉分隔符）
                val rawFrame = buffer.subList(0, frameEndIndex).toByteArray()
                buffer.subList(0, i + 1).clear()

                return processMdbFrame(rawFrame)
            }
        }
        return null
    }

    /**
     * 处理 MDB 帧数据：
     * 1. ASCII 字符串转十六进制字节
     * 2. 按 [MdbFrameType] 分类：ACK/NAK 直接返回，主动上报跳过校验，请求应答校验末字节
     * 3. 返回完整 MDB 数据
     */
    private fun processMdbFrame(rawFrame: ByteArray): ByteArray? {
        try {
            // Step 1: 将 ASCII 字节转为字符串（如 "02 19 78 00..."）
            val asciiString = String(rawFrame, StandardCharsets.US_ASCII)
                .replace(" ".toRegex(), "")  // 去掉所有空白

            if (asciiString.isEmpty()) {
                Logger.i("StickPacket","DelimiterStickPacketHandle 收到空帧")
                return null
            }

            // Step 2: 将十六进制字符串转为字节数组（要求长度为偶数）
            if (asciiString.length % 2 != 0) {
                Logger.i("StickPacket","DelimiterStickPacketHandle 无效数据长度（非偶数）: ${asciiString.length}, 内容: $asciiString")
                return null
            }

            val mdbData = MdbHex.decode(asciiString)

            if (mdbData.isEmpty()) {
                Logger.i("StickPacket","DelimiterStickPacketHandle 解码后数据为空")
                return null
            }

            // Step 3: 按帧类型分流校验
            return when (MdbFrameType.classify(mdbData)) {
                MdbFrameType.Ack, MdbFrameType.Nak, MdbFrameType.UnsolicitedPush -> {
                    mdbData
                }

                MdbFrameType.RequestReply -> {
                    validateChecksumAndReturn(mdbData)
                }
            }
        } catch (e: Exception) {
            Logger.i("StickPacket","DelimiterStickPacketHandle 处理 MDB 帧失败: ${rawFrame.toHexString()}")
            return null
        }
    }

    /**
     * 请求应答帧校验：末字节为校验和，失败仍返回（让上层决定是否处理）。
     */
    private fun validateChecksumAndReturn(mdbData: ByteArray): ByteArray? {
        if (mdbData.size < 2) {
            Logger.i("StickPacket","DelimiterStickPacketHandle 数据过短，无法校验: ${mdbData.toHexString()}")
            return null
        }

        if (!MdbFrameType.passesReplyChecksum(mdbData)) {
            val receivedChecksum = mdbData.last().toInt() and 0xFF
            val calculatedChecksum = MdbFrameType.calculateChecksum(mdbData, endExclusive = mdbData.size - 1)
            Logger.i("StickPacket", "DelimiterStickPacketHandle 校验和错误: " +
                    "接收=${String.format("%02X", receivedChecksum)}, 计算=${String.format("%02X", calculatedChecksum)}, 数据=${mdbData.toHexString()}")
        }

        return mdbData
    }

    private fun endsWithDelimiterAt(endInclusive: Int): Boolean {
        val start = endInclusive - delimiter.size + 1
        if (start < 0) return false
        for (j in delimiter.indices) {
            if (buffer[start + j] != delimiter[j]) {
                return false
            }
        }
        return true
    }

    private fun ByteArray.toHexString(): String {
        return joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
    }
}
