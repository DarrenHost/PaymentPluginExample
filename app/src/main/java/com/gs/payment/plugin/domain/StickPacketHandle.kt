package com.gs.payment.plugin.domain

import com.gs.payment.plugin.utils.ByteUtil
import com.gs.payment.plugin.utils.Logger
import com.ok.serialport.stick.AbsStickPacketHandle
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException

/**
 * 数据包拆包处理器
 * 数据包格式：
 * - 包头：固定为 0x5A (1字节)
 * - 包序号：流水号 (2字节，大端序)
 * - 命令类别：固定为 0x01 (1字节)
 * - 命令码：操作类型 (1字节)
 * - 数据：参数内容 (N字节)
 * 
 * - LRC校验和：前面所有数据的累加和，取低字节 (1字节)
 * 
 * @author Leyi
 * @date 2024/10/24 15:45
 */
class StickPacketHandle : AbsStickPacketHandle {

    companion object {
        private const val TAG = "StickPacketHandle"
        // 固定缓冲区大小，减少内存分配
        private const val BUFFER_SIZE = 4096
        // 包头标识
        private const val PACKET_HEADER = 0x5A.toByte()
        // 最小数据包长度：包头(1) + 包序号(2) + 命令类别(1) + 命令码(1) + 数据长度(2) + 数据(0) + 校验和(1) = 8字节
        private const val MIN_PACKET_SIZE = 6
        // 最大数据包长度：包头(1) + 包序号(2) + 命令类别(1) + 命令码(1) + 数据长度(2) + 数据(65535) + 校验和(1) = 65543字节
        private const val MAX_PACKET_SIZE = 512
    }

    // 数据缓冲区，用于处理粘包和不完整数据
    private val dataBuffer = mutableListOf<Byte>()

    override fun execute(inputStream: InputStream): ByteArray? {
        try {
            // 使用固定大小缓冲区读取数据
            val buffer = ByteArray(BUFFER_SIZE)
            // 阻塞读取，直到有数据到达或流关闭
            val size = inputStream.read(buffer)
            
            if (size > 0) {
                // 将新读取的数据添加到缓冲区
                val newData = if (size < buffer.size) {
                    buffer.copyOf(size)
                } else {
                    buffer
                }
                Logger.w(TAG,"readMsg  size = $size  data = ${ByteUtil.bytesToHex(newData)}")

                dataBuffer.addAll(newData.toList())
                
                // 尝试从缓冲区中解析完整的数据包
                return parsePacketFromBuffer()
            }
            // size == -1 表示流已关闭
            return null
        } catch (e: EOFException) {
            // 流结束异常，表示流已关闭，这是正常情况
            return null
        } catch (e: InterruptedIOException) {
            // 线程中断异常，重新设置中断标志并返回 null
            Thread.currentThread().interrupt()
            return null
        } catch (e: IOException) {
            // IO异常，可能是串口断开或设备移除，由上层处理
            // 这里返回 null，上层会捕获 IOException 并处理
            throw e
        } catch (e: Exception) {
            // 其他未知异常，重新抛出让上层处理
            throw e
        }
    }

    /**
     * 从缓冲区中解析完整的数据包
     * @return 完整的数据包，如果数据不完整或未找到有效包则返回 null
     */
    private fun parsePacketFromBuffer(): ByteArray? {
        while (dataBuffer.size >= MIN_PACKET_SIZE) {
            // 查找包头位置
            val headerIndex = findPacketHeader()
            if (headerIndex == -1) {
                // 未找到包头，清空缓冲区
                val discardedData = ByteArray(dataBuffer.size)
                for (i in dataBuffer.indices) {
                    discardedData[i] = dataBuffer[i]
                }
                Logger.w(TAG, "未找到包头，丢弃数据: ${ByteUtil.bytesToHex(discardedData)}")
                dataBuffer.clear()
                return null
            }
            
            // 移除包头之前的数据
            if (headerIndex > 0) {
                val discardedData = ByteArray(headerIndex)
                for (i in 0 until headerIndex) {
                    discardedData[i] = dataBuffer[i]
                }
                Logger.w(TAG, "包头前存在无效数据，丢弃: ${ByteUtil.bytesToHex(discardedData)}")
                repeat(headerIndex) { dataBuffer.removeAt(0) }
            }
            
            // 检查是否有足够的数据读取数据长度字段
            if (dataBuffer.size < 6) {
                // 数据不完整，等待更多数据
                return null
            }
            
            // 读取数据长度（下标5-6，大端序）
            val cmdCode = dataBuffer[4]
            // 取消扣费回复没有数据域
            var dataLength = if (cmdCode.toInt() == 0x54) 0 else (ByteUtil.bytesToInt(dataBuffer.toByteArray(), 5,2))
            Logger.w(TAG, "dataLength = $dataLength")
            if (dataLength > 0) {
                dataLength+=2
            }
            // 计算完整数据包长度：包头(1) + 包序号(2) + 命令类别(1) + 命令码(1) + 数据长度(2) + 数据(N) + 校验和(1)
            val packetSize = MIN_PACKET_SIZE + dataLength
            
            // 检查数据包长度是否合法
            if (packetSize > MAX_PACKET_SIZE) {
                // 数据包长度异常，丢弃包头，继续查找下一个包头
                val discardedByte = dataBuffer[0]
                Logger.w(TAG, "数据包长度异常($packetSize > $MAX_PACKET_SIZE)，丢弃包头: ${ByteUtil.bytesToHex(byteArrayOf(discardedByte))}")
                dataBuffer.removeAt(0)
                continue
            }
            Logger.w(TAG, "dataBuffer size  = ${dataBuffer.size} ")
            // 检查是否有足够的数据组成完整的数据包
            if (dataBuffer.size < packetSize) {
                // 数据不完整，等待更多数据
                return null
            }
            
            // 提取完整数据包
            val packet = ByteArray(packetSize)
            for (i in 0 until packetSize) {
                packet[i] = dataBuffer[i]
            }
            
            // 验证校验和
            Logger.w(TAG, "check = ${validateChecksum2(packet)}")
            if (validateChecksum2(packet)) {
                // 校验通过，移除已处理的数据
                repeat(packetSize) { dataBuffer.removeAt(0) }
                return packet
            } else {
                // 校验失败，丢弃包头，继续查找下一个包头
                Logger.w(TAG, "校验和验证失败，丢弃数据包: ${ByteUtil.bytesToHex(packet)}")
                dataBuffer.removeAt(0)
                continue
            }
        }
        
        // 缓冲区数据不足，等待更多数据
        return null
    }

    /**
     * 查找包头位置
     * @return 包头在缓冲区中的索引，如果未找到返回 -1
     */
    private fun findPacketHeader(): Int {
        for (i in dataBuffer.indices) {
            if (dataBuffer[i] == PACKET_HEADER) {
                return i
            }
        }
        return -1
    }

    /**
     * 验证数据包的校验和
     * @param packet 完整的数据包
     * @return true 如果校验和正确，false 否则
     */
    private fun validateChecksum(packet: ByteArray): Boolean {
        if (packet.size < MIN_PACKET_SIZE) {
            return false
        }
        
        // 计算校验和：包头 + 命令码 + 数据长度 + 数据
        // 校验和字段是最后一个字节
        var checksum = 0
        for (i in 0 until packet.size - 1) {
            checksum += packet[i].toInt() and 0xFF
        }
        
        // 取低字节
        val calculatedChecksum = checksum and 0xFF
        val receivedChecksum = packet[packet.size - 1].toInt() and 0xFF
        Logger.w(TAG, "calculatedChecksum = $calculatedChecksum  receivedChecksum=$receivedChecksum" )
        return calculatedChecksum == receivedChecksum
    }


    /**
     *  采用LRC校验，为除起始字符外其他数据的异或值
     */
    private fun validateChecksum2(packet: ByteArray): Boolean {
        if (packet.size < MIN_PACKET_SIZE) {
            return false
        }

        // 计算校验和：为除起始字符外其他数据的异或值
        // 校验和字段是最后一个字节
        var checksum = 0
        for (i in 1 until packet.size - 1) {
            checksum = checksum xor (packet[i].toInt() and 0xFF)
        }
        // 取低字节
        val calculatedChecksum = checksum and 0xFF
        val receivedChecksum = packet[packet.size - 1].toInt() and 0xFF
        Logger.w(TAG, "calculatedChecksum = $calculatedChecksum  receivedChecksum=$receivedChecksum" )
        return calculatedChecksum == receivedChecksum
    }

}
