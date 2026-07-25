package com.gs.payment.plugin.domain

import com.gs.payment.plugin.utils.Logger
import com.ok.serialport.OkSerialPort
import com.ok.serialport.data.Request
import com.ok.serialport.data.Response
import com.ok.serialport.data.ResponseProcess
import com.ok.serialport.data.ResponseRule
import com.ok.serialport.listener.OnConnectListener
import com.ok.serialport.listener.OnDataListener
import com.ok.serialport.listener.OnResponseListener

/**
 * 串口服务（单例）
 * @author chyi
 * @date 2026/1/4 19:06
 */
object SerialPortManager {
    private var serialClient: OkSerialPort? = null
    private var devicePath: String? = null
    private var baudRate: Int? = null

    /**
     * 打开串口
     * @param devicePath 设备路径，例如 "/dev/ttyS7"
     * @param baudRate 波特率，例如 9600
     */
    fun openSerialPort(devicePath: String, baudRate: Int) {
        // 如果已经连接，先关闭
        if (serialClient?.isConnect() == true) {
            return
        }

        this.devicePath = devicePath
        this.baudRate = baudRate

        serialClient = OkSerialPort.Builder()
            .devicePath(devicePath)
            .baudRate(baudRate)
            .sendInterval(100)
            .stickPacketHandle(StickPacketHandle())
            .addResponseRule(object : ResponseRule {
                override fun match(request: Request?, receive: ByteArray): Boolean {
                    return receive[3] == 0x01.toByte()
                }
            })
            .build()

        serialClient?.addConnectListener(object : OnConnectListener {
            override fun onConnect(devicePath: String) {
                Logger.i("SerialPortManager", "串口${devicePath}连接成功")
            }

            override fun onDisconnect(devicePath: String, errorMag: Throwable?) {
                if (errorMag != null) {
                    Logger.e(
                        "SerialPortManager",
                        "串口${devicePath}连接失败：${errorMag.message}",
                        errorMag
                    )
                } else {
                    Logger.e("SerialPortManager", "串口${devicePath}连接失败")
                }
            }
        })

        serialClient?.addDataListener(object : OnDataListener {
            override fun onRequest(data: ByteArray) {
                Logger.i("SerialPortManager", "发送数据: ${bytesToHex(data)}")
            }

            override fun onResponse(data: ByteArray) {
                Logger.i("SerialPortManager", "接收数据: ${bytesToHex(data)}")
            }
        })

        serialClient?.connect()
    }

    fun addProcess(responseProcess: ResponseProcess) {
        serialClient?.addProcess(responseProcess)
    }

    fun removeProcess(responseProcess: ResponseProcess) {
        serialClient?.removeProcess(responseProcess)
    }

    /**
     * 关闭串口
     */
    fun closeSerialPort() {
        serialClient?.disconnect()
        serialClient = null
        devicePath = null
        baudRate = null
    }

    /**
     * 发送数据
     * @param request
     */
    fun send(request: Request) {
        if (serialClient?.isConnect() == true) {
            serialClient?.request(request)
        } else {
            openSerialPort(devicePath!!, baudRate!!)
            Logger.w("SerialPortManager", "串口未连接，无法发送数据")
        }
    }

    /**
     * 检查串口是否连接
     * @return true 如果已连接，false 如果未连接
     */
    fun isConnected(): Boolean {
        return serialClient?.isConnect() == true
    }

    /**
     * 获取当前设备路径
     */
    fun getDevicePath(): String? = devicePath

    /**
     * 获取当前波特率
     */
    fun getBaudRate(): Int? = baudRate

    /**
     * 将字节数组转换为十六进制字符串
     */
    private fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString(" ") { "%02X".format(it) }
    }
}