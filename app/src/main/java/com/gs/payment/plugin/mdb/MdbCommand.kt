package com.gs.payment.plugin.mdb

/**
 * MDB 命令抽象基类。
 *
 * 通信模式说明（基于串口通信日志）：
 * - [ResponseMode.ACK_ONLY]：发送后只等单字节 ACK(0x00)，如开关通道、出货结果通知等
 * - [ResponseMode.DATA_ONLY]：发送后直接返回数据帧（无单独 ACK），如 Setup / Status / Expansion 查询
 */
abstract class MdbCommand<T : Any> {

    abstract val desc: String

    /** 编码为原始 MDB 字节 */
    abstract fun encode(): ByteArray

    /** 响应模式 */
    open val responseMode: ResponseMode = ResponseMode.ACK_ONLY

    /** ACK 等待超时（ms），仅 ACK_ONLY 模式有效 */
    open val ackTimeout: Long = 1500L

    /** ACK 失败重试次数 */
    open val ackRetry: Int = 1

    /** 数据帧等待超时（ms），仅 DATA_ONLY 模式有效 */
    open val dataTimeout: Long = 3000L

    /**
     * 判断是否为本命令期望的数据帧。
     * 仅 [ResponseMode.DATA_ONLY] 时由 Transport 用于 ResponseRule 匹配。
     */
    open fun matchData(data: ByteArray): Boolean = data.size >= 2

    /**
     * 解析响应数据。
     * - ACK_ONLY 时 [data] 为空字节数组，通常返回固定 true 或 Unit
     * - DATA_ONLY 时 [data] 为完整帧（含校验和末字节）
     */
    abstract fun decode(data: ByteArray): T

    /**
     * 将解析结果格式化为可读日志字符串。
     * 返回 [ByteArray] 的命令应 override 为 hex；其余默认 [toString]。
     */
    open fun formatResult(result: T): String = result.toString()

    /** 去掉末尾校验和，返回有效载荷 */
    protected fun ByteArray.payload(): ByteArray =
        if (size > 1) copyOfRange(0, size - 1) else this

    enum class ResponseMode {
        /** 仅等待单字节 ACK(0x00)，NAK(0xFF) 触发重试 */
        ACK_ONLY,

        /** 直接等待业务数据帧，不经过 ACK/NAK 阶段 */
        DATA_ONLY
    }
}
