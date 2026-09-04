package com.gs.payment.plugin.mdb.event

/**
 * MDB 刷卡器主动推送事件（不绑定具体命令应答，由 MdbEventDispatcher 解析广播）。
 *
 * 本次迁移仅保留咖啡机 MDB 支付中的 Cashless（刷卡器）事件：
 * - FF            -> CashlessOffline
 * - 10 00         -> CashlessReset（Just Reset）
 * - 10 04         -> CashlessSessionCancel
 * - 10 05 [HH LL] -> CashlessVendApproved
 * - 10 06         -> CashlessVendDenied
 */
sealed class MdbEvent {

    /** 刷卡器断开/离线（单字节 0xFF） */
    data object CashlessOffline : MdbEvent()

    /** 刷卡器复位（Just Reset，10 00） */
    data object CashlessReset : MdbEvent()

    /** 刷卡会话取消请求（Session Cancel Request，10 04） */
    data object CashlessSessionCancel : MdbEvent()

    /**
     * 刷卡扣款批准（Vend Approved，10 05）。
     * @param amountScaled MDB 缩放金额（可选，来自帧内 HH LL）
     */
    data class CashlessVendApproved(val amountScaled: Int = 0) : MdbEvent()

    /** 刷卡扣款拒绝（Vend Denied，10 06） */
    data object CashlessVendDenied : MdbEvent()
}