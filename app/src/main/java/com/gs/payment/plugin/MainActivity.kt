package com.gs.payment.plugin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class MainActivity : ComponentActivity() {
    companion object {
        const val LOG_ACTION = "com.gs.payment.plugin.LOG_ACTION"
        const val LOG_MESSAGE_KEY = "LOG_MESSAGE_KEY"
        const val PAY_STATE_ACTION = "com.coffeeji.payment.plugin.PAY_STATE_ACTION"
    }

    private var logState: MutableList<String>? = null
    private var keepAliveRunning: Boolean = false
    private val receiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let {
                when (it.action) {
                    LOG_ACTION -> {
                        val logMessage = it.getStringExtra(LOG_MESSAGE_KEY)
                        logMessage?.let { addLogMessage(logMessage) }
                    }
                    PAY_STATE_ACTION -> {
                        // 插件输入页回传的结果（主应用侧也应监听此 action 获取账号密码）
                        val state = it.getStringExtra("STATE")
                        val message = it.getStringExtra("MESSAGE")
                        val money = it.getStringExtra("MONEY")
                        val account = it.getStringExtra("ACCOUNT")
                        val password = it.getStringExtra("PASSWORD")
                        val orderId = it.getStringExtra("ORDER_ID")
                        addLogMessage(
                            "收到回传: state=$state, orderId=$orderId, money=$money, " +
                                "account=$account, password=${password?.let { p -> "*".repeat(p.length) }}"
                        )
                    }

                    else -> {}
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
        // 悬浮窗权限
        requestOverlayPermission()
        // 常驻前台服务
        toggleKeepAliveService()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(receiver)
        stopService(Intent(this, PayKeepAliveService::class.java))
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
     * Initiate payments
     * This method is limited to test data communication and does not contain business logic, and payment status control needs to be controlled by the business itself
     */
    private fun startPay() {
        val intent = Intent("com.coffeeji.payment.plugin.PAY_ACTION")
        intent.setPackage(packageName)
        intent.putExtra("ORDER_ID", "100001")
        intent.putExtra("ORDER_MONEY", "10")
        intent.putExtra("PRODUCT_ID", "1002001")
        intent.putExtra("PRODUCT_NAME", "Test the product name")
        sendBroadcast(intent)
    }

    /**
     * Initiate payment - Scanner
     * This method is limited to test data communication and does not contain business logic, and payment status control needs to be controlled by the business itself
     */
    private fun startPayScan() {
        val intent = Intent("com.coffeeji.payment.plugin.PAY_ACTION")
        intent.setPackage(packageName)
        intent.putExtra("ORDER_ID", "100001")
        intent.putExtra("ORDER_MONEY", "10")
        intent.putExtra("PRODUCT_ID", "1002001")
        intent.putExtra("PRODUCT_NAME", "Test the product name")
        intent.putExtra("SCAN_CODE", "a1223454565")
        sendBroadcast(intent)
    }

    /**
     * Cancel payment
     * This method is limited to test data communication and does not contain business logic, and payment status control needs to be controlled by the business itself
     */
    private fun cancelPay() {
        val intent = Intent("com.coffeeji.payment.plugin.PAY_CANCEL_ACTION")
        intent.setPackage(packageName)
        intent.putExtra("ORDER_ID", "100001")
        intent.putExtra("ORDER_MONEY", "10")
        sendBroadcast(intent)
    }

    /**
     * 启用常驻前台服务。常驻后，插件在后台收到 PAY_ACTION 广播时
     */
    private fun toggleKeepAliveService() {
        val intent = Intent(this, PayKeepAliveService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }

    /**
     * 跳转到系统“在其他应用上层显示”设置页，授予 SYSTEM_ALERT_WINDOW 权限。
     * 悬浮窗弹窗依赖该权限；Android 6+ 需用户手动授予。
     */
    private fun requestOverlayPermission() {
        if (Settings.canDrawOverlays(this)) {
            addLogMessage("悬浮窗权限已授予")
        } else {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }

    /**
     * Feedback payment
     * This method is limited to test data communication and does not contain business logic, and payment status control needs to be controlled by the business itself
     */
    private fun feedbackPay(isSuccess: Boolean) {
        val intent = Intent("com.coffeeji.payment.plugin.MAKE_STATE_ACTION")
        intent.setPackage(packageName)
        intent.putExtra("ORDER_ID", "100001")
        intent.putExtra("ORDER_MONEY", "10")
        intent.putExtra("STATE", if (isSuccess) "success" else "fail")
        sendBroadcast(intent)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun ScrollableLogApp(logList: List<String>) {
        val lazyListState = rememberLazyListState()

        // 自动滚动到底部
        LaunchedEffect(logList.size) {
            if (logList.isNotEmpty()) {
                lazyListState.animateScrollToItem(logList.size - 1)
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
                            startPayScan()
                        }
                    ) {
                        Text("Initiate payments-Scanner")
                    }

                    Button(
                        onClick = {
                            cancelPay()
                        }
                    ) {
                        Text("Cancel payment")
                    }

                    Button(
                        onClick = {
                            feedbackPay(true)
                        }
                    ) {
                        Text("Feedback payment-success")
                    }
                    Button(
                        onClick = {
                            feedbackPay(false)
                        }
                    ) {
                        Text("Feedback payment-Fail")
                    }
                }
            }
        }
    }
}