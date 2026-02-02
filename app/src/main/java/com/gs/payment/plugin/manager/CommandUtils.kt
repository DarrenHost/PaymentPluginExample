package com.mg.switchgpio.manager

import android.util.Log
import com.gs.payment.plugin.manager.AsciiSocketManager
import com.gs.payment.plugin.manager.Constants
import com.gs.payment.plugin.manager.SocketCallback
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Calendar
import java.util.regex.Matcher
import java.util.regex.Pattern

object CommandUtils {
    //支付金额
    const val payMoney: String = "00201200000000"

    //收银员编号
    const val cashierNo: String = "0030073412345"

    //序列号
    const val serialNo: String = "004006000260"

    //货币代码
    const val currencyCode: String = "012003504"

    //日期
    const val date: String = "014008"

    //时间
    const val time: String = "015006"

    //日期
    var senddate: String = "018008"

    //时间
    var sendtime: String = "019006"

    //发送命令
    fun sendCommand(socketManager: AsciiSocketManager, commandType: CommandEnum, money: String, callback: SocketCallback? = null) {
        var str = ""
        val commandCode = commandType.getCode()
        val bigDecimal = BigDecimal(money)
        val multiplied = bigDecimal.multiply(BigDecimal.valueOf(100))
        val result = multiplied.setScale(0, RoundingMode.DOWN).toString().padStart(4, '0')

        if (commandType == CommandEnum.ANULACION) {
            str =
                commandCode + cashierNo + serialNo + Constants.TRACE_NO + date + getDate() + time + getTime()

        } else if (commandType == CommandEnum.DEVOLUCION) {
            str =
                commandCode + cashierNo + serialNo + Constants.TRACE_NO + payMoney + result + currencyCode + senddate + sendtime + date + getDate() + time + getTime()
        } else if (commandType == CommandEnum.VENTA) {
            senddate += getDate()
            sendtime += getTime()
            str =
                commandCode + payMoney + result + cashierNo + serialNo + currencyCode + date + getDate() + time + getTime()
        } else if (commandType == CommandEnum.CONFIRMACION) {
            str =
                commandCode + cashierNo + serialNo + payMoney + result + currencyCode + Constants.TRACE_NO + Constants.CARD_NO + Constants.CARD_EXPIRY_DATE + date + getDate() + time + getTime()
        }
        Log.d("CommandUtils", "sendCommand: $str")
        socketManager.sendAsciiMessage(str, callback)
    }

    //获取日日月月年年年年 （jjmmaaaa）
    fun getDate(): String {
        val calendar = Calendar.getInstance()
        val year = calendar[Calendar.YEAR]
        val month = calendar[Calendar.MONTH] + 1
        val day = calendar[Calendar.DAY_OF_MONTH]
        return String.format("%02d%02d%04d", day, month, year)
    }

    //获取时时分分秒秒
    fun getTime(): String {
        val calendar = Calendar.getInstance()
        val hour = calendar[Calendar.HOUR_OF_DAY]
        val minute = calendar[Calendar.MINUTE]
        val second = calendar[Calendar.SECOND]
        return String.format("%02d%02d%02d", hour, minute, second)
    }


    fun parseTransactionData(data: String): Map<String, String> {
        val result = mutableMapOf<String, String>()

        // 1. 提取订单号 (008006000015)
        val orderPattern: Pattern = Pattern.compile("0080060\\d{5}")
        val orderMatcher: Matcher = orderPattern.matcher(data)
        if (orderMatcher.find()) {
            result["订单号"] = orderMatcher.group()
            Constants.TRACE_NO = orderMatcher.group()
        }

        // 2. 提取银行卡号 (0070164984513124707724)
        val cardPattern: Pattern = Pattern.compile("007016\\d{16}")
        val cardMatcher: Matcher = cardPattern.matcher(data)
        if (cardMatcher.find()) {
            result["银行卡号"] = cardMatcher.group()
            Constants.CARD_NO = cardMatcher.group()
        }

        // 3. 提取有效时间 (0170043308)
        val expiryPattern: Pattern = Pattern.compile("01700\\d{5}")
        val expiryMatcher: Matcher = expiryPattern.matcher(data)
        if (expiryMatcher.find()) {
            result["有效时间"] = expiryMatcher.group()
            Constants.CARD_EXPIRY_DATE = expiryMatcher.group()
        }

        return result
    }

    /**
     * 发送支付命令并接收支付结果回调
     * @param socketManager Socket管理器
     * @param commandType 命令类型
     * @param money 金额
     * @param callback 支付结果回调
     */
    fun sendPaymentCommand(
        socketManager: AsciiSocketManager, 
        commandType: CommandEnum, 
        money: String, 
        callback: AsciiSocketManager.PaymentResultCallback
    ) {
        var str = ""
        val commandCode = commandType.getCode()
        val bigDecimal = BigDecimal(money)
        val multiplied = bigDecimal.multiply(BigDecimal.valueOf(100))
        val result = multiplied.setScale(0, RoundingMode.DOWN).toString().padStart(4, '0')
        if (commandType == CommandEnum.VENTA) {
            senddate += getDate()
            sendtime += getTime()
            str = commandCode + payMoney + result + cashierNo + serialNo + currencyCode + date + getDate() + time + getTime()
        }

        Log.d("CommandUtils", "sendPaymentCommand: $str")
        
        // 使用订单ID或时间戳作为消息ID
        val messageId = System.currentTimeMillis().toString()
        
        // 发送支付消息并注册回调
        socketManager.sendPaymentMessage(str, messageId, null, callback)
    }
}