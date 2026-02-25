package com.gs.payment.plugin

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.await
import com.gs.payment.plugin.service.PaymentService
import com.gs.payment.plugin.utils.Logger
import com.gs.payment.plugin.utils.SerialPortConfig
import com.gs.payment.plugin.work.MonitoringWorker
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit


class MainActivity : ComponentActivity() {
    companion object {
        private const val TAG = "MainActivity"
        const val LOG_ACTION = "com.gs.payment.plugin.LOG_ACTION"
        const val LOG_MESSAGE_KEY = "LOG_MESSAGE_KEY"
    }

    private var logState: MutableList<String>? = null
    private val receiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let {
                if (LOG_ACTION == intent.action) {
                    val logMessage = intent.getStringExtra(LOG_MESSAGE_KEY)
                    logMessage?.let {
                        addLogMessage(logMessage)
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val logList = remember { mutableStateListOf<String>() }
            logState = logList

            ScrollableLogApp(logList)
        }
        val filter = IntentFilter(LOG_ACTION)
        registerReceiver(receiver, filter)
        
        // 启动PaymentService（如果未启动）
        startPaymentServiceIfNeeded()
        
        // 启动MonitoringWorker（如果未启动）
        startMonitoringWorkerIfNeeded()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(receiver)
    }

    fun addLogMessage(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val logEntry = "[$timestamp] $message"

        logState?.add(logEntry)
    }

    fun clearLogMessages() {
        logState?.clear()
    }

    /**
     * 启动PaymentService（如果未启动）
     */
    private fun startPaymentServiceIfNeeded() {
        if (!isPaymentServiceRunning()) {
            Logger.i(TAG, "PaymentService未运行，启动服务")
            PaymentService.start(this)
        } else {
            Logger.i(TAG, "PaymentService已在运行")
        }
    }

    /**
     * 检查PaymentService是否在运行
     */
    private fun isPaymentServiceRunning(): Boolean {
        return try {
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val runningServices = activityManager.getRunningServices(Integer.MAX_VALUE)
            val serviceClassName = PaymentService::class.java.name
            runningServices.any { serviceInfo ->
                serviceInfo.service.className == serviceClassName
            }
        } catch (e: Exception) {
            Logger.e(TAG, "检查PaymentService运行状态异常", e)
            false
        }
    }

    /**
     * 启动MonitoringWorker（如果未启动）
     */
    @SuppressLint("RestrictedApi")
    private fun startMonitoringWorkerIfNeeded() {
        lifecycleScope.launch {
            try {
                val workManager = WorkManager.getInstance(this@MainActivity)
                val workInfos = workManager.getWorkInfosForUniqueWork("payment_service_monitoring").await()
                
                val isRunning = workInfos.any { workInfo ->
                    workInfo.state == WorkInfo.State.RUNNING || 
                    workInfo.state == WorkInfo.State.ENQUEUED
                }
                
                if (!isRunning) {
                    Logger.i(TAG, "MonitoringWorker未运行，启动监控任务")
                    startMonitoringWorker()
                } else {
                    Logger.i(TAG, "MonitoringWorker已在运行")
                }
            } catch (e: Exception) {
                Logger.e(TAG, "检查MonitoringWorker运行状态异常，尝试启动", e)
                startMonitoringWorker()
            }
        }
    }

    /**
     * 启动MonitoringWorker监控任务
     */
    private fun startMonitoringWorker() {
        try {
            // 每15分钟检查一次PaymentService是否在运行
            val request = PeriodicWorkRequestBuilder<MonitoringWorker>(
                15, TimeUnit.MINUTES
            ).setInitialDelay(30, TimeUnit.SECONDS).build()

            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "payment_service_monitoring",
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
            Logger.i(TAG, "启动MonitoringWorker监控任务")
        } catch (e: Exception) {
            Logger.e(TAG, "启动MonitoringWorker失败", e)
        }
    }

    /**
     * Initiate payments
     * This method is limited to test data communication and does not contain business logic, and payment status control needs to be controlled by the business itself
     */
    private fun startPay() {
        val intent = Intent("com.coffeeji.payment.plugin.PAY_ACTON")
        intent.setPackage(packageName)
        intent.putExtra("ORDER_ID", "100003")
        intent.putExtra("ORDER_MONEY", "0.10")
        intent.putExtra("PRODUCT_ID", "1002003")
        intent.putExtra("PRODUCT_NAME", "Test the product name")
        intent.putExtra("ATTACH_CODE", "deviceNo=E01654&deviceId=247b3c45ba8b829b")
        sendBroadcast(intent)
    }

    /**
     * Cancel payment
     * This method is limited to test data communication and does not contain business logic, and payment status control needs to be controlled by the business itself
     */
    private fun cancelPay() {
        val intent = Intent("com.coffeeji.payment.plugin.PAY_CANCEL_ACTON")
        intent.setPackage(packageName)
        intent.putExtra("ORDER_ID", "100003")
        intent.putExtra("ORDER_MONEY", "0.10")
        sendBroadcast(intent)
    }

    /**
     * Feedback payment
     * This method is limited to test data communication and does not contain business logic, and payment status control needs to be controlled by the business itself
     */
    private fun feedbackPay(isSuccess: Boolean) {
        val intent = Intent("com.coffeeji.payment.plugin.MAKE_STATE_ACTION")
        intent.setPackage(packageName)
        intent.putExtra("ORDER_ID", "100003")
        intent.putExtra("ORDER_MONEY", "0.10")
        intent.putExtra("STATE", if (isSuccess) "success" else "fail")
        sendBroadcast(intent)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun ScrollableLogApp(logList: List<String>) {
        val context = this@MainActivity
        val lazyListState = rememberLazyListState()
        val coroutineScope = rememberCoroutineScope()
        
        // socket状态
        val currentDevicePath = remember {
            mutableStateOf(SerialPortConfig.getSocketIp(context))
        }
        
        // 对话框显示状态
        val showDialog = remember { mutableStateOf(false) }

        // 自动滚动到底部
        LaunchedEffect(logList.size) {
            if (logList.isNotEmpty()) {
                lazyListState.animateScrollToItem(logList.size - 1)
            }
        }
        
        // 处理socket配置确认
        fun onConfirmSerialPortSelection() {
            if (currentDevicePath.value.isNotEmpty()) {
                try {
                    
                    // 保存配置
                    SerialPortConfig.saveSocketIp(context, currentDevicePath.value)
                    // 关闭对话框
                    showDialog.value = false

                    // 重启服务
                    coroutineScope.launch {
                        try {
                            // 停止服务
                            PaymentService.stop(context)
                            // 等待服务停止
                            kotlinx.coroutines.delay(500)
                            // 启动服务
                            PaymentService.start(context)
                            Logger.i(TAG, "socket已更新为: ${currentDevicePath.value}:，服务已重启")
                        } catch (e: Exception) {
                            Logger.e(TAG, "重启服务失败", e)
                        }
                    }
                } catch (e: NumberFormatException) {
                    Logger.e(TAG, "错误: ${e.message}")
                }
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "Plugin payment debug log",
                            fontWeight = FontWeight.Bold
                        )
                    },
                    actions = {
                        // Socket配置显示和编辑
                        Row(
                            modifier = Modifier.padding(end = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = currentDevicePath.value,
                                modifier = Modifier.padding(end = 4.dp),
                                fontSize = 14.sp,
                                color = Color.Black
                            )
                            IconButton(onClick = { showDialog.value = true }) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit Socket",
                                    tint = Color.Black
                                )
                            }
                        }
                        Text(
                            text = "Count: ${logList.size}",
                            modifier = Modifier.padding(end = 16.dp),
                            fontSize = 14.sp
                        )
                        TextButton(onClick = { clearLogMessages() }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Clear Log",
                                tint = Color.Black
                            )
                            Text(
                                text = "Clear Log",
                                fontSize = 14.sp,
                                color = Color.Black
                            )
                        }
                    }
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .background(Color.White)
            ) {
                if (logList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "There is no log content at this time",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 16.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f)
                            .padding(8.dp),
                        state = lazyListState,
                        verticalArrangement = Arrangement.Top
                    ) {
                        items(logList) { logItem ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                            ) {
                                Text(
                                    text = logItem,
                                    modifier = Modifier.padding(12.dp),
                                    fontSize = 14.sp,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(
                        onClick = {
                            startPay()
                        }
                    ) {
                        Text("Initiate payments")
                    }

                    Button(
                        onClick = {
                            cancelPay()
                        }
                    ) {
                        Text("Order payment")
                    }

                    Button(
                        onClick = {
                            feedbackPay(false)
                        }
                    ) {
                        Text("Refund payment")
                    }

                    Button(
                        onClick = {
                            feedbackPay(true)
                        }
                    ) {
                        Text("Feedback payment")
                    }
                }
            }
        }
        
        // socket输入对话框
        if (showDialog.value) {
            AlertDialog(
                onDismissRequest = { showDialog.value = false },
                title = {
                    Text(
                        text = "输入http路径",
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column {
                        // IP地址输入框
                        TextField(
                            value = currentDevicePath.value,
                            onValueChange = { currentDevicePath.value = it },
                            label = { Text("IP地址") },
                            singleLine = true,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { onConfirmSerialPortSelection() },
                        enabled = currentDevicePath.value.isNotEmpty()
                    ) {
                        Text("确认")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDialog.value = false }) {
                        Text("取消")
                    }
                }
            )
        }
    }
}