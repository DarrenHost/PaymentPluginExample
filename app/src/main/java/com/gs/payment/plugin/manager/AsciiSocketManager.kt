package com.gs.payment.plugin.manager

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.gs.payment.plugin.utils.Logger
import com.mg.switchgpio.manager.CommandUtils
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketException
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import kotlin.concurrent.Volatile

object AsciiSocketManager {
    private var socket: Socket? = null
    private var reader: BufferedReader? = null
    private var writer: OutputStream? = null

    @Volatile
    private var isRunning = false

    @Volatile
    private var isConnecting = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private var callback: SocketCallback? = null
    private var sendExecutor: ExecutorService?
    private val heartbeatExecutor: ScheduledExecutorService? = null
    private var serverIp: String? = null
    private var serverPort = 0
    private var retryCount = 0
    private const val TAG = "AsciiSocketManager"
    private const val CONNECT_TIMEOUT = 5000 // 连接超时时间(毫秒)
    private const val RECONNECT_DELAY = 5 // 重连延迟(秒)
    private const val MAX_RETRY_COUNT = 3 // 最大重试次数
    
    // 存储支付结果回调，使用消息ID作为键
    private val paymentResultCallbacks = ConcurrentHashMap<String, PaymentResultCallback>()

    init {
        sendExecutor = Executors.newSingleThreadExecutor()
    }

    @Synchronized
    fun connect(ip: String?, port: Int, callback: SocketCallback?) {
        if (isRunning || isConnecting) {
            Logger.i(TAG, "Connection already in progress:")
            return
        }

        this.serverIp = ip
        this.serverPort = port
        this.callback = callback
        isConnecting = true
        retryCount = 0

        if (sendExecutor == null || sendExecutor!!.isShutdown) {
            sendExecutor = Executors.newSingleThreadExecutor()
        }

        Thread {
            try {
                Logger.i(TAG, "Attempting to connect to $ip:$port")
                socket = Socket()
                socket!!.connect(InetSocketAddress(ip, port), CONNECT_TIMEOUT)
                writer = socket!!.getOutputStream()
                reader = BufferedReader(
                    InputStreamReader(
                        socket!!.getInputStream(), StandardCharsets.US_ASCII
                    )
                )

                isRunning = true
                isConnecting = false
                retryCount = 0

                mainHandler.post {
                    callback?.onConnectionStatusChanged(true)
                }

                Logger.i(TAG, "Socket connected to $ip:$port")

                // 开始接收消息
                readMessages()
            } catch (e: Exception) {
                isConnecting = false
                Log.e(TAG, "Connection failed: " + e.message)
                handleConnectionError(e)
            }
        }.start()
    }

    private fun readMessages() {
        try {
            val buffer = CharArray(2048) // 缓冲区大小
            while (isRunning) {
                val bytesRead = reader!!.read(buffer)
                if (bytesRead == -1) {
                    Logger.i(TAG, "Connection closed by server")
                    break
                }

                val message = String(buffer, 0, bytesRead)
                Logger.i(TAG, "RECV ASCII/" + message.length + " <<< " + message)

                // 处理支付结果回调
                processPaymentResponse(message)

                mainHandler.post {
                    if (callback != null) callback!!.onMessageReceived(message)
                }
            }
        } catch (e: SocketException) {
            if (isRunning) {
                Log.e(TAG, "Socket read error: " + e.message)
                handleConnectionError(e)
            }
        } catch (e: IOException) {
            if (isRunning) {
                Log.e(TAG, "IO error while reading: " + e.message)
                handleConnectionError(e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error while reading: " + e.message)
            handleConnectionError(e)
        } finally {
            if (isRunning) {
                disconnect()
            }
        }
    }

    /**
     * 处理支付响应，检查是否匹配已注册的回调
     */
    private fun processPaymentResponse(response: String) {
        // 尝试从响应中提取消息ID或匹配规则
        // 这里使用简单的包含"Transaction"作为支付响应的标识
        if (response.contains("Transaction") && CommandUtils.parseTransactionData(response).isNotEmpty()) {
            // 对于支付响应，尝试匹配已注册的回调
            // 由于我们无法确定消息ID，这里简单地将响应传递给第一个注册的回调
            if (paymentResultCallbacks.isNotEmpty()) {
                val firstKey = paymentResultCallbacks.keys.first()
                handlePaymentResponse(response, firstKey)
            }
        }
    }

    fun sendAsciiMessage(message: String , callback: SocketCallback?) {
        if (!isRunning) {
            Log.w(TAG, "Socket not connected, cannot send message")
            return
        }

        sendExecutor!!.execute {
            try {
                val messageBytes = message.toByteArray(StandardCharsets.US_ASCII)
                writer!!.write(messageBytes)
                writer!!.flush()

                Logger.i(TAG, "SEND ASCII/" + messageBytes.size + " >>> " + message)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send message: " + e.message)
                mainHandler.post {
                    if (callback != null) callback!!.onError(e)
                }
            }
        }
    }

    private fun handleConnectionError(e: Exception) {
        if (!isRunning) return

        mainHandler.post {
            if (callback != null) callback!!.onError(e)
        }

        // 自动重连逻辑
        if (retryCount < MAX_RETRY_COUNT) {
            retryCount++
            mainHandler.postDelayed({
                if (!isRunning && !isConnecting) {
                    Logger.i(
                        TAG,
                        "Attempting to reconnect (attempt " + retryCount + "/" + MAX_RETRY_COUNT + ")..."
                    )
                    connect(serverIp, serverPort, callback)
                }
            }, (RECONNECT_DELAY * 1000).toLong())
        } else {
            Log.e(TAG, "Max reconnect attempts reached. Giving up.")
            disconnect()
        }
    }

    @Synchronized
    fun disconnect() {
        if (!isRunning) return
        Logger.i(TAG, "Disconnecting socket")

        isRunning = false
        isConnecting = false

        try {
            if (writer != null) writer!!.close()
            if (reader != null) reader!!.close()
            if (socket != null && !socket!!.isClosed) socket!!.close()
        } catch (e: IOException) {
            Log.e(TAG, "Error closing socket: " + e.message)
        }

        if (heartbeatExecutor != null && !heartbeatExecutor.isShutdown) {
            heartbeatExecutor.shutdown()
        }

        mainHandler.post {
            if (callback != null) callback!!.onConnectionStatusChanged(false)
        }
    }

    val isConnected: Boolean
        get() = isRunning && socket != null && socket!!.isConnected && !socket!!.isClosed

    /**
     * 支付结果回调接口
     */
    interface PaymentResultCallback {
        fun onPaymentSuccess(response: String)
        fun onPaymentFailed(error: String)
    }

    /**
     * 发送支付消息并注册回调
     * @param message 要发送的消息
     * @param messageId 消息ID，用于匹配响应
     * @param socketCallback Socket连接回调
     * @param paymentCallback 支付结果回调
     */
    fun sendPaymentMessage(
        message: String, 
        messageId: String,
        socketCallback: SocketCallback? = null, 
        paymentCallback: PaymentResultCallback
    ) {
        // 注册支付结果回调
        paymentResultCallbacks[messageId] = paymentCallback
        
        // 发送消息
        sendAsciiMessage(message, socketCallback)
    }

    /**
     * 处理支付响应
     * @param response 响应消息
     * @param messageId 消息ID
     */
    private fun handlePaymentResponse(response: String, messageId: String) {
        val callback = paymentResultCallbacks.remove(messageId)
        if (callback != null) {
            mainHandler.post {
                // 判断支付是否成功
                val isSuccess = response.contains("Transaction") && 
                               CommandUtils.parseTransactionData(response).isNotEmpty()
                
                if (isSuccess) {
                    callback.onPaymentSuccess(response)
                } else {
                    callback.onPaymentFailed("支付失败: 未收到有效的交易响应")
                }
            }
        }
    }

}