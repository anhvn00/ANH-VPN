package com.v2ray.ang.automation

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import com.v2ray.ang.AppConfig
import com.v2ray.ang.root.RootManager
import com.v2ray.ang.root.RootProcessRunner
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AutomationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        LogUtil.i(AppConfig.TAG, "AutomationReceiver: received $action")

        when (action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                WifiVpnAutomationMonitor.syncRegistration(context)
                AutomationScheduler.rescheduleAll(context)
            }
            AutomationScheduler.ACTION_WIFI_OFF -> {
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        disableWifi(context)
                        AutomationScheduler.rescheduleAll(context)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
            AutomationScheduler.ACTION_VIBRATE_ON -> {
                enableVibrateMode(context)
                AutomationScheduler.rescheduleAll(context)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun disableWifi(context: Context) {
        if (ShizukuHelper.hasPermission() && ShizukuHelper.setWifiEnabled(context, false)) {
            LogUtil.i(AppConfig.TAG, "AutomationReceiver: Wi-Fi disabled via Shizuku")
            return
        }
        if (RootManager.isRootAvailable()) {
            val res = RootProcessRunner.run(listOf("su", "-c", "svc wifi disable"), 5000, 1024)
            if (res.code == 0) {
                LogUtil.i(AppConfig.TAG, "AutomationReceiver: Wi-Fi disabled via root")
                return
            }
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            try {
                val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                if (wm != null && wm.setWifiEnabled(false)) {
                    LogUtil.i(AppConfig.TAG, "AutomationReceiver: Wi-Fi disabled via WifiManager")
                    return
                }
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "AutomationReceiver: WifiManager disable failed", e)
            }
        }
        // Android 10+ restricts non-system apps from turning off Wi-Fi directly
        val panelIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Intent(Settings.Panel.ACTION_WIFI)
        } else {
            Intent(Settings.ACTION_WIFI_SETTINGS)
        }
        WifiVpnAutomationMonitor.notifyAction(
            context = context,
            notifId = 9102,
            title = "Lịch tắt Wi-Fi (ANHVN)",
            message = "Đến giờ tắt Wi-Fi — chạm để tắt nhanh Wi-Fi.",
            targetIntent = panelIntent
        )
    }

    private fun enableVibrateMode(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && nm != null && !nm.isNotificationPolicyAccessGranted) {
                // Attempt anyway (some OEMs allow RINGER_MODE_VIBRATE without DND access), fallback to prompt
                am.ringerMode = AudioManager.RINGER_MODE_VIBRATE
            } else {
                am.ringerMode = AudioManager.RINGER_MODE_VIBRATE
            }
            LogUtil.i(AppConfig.TAG, "AutomationReceiver: RingerMode set to VIBRATE")
        } catch (e: SecurityException) {
            LogUtil.e(AppConfig.TAG, "AutomationReceiver: DND access required for Vibrate mode", e)
            WifiVpnAutomationMonitor.notifyAction(
                context = context,
                notifId = 9103,
                title = "Lịch bật Chế độ rung (ANHVN)",
                message = "Cần cấp quyền Không làm phiền để tự động chuyển sang chế độ rung.",
                targetIntent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            )
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "AutomationReceiver: enableVibrateMode failed", e)
        }
    }
}
