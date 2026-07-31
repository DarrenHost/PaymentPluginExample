package com.gs.payment.plugin.domain

import com.gs.payment.plugin.utils.ByteUtil
import com.gs.payment.plugin.utils.Logger
import com.ok.serialport.data.Request
import com.ok.serialport.data.Response
import com.ok.serialport.data.ResponseRule
import com.ok.serialport.listener.OnResponseListener


/**
 * 新开普pos支付指令构建器
 * @author lixiqiang
 * @date 2026/07/14
 */
object NewCapPosCommandBuilder {

    // 包头
    private const val PACKET_HEADER = 0x5A.toByte()

    // 命令类别
    private const val CMD_TYPE = 0x01.toByte()

    // 请求扣费
    private const val PAY_CODE = 0xA2.toByte()
    // 取消扣费
    private const val CANCEL_PAY_CODE = 0xA4.toByte()

    private var packageSerial = 0


    /**
     *  扣费指令
     */
    fun buildPayCmd(amount: Int, action: ((Boolean, Int, String?) -> Unit)): Request {
        val amountBytes = ByteUtil.intToBytes(amount, 4)
        val sendPacket = getSendCmdPacket(PAY_CODE, amountBytes)
        return Request(sendPacket)
            .timeout(30000)
            .addResponseRule(object : ResponseRule {
                override fun match(request: Request?, receive: ByteArray): Boolean {
                    if (request == null) return false
                    // 扣费命令名码 0xA2  应答码 0x52 且包序号对应
                    return (request.data[4].toInt() and 0xFF == 0xA2 && receive[4].toInt() and 0xFF == 0x52)
                            && (ByteUtil.bytesToInt(request.data, 1, 2) == ByteUtil.bytesToInt(receive, 1, 2))
                }
            })
            .onResponseListener(object : OnResponseListener {
                override fun onFailure(request: Request?, e: Exception) {
                    action.invoke(false, 0xff, e.message)
                }

                override fun onResponse(response: Response) {
                    Logger.w("posPayResult", "code =  ${response.data[7]}")
                    when (response.data[7]) {
                        0x00.toByte() -> {
                            val responseData = response.data
                            val dataLength =   ByteUtil.bytesToInt(responseData,5,2)
                            val contentByte = ByteArray(dataLength)
                            System.arraycopy(responseData,8,contentByte,0,dataLength-1)
                            val costAmount = ByteUtil.bytesToInt(contentByte,10,4)
                            val afterAmount = ByteUtil.bytesToInt(contentByte, 14, 4)
                            Logger.w("posPayResult", " content = ${ByteUtil.bytesToHex(contentByte)} costAmount =$costAmount  afterAmount= $afterAmount")
                            action.invoke(true, 0x00, null)
                        }
                        0x01.toByte() -> action.invoke(false, 0x01, "校验失败")
                        0x02.toByte() -> action.invoke(false, 0x02, "帧序号错误")
                        0x03.toByte() -> action.invoke(false, 0x03, "不支持该命令")
                        0x04.toByte() -> action.invoke(false, 0x04, "无卡")
                        0x05.toByte() -> action.invoke(false, 0x05, "交易失败")
                        0x06.toByte() -> {
                            Logger.w("posPayResult", "0x06 交易未决")
//                            action.invoke(false, "交易未决")
                        }
                        0x07.toByte() -> action.invoke(false, 0x07,  "未决超时")
                        0x08.toByte() -> action.invoke(false, 0x08,"卡号不一致（交易失败或未决时出现）")
                        0x10.toByte() -> action.invoke(false, 0x10, "系统平台错误")
                        0x20.toByte() -> action.invoke(false, 0x20, "POS错误1（POS未开通）")
                        0x21.toByte() -> action.invoke(false, 0x21, "POS错误2（硬件问题）")
                        0x31.toByte() -> action.invoke(false, 0x31, "卡错误1 （读卡失败）")
                        0x32.toByte() -> action.invoke(false, 0x32, "卡错误2  (卡限额)")
                        0x33.toByte() -> action.invoke(false, 0x33, "卡错误3  (余额不足）")
                        0x34.toByte() -> action.invoke(false, 0x34, "卡错误4  (卡失效)")
                        0x35.toByte() -> action.invoke(false, 0x35,"卡错误5  (卡禁用)")
                        0x41.toByte() -> action.invoke(false, 0x41,"交易失败")
                        else -> action.invoke(false, 0xff, "未知异常")
                    }
                }
            })
    }


    /**
     *  取消扣费指令
     */
    fun buildCancelPayCmd(action: ((Boolean, String?) -> Unit)): Request {
        val sendPacket = getSendCmdPacket(CANCEL_PAY_CODE, byteArrayOf())
        return Request(sendPacket)
            .timeout(2000)
            .timeoutRetry(1)
            .addResponseRule(object : ResponseRule {
                override fun match(request: Request?, receive: ByteArray): Boolean {
                    return receive[4] == 0x54.toByte()
                }
            })
            .onResponseListener(object : OnResponseListener {
                override fun onFailure(request: Request?, e: Exception) {
                    action.invoke(false, e.message)
                }

                override fun onResponse(response: Response) {
                    Logger.w("CancelPayResult", "size =  ${response.data.size}")
                    if (response.data.size > 7) {
                        when (response.data[7]) {
                            0x00.toByte() -> action.invoke(true, null)
                            0x01.toByte() -> action.invoke(false, "取消失败，设备处于支付中")
                            0x02.toByte() -> action.invoke(false, "校验失败")
                            0x03.toByte() -> action.invoke(false, "帧序号错误")
                            else -> action.invoke(false, "未知异常")
                        }
                    } else {
                        action.invoke(true, null)
                    }
                }
            })
    }

    // 构建发送命令
    private fun getSendCmdPacket(cmdCode: Byte, contentByte: ByteArray): ByteArray {
        val contentSize = contentByte.size
        val length = if (contentSize > 0) contentSize + 8 else 6
        val packet = ByteArray(length)
        var index = 0
        // 包头
        packet[index++] = PACKET_HEADER
        // 包序号
        val packageSerByte = ByteUtil.intToBytes(packageSerial, 2)
        packageSerial++
        if (packageSerial >= 65535) {
            packageSerial = 0
        }
        packet[index++] = packageSerByte[0]
        packet[index++] = packageSerByte[1]
        // 命令类别
        packet[index++] = CMD_TYPE
        // 命令码
        packet[index++] = cmdCode
        // 内容长度
        if (contentSize > 0) {
            val contentLengthByte = ByteUtil.intToBytes(contentSize, 2)
            packet[index++] = contentLengthByte[0]
            packet[index++] = contentLengthByte[1]
            // 内容
            System.arraycopy(contentByte, 0, packet, index, contentSize)
            index += contentSize
        }
        // LRC
        packet[index] = calculateChecksum(packet, 1, index)
        return packet
    }


    /**
     *  LRC校验，为除起始字符外其他数据的异或值
     */
    private fun calculateChecksum(data: ByteArray, start: Int, end: Int): Byte {
        var checksum = 0
        for (i in start until end) {
            checksum = checksum xor (data[i].toInt() and 0xFF)
        }
        return (checksum and 0xFF).toByte()
    }
}
