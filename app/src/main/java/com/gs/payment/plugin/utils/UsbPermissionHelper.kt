package com.gs.payment.plugin.utils

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import com.hc.reader.AndroidUSB
import com.hc.reader.Card

/**
 * USB 设备权限申请工具：
 * 动态注册广播接收授权结果，并在需要时调用 requestPermission 弹出授权对话框。
 */
object UsbPermissionHelper {

    private const val TAG = "UsbPermissionHelper"
    const val ACTION_USB_PERMISSION = "com.gs.payment.plugin.USB_DEVICE_PERMISSION"

    data class PermissionResult(val granted: Boolean, val message: String)

    private var registered = false

    // 是否正在等待用户授权，避免重复弹框
    @Volatile
    private var requestingPermission = false

    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (ACTION_USB_PERMISSION != intent.action) return
            requestingPermission = false
            val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
            val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
            if (granted) {
                Logger.i(TAG, "USB设备授权成功: ${device?.deviceName}")
            } else {
                Logger.w(TAG, "USB设备授权被拒绝: ${device?.deviceName}")
            }
        }
    }

    /**
     * 注册授权结果广播（使用 applicationContext，避免 Receiver 销毁后失效）
     */
    fun register(context: Context) {
        if (registered) return
        try {
            val appContext = context.applicationContext
            val filter = IntentFilter(ACTION_USB_PERMISSION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.registerReceiver(permissionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                appContext.registerReceiver(permissionReceiver, filter)
            }
            registered = true
            Logger.i(TAG, "USB授权广播已注册")
        } catch (e: Exception) {
            Logger.e(TAG, "注册USB授权广播失败", e)
        }
    }

    /**
     * 验证 USB 读卡器权限：已授权直接返回 true，未授权则申请并返回包含真实原因的结果。
     * 供前台 MainActivity 启动时调用，确保授权弹窗在前台弹出。
     */
    fun verifyReaderPermission(context: Context): PermissionResult {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager
            ?: return PermissionResult(false, "获取 UsbManager 失败")

        // 先注册广播，确保能收到授权结果
        register(context)

        val device = findReaderDevice(context, usbManager)
            ?: return PermissionResult(false, "未找到USB读卡器，请检查设备是否插入")

        if (usbManager.hasPermission(device)) {
            Logger.i(TAG, "USB读卡器已授权: ${device.deviceName}")
            return PermissionResult(true, "USB读卡器权限已授予")
        }

        val requested = requestPermission(context, usbManager, device)
        return PermissionResult(
            false,
            if (requested) "已发送USB授权请求，请在弹框中允许"
            else "USB授权请求未发出（可能正在等待上次授权）"
        )
    }

    /**
     * 查找 USB 读卡器：优先用读卡库的方法，失败时回退遍历系统设备列表
     */
    fun findReaderDevice(context: Context, usbManager: UsbManager): UsbDevice? {
        try {
            val card: Card = AndroidUSB(context, usbManager)
            card.GetUsbReader()?.let {
                Logger.i(TAG, "读卡库找到USB设备: ${it.deviceName}")
                return it
            }
        } catch (e: Exception) {
            Logger.e(TAG, "读卡库查找USB设备异常", e)
        }
        // 兜底：遍历系统 USB 设备列表（无需权限即可枚举）
        // hcreader 库只认 VID=4292/PID=528，而当前读卡器是 VID=4292/PID=5140（CP210x 同系列）
        // GetUsbReader() 的 isSupported 按死 PID 过滤导致返回 null，
        // 但 OpenReader(device) 本身不校验 VID/PID，因此这里按 VID 匹配即可绕过。
        val devices = usbManager.deviceList.values.toList()
        if (devices.isEmpty()) {
            Logger.w(TAG, "系统USB设备列表为空，请检查设备是否插入")
            return null
        }
        devices.forEach {
            Logger.i(TAG, "枚举到USB设备: ${it.deviceName} (VID=${it.vendorId}, PID=${it.productId})")
        }
        // 优先 VID+PID 精确匹配，其次 VID 匹配（CP210x 同系列不同 PID）
        val reader = devices.firstOrNull { it.vendorId == SUPPORTED_VID && it.productId == SUPPORTED_PID }
            ?: devices.firstOrNull { it.vendorId == SUPPORTED_VID }
        if (reader != null) {
            Logger.i(TAG, "选定USB读卡器: ${reader.deviceName} (VID=${reader.vendorId}, PID=${reader.productId})")
        } else {
            Logger.w(TAG, "未找到 VID=$SUPPORTED_VID 的读卡器，退回第一个设备")
        }
        return reader ?: devices.firstOrNull()
    }

    private const val SUPPORTED_VID = 4292   // 0x10C4
    private const val SUPPORTED_PID = 528    // 0x0210 hcreader 库默认支持的 PID

    /**
     * 请求 USB 设备权限
     * @return 是否真正发出了请求
     */
    fun requestPermission(context: Context, manager: UsbManager, device: UsbDevice): Boolean {
        register(context)
        if (manager.hasPermission(device)) return false
        if (requestingPermission) {
            Logger.w(TAG, "正在等待上次授权，跳过重复请求")
            return false
        }
        requestingPermission = true
        return try {
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            // 显式指定包名，确保广播只发给本应用，避免被系统当作隐式广播拦截
            val intent = Intent(ACTION_USB_PERMISSION).setPackage(context.packageName)
            val pendingIntent = PendingIntent.getBroadcast(
                context.applicationContext,
                0,
                intent,
                flags
            )
            manager.requestPermission(device, pendingIntent)
            Logger.i(TAG, "已调用 requestPermission，授权对话框应出现: ${device.deviceName}")
            true
        } catch (e: Exception) {
            requestingPermission = false
            Logger.e(TAG, "请求USB设备权限失败", e)
            false
        }
    }

    /**
     * 检查设备是否已授权
     */
    fun hasPermission(manager: UsbManager, device: UsbDevice): Boolean =
        manager.hasPermission(device)
}
