package com.gs.payment.plugin.mdb

/**
 * MDB 帧类型分类。
 *
 * 收包存在两种格式：
 * - **RequestReply**（请求应答）：数据 + 校验码（末字节为累加和低 8 位）
 * - **UnsolicitedPush**（主动上报）：设备码 + 数据（无校验码）
 *
 * 设备码：刷卡器 `0x10`、硬币器 `0x08`、纸币器 `0x30`。
 * 上述前缀的应答与上报共用设备码，靠「末字节按请求应答校验和」区分：
 * - 校验**不通过** → 主动上报（无校验码）
 * - 校验**通过** → 请求应答
 */
enum class MdbFrameType {
    /** 单字节 ACK (0x00) */
    Ack,
    /** 单字节 NAK (0xFF) */
    Nak,
    /** 下位机主动上报帧：设备码 + 数据，无校验码 */
    UnsolicitedPush,
    /** 请求应答帧：数据 + 校验码 */
    RequestReply;

    companion object {
        /**
         * 对解码后的 MDB 字节数据进行帧类型分类。
         */
        fun classify(data: ByteArray): MdbFrameType = when {
            data.size == 1 && data[0] == 0x00.toByte() -> Ack
            data.size == 1 && data[0] == 0xFF.toByte() -> Nak
            data.size >= 2 && isUnsolicitedPush(data) -> UnsolicitedPush
            else -> RequestReply
        }

        /**
         * 判断是否为下位机主动上报帧（设备码 + 数据，无校验码）。
         *
         * 规则与 MdbEventDispatcher.isUnsolicitedEventFrame 保持一致：
         * 首字节为刷卡器 `0x10` / 硬币器 `0x08` / 纸币器 `0x30`，
         * 且按请求应答规则校验末字节校验和不通过。
         */
        fun isUnsolicitedPush(data: ByteArray): Boolean {
            if (data.size < 2) return false
            if (!isDevicePrefix(data[0])) return false
            return !passesReplyChecksum(data)
        }

        /** 刷卡器 / 硬币器 / 纸币器设备码前缀 */
        fun isDevicePrefix(b: Byte): Boolean =
            when (b.toInt() and 0xFF) {
                0x10, 0x08, 0x30 -> true
                else -> false
            }

        /**
         * 按请求应答规则校验末字节：前 n-1 字节累加和取低 8 位是否等于末字节。
         */
        fun passesReplyChecksum(data: ByteArray): Boolean {
            if (data.size < 2) return false
            val received = data.last().toInt() and 0xFF
            return calculateChecksum(data, endExclusive = data.size - 1) == received
        }

        /**
         * 计算校验和：`data[0 until endExclusive]` 累加和取低 8 位。
         */
        fun calculateChecksum(data: ByteArray, endExclusive: Int = data.size): Int {
            var sum = 0
            for (i in 0 until endExclusive) {
                sum += data[i].toInt() and 0xFF
            }
            return sum and 0xFF
        }
    }
}
