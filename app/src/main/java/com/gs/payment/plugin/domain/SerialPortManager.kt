package com.gs.payment.plugin.domain

import com.gs.payment.plugin.mdb.MdbCallback
import com.gs.payment.plugin.mdb.MdbCommand
import com.gs.payment.plugin.mdb.MdbHex
import com.gs.payment.plugin.mdb.event.MdbEventDispatcher
import com.gs.payment.plugin.utils.Logger
import com.ok.serialport.OkSerialPort
import com.ok.serialport.data.Request
import com.ok.serialport.data.Response
import com.ok.serialport.data.ResponseProcess
import com.ok.serialport.data.ResponseRule
import com.ok.serialport.listener.OnConnectListener
import com.ok.serialport.listener.OnDataListener
import com.ok.serialport.listener.OnResponseListener
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 串口服务（单例）
 * @author chyi
 * @date 2026/1/4 19:06
 */
object SerialPortManager {

    private var serialClient: OkSerialPort? = null
    private var devicePath: String? = null
    private var baudRate: Int? = null
    private var mdbEventProcess: ResponseProcess? = null

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
        mdbEventProcess = null

        serialClient = OkSerialPort.Builder()
            .devicePath(devicePath)
            .baudRate(baudRate)
            .sendInterval(100)
            .stickPacketHandle(DelimiterStickPacketHandle())
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
     * 注册唯一的 MDB 推送事件分发器，重复注册前会先移除旧进程。
     */
    fun registerMdbEventDispatcher(dispatcher: MdbEventDispatcher): Boolean {
        if (!isConnected()) {
            return false
        }
        unregisterMdbEventDispatcher()
        val process = dispatcher.createResponseProcess()
        serialClient?.addProcess(process)
        mdbEventProcess = process
        return true
    }

    fun unregisterMdbEventDispatcher() {
        mdbEventProcess?.let { process ->
            if (serialClient?.isConnect() == true) {
                serialClient?.removeProcess(process)
            }
        }
        mdbEventProcess = null
    }

    /**
     * 关闭串口
     */
    fun closeSerialPort() {
        serialClient?.disconnect()
        mdbEventProcess = null
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


    fun <T : Any> send(command: MdbCommand<T>, callback: MdbCallback<T>) {
        if (!isConnected()) {
            Logger.i("SerialPortManager","[MDB] < 作用=${command.desc} 失败=MDB串口未连接 mode=${command.responseMode}")
            callback.onFailure(IllegalStateException("MDB串口未连接"))
            return
        }
        try {
            val encoded = command.encode()
            val hex = MdbHex.encode(encoded)
            Logger.i("SerialPortManager","[MDB] > 作用=${command.desc} mode=${command.responseMode} hex=$hex")

            val request = Request(encoded).tag("mdb:${command.desc}")
            when (command.responseMode) {
                MdbCommand.ResponseMode.ACK_ONLY -> {
                    request.ackNakConfig(
                        ackRule = { it.size == 1 && it[0] == 0x00.toByte() },
                        nakRule = { it.size == 1 && it[0] == 0xFF.toByte() },
                        waitData = false,
                        ackTimeout = command.ackTimeout,
                        ackRetryCount = command.ackRetry
                    )
                    // ok-serialport 要求 responseRules 非空才能入队；ACK 由 ackNakConfig 匹配
                    request.addResponseRule(object : ResponseRule {
                        override fun match(request: Request?, receive: ByteArray): Boolean = false
                    })
                    request.onResponseListener(object : OnResponseListener {
                        override fun onResponse(response: Response) {}
                        override fun onFailure(request: Request?, e: Exception) {
                            Logger.i("SerialPortManager", "[MDB] < 作用=${command.desc} 失败=${e.message} mode=ACK_ONLY hex=$hex")
                            callback.onFailure(e)
                        }

                        override fun onAckReceived(request: Request) {
                            try {
                                val result = command.decode(byteArrayOf())
                                Logger.i("SerialPortManager", "[MDB] < 作用=${command.desc} 响应=ACK 解析=${command.formatResult(result)}")
                                callback.onSuccess(result)
                            } catch (e: Exception) {
                                Logger.i("SerialPortManager",
                                    "[MDB] < 作用=${command.desc} 失败=解析异常:${e.message} mode=ACK_ONLY hex=$hex"
                                )
                                callback.onFailure(e)
                            }
                        }
                    })
                }

                MdbCommand.ResponseMode.DATA_ONLY -> {
                    request.timeout(command.dataTimeout)
                    request.addResponseRule(object : ResponseRule {
                        override fun match(request: Request?, receive: ByteArray): Boolean {
                            return command.matchData(receive)
                        }
                    })
                    request.onResponseListener(object : OnResponseListener {
                        override fun onResponse(response: Response) {
                            try {
                                val rawHex = MdbHex.encode(response.data)
                                val result = command.decode(response.data)
                                Logger.i("SerialPortManager",
                                    "[MDB] < 作用=${command.desc} hex=$rawHex 解析=${
                                        command.formatResult(
                                            result
                                        )
                                    }"
                                )
                                callback.onSuccess(result)
                            } catch (e: Exception) {
                                Logger.i("SerialPortManager", "[MDB] < 作用=${command.desc} 失败=解析异常:${e.message} " +
                                            "mode=DATA_ONLY hex=${MdbHex.encode(response.data)}"
                                )
                                callback.onFailure(e)
                            }
                        }

                        override fun onFailure(request: Request?, e: Exception) {
                            Logger.i("SerialPortManager",
                                "[MDB] < 作用=${command.desc} 失败=${e.message} mode=DATA_ONLY hex=$hex"
                            )
                            callback.onFailure(e)
                        }
                    })
                }
            }
            serialClient?.request(request)
        } catch (e: Exception) {
            Logger.i("SerialPortManager","[MDB] < 作用=${command.desc} 失败=${e.message} mode=${command.responseMode}")
            callback.onFailure(e)
        }
    }

    suspend fun <T : Any> sendSuspend(command: MdbCommand<T>): T =
        suspendCancellableCoroutine { cont ->
            send(command, object : MdbCallback<T> {
                override fun onSuccess(data: T) {
                    if (cont.isActive) cont.resume(data)
                }

                override fun onFailure(exception: Exception) {
                    if (cont.isActive) cont.resumeWithException(exception)
                }
            })
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