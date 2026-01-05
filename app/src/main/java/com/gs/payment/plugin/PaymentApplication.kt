package com.gs.payment.plugin

import android.app.Application
import com.elvishew.xlog.LogConfiguration
import com.elvishew.xlog.LogLevel
import com.elvishew.xlog.XLog
import com.elvishew.xlog.flattener.PatternFlattener
import com.elvishew.xlog.printer.AndroidPrinter
import com.elvishew.xlog.printer.Printer
import com.elvishew.xlog.printer.file.FilePrinter
import com.elvishew.xlog.printer.file.backup.NeverBackupStrategy
import com.elvishew.xlog.printer.file.clean.FileLastModifiedCleanStrategy
import com.gs.payment.plugin.utils.LogFileNameGenerator
import java.io.File

/**
 * @author chyi
 * @date 2025/9/28 16:45
 */
class PaymentApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        initLog()
    }

    /**
     * 初始化日志框架
     */
    private fun initLog() {
        val config = LogConfiguration.Builder()
            .logLevel(LogLevel.ALL) // 指定日志级别，低于该级别的日志将不会被打印，默认为 LogLevel.ALL
            .tag("PaymentPlugin") // 指定 TAG，默认为 "X-LOG"
            .build()
        val androidPrinter: Printer = AndroidPrinter() // 通过 android.util.Log 打印日志的打印器
        
        // 获取日志文件路径
        val logPath = getLogPath()
        val filePrinter: Printer = FilePrinter.Builder(logPath) // 指定保存日志文件的路径
            .flattener(PatternFlattener("{d} {t}: {m}")) // 自定义日志格式
            .fileNameGenerator(LogFileNameGenerator()) // 指定日志文件名生成器
            .backupStrategy(NeverBackupStrategy()) // 指定日志文件备份策略，默认为 FileSizeBackupStrategy(1024 * 1024)
            .cleanStrategy(FileLastModifiedCleanStrategy((1000 * 60 * 60 * 24 * 7).toLong())) // 指定日志文件清除策略，保留7天
            .build()

        XLog.init(config, filePrinter, androidPrinter)
    }

    /**
     * 获取日志文件路径
     */
    private fun getLogPath(): String {
        val cacheDir = getExternalFilesDir(null) ?: filesDir
        val logDir = File(cacheDir, "Log")
        if (!logDir.exists()) {
            logDir.mkdirs()
        }
        return logDir.absolutePath
    }
}