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

    // 等待中的最终授权结果回调；授权弹窗是异步的，不能用返回值直接拿到结果
    @Volatile
    private var permissionCallback: ((PermissionResult) -> Unit)? = null

    @Volatile
    private var waitingDeviceKey: String? = null

    private fun UsbDevice.key(): String = "$vendorId:$productId:$deviceName"

    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (ACTION_USB_PERMISSION != intent.action) return
            requestingPermission = false

            val expectedKey = waitingDeviceKey
            waitingDeviceKey = null

            val systemGranted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
            val deviceFromExtra = getDeviceFromIntent(intent)
            val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager
            // 某些 ROM 的授权回调不带 EXTRA_DEVICE，按请求时记录的设备标识回退查找
            val device = deviceFromExtra
                ?: usbManager?.deviceList?.values?.firstOrNull { it.key() == expectedKey }

            val deviceInfo = device?.let {
                "${it.deviceName} (VID=${it.vendorId}, PID=${it.productId})"
            } ?: "等待设备=$expectedKey，广播设备=null"

            if (expectedKey != null && device != null && device.key() != expectedKey) {
                Logger.w(TAG, "收到其他USB设备授权结果，已忽略: $deviceInfo")
                return
            }

            val reallyGranted = if (systemGranted && device != null && usbManager != null) {
                hasPermission(usbManager, device)
            } else {
                false
            }
            val message = when {
                reallyGranted -> "USB读卡器权限已授予: $deviceInfo"
                device == null -> "USB授权回调缺少设备信息，且无法回退定位设备，授权视为失败: $deviceInfo"
                !systemGranted -> "USB设备授权被拒绝: $deviceInfo"
                else -> "系统返回授权成功，但 UsbManager 校验失败，请重新插拔后授权: $deviceInfo"
            }

            if (reallyGranted) Logger.i(TAG, message) else Logger.w(TAG, message)
            permissionCallback?.invoke(PermissionResult(reallyGranted, message))
            permissionCallback = null
        }
    }

    private fun getDeviceFromIntent(intent: Intent): UsbDevice? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.setExtrasClassLoader(UsbDevice::class.java.classLoader)
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
        }
    } catch (e: Exception) {
        Logger.e(TAG, "解析USB_DEVICE失败", e)
        null
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
     * 查找 USB 读卡器：优先用读卡库匹配，失败时回退遍历系统设备列表。
     * hcreader 库只认 VID=4292/PID=528，当前读卡器是 VID=4292/PID=5140，
     * 但 OpenReader(device) 本身不校验 VID/PID，因此按 VID 回退即可绕过库匹配限制。
     */
    fun findReaderDevice(context: Context, usbManager: UsbManager): UsbDevice? {
        try {
            val card: Card = AndroidUSB(context, usbManager)
            card.GetUsbReader()?.let {
                Logger.i(
                    TAG,
                    "读卡库找到USB设备: ${it.deviceName} (VID=${it.vendorId}, PID=${it.productId})"
                )
                return it
            }
        } catch (e: Exception) {
            Logger.e(TAG, "读卡库查找USB设备异常", e)
        }

        val devices = usbManager.deviceList.values.toList()
        if (devices.isEmpty()) {
            Logger.w(TAG, "系统USB设备列表为空，请检查设备是否插入")
            return null
        }
        devices.forEach {
            Logger.i(TAG, "枚举到USB设备: ${it.deviceName} (VID=${it.vendorId}, PID=${it.productId})")
        }

        val reader = devices.firstOrNull {
            it.vendorId == SUPPORTED_VID && it.productId == SUPPORTED_PID
        } ?: devices.firstOrNull { it.vendorId == SUPPORTED_VID }
        if (reader != null) {
            Logger.i(
                TAG,
                "选定USB读卡器: ${reader.deviceName} (VID=${reader.vendorId}, PID=${reader.productId})"
            )
        } else {
            Logger.w(TAG, "未找到 VID=$SUPPORTED_VID 的读卡器，退回第一个设备")
        }
        return reader ?: devices.firstOrNull()
    }

    private const val SUPPORTED_VID = 4292   // 0x10C4
    private const val SUPPORTED_PID = 528    // 0x0210 hcreader 库默认支持的 PID

    /**
     * 验证 USB 读卡器权限。
     *
     * 返回值只表示当前同步检查结果：
     * - 已授权：granted=true；
     * - 需要弹框：granted=false，最终允许/拒绝结果通过 [onResult] 回调返回。
     *
     * 供前台 MainActivity 调用，确保授权弹窗在前台弹出。
     */
    fun verifyReaderPermission(
        context: Context,
        onResult: ((PermissionResult) -> Unit)? = null
    ): PermissionResult {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager
            ?: return PermissionResult(false, "获取 UsbManager 失败").also { clearPendingState() }

        // 先注册广播，确保能收到授权结果
        register(context)

        val device: UsbDevice = findReaderDevice(context, usbManager) ?: run {
            clearPendingState()
            return PermissionResult(false, "未找到USB读卡器，请检查设备是否插入")
        }

        if (hasPermission(usbManager, device)) {
            clearPendingState()
            val message = "USB读卡器权限已授予: " +
                    "${device.deviceName} (VID=${device.vendorId}, PID=${device.productId})"
            Logger.i(TAG, message)
            return PermissionResult(true, message)
        }

        if (requestingPermission) {
            return PermissionResult(false, "USB授权请求已发出，请在弹框中允许")
        }

        permissionCallback = onResult
        waitingDeviceKey = device.key()

        val requested = requestPermission(context, usbManager, device)
        if (!requested) {
            if (hasPermission(usbManager, device)) {
                clearPendingState()
                return PermissionResult(true, "USB读卡器权限已授予")
            }
            clearPendingState()
            return PermissionResult(false, "USB授权请求未发出，请确认 App 处于前台")
        }

        return PermissionResult(
            false,
            "已发送USB授权请求，请在弹框中允许；最终授权结果将通过回调返回"
        )
    }

    private fun clearPendingState() {
        permissionCallback = null
        waitingDeviceKey = null
    }

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
