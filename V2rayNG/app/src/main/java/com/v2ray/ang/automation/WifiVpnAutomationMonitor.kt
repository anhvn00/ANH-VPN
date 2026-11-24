package com.v2ray.ang.automation

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.core.LauncherManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.root.RootManager
import com.v2ray.ang.root.RootProcessRunner
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Monitors Wi-Fi connectivity when [AutomationManager.isAutoVpnOnNoWifi] is enabled:
 * - Wi-Fi disconnected -> enable 4G (if root) & start VPN.
 * - Wi-Fi connected -> stop VPN & disable 4G (if root).
 */
object WifiVpnAutomationMonitor {
    private const val CHANNEL_ID = "anhvn_automation_channel"
    private const val NOTIF_ID_DATA = 9101

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var registered = false

    @Volatile
    private var appContext: Context? = null

    private val activeWifiNetworks = HashSet<String>()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            val ctx = appContext ?: return
            if (!AutomationManager.isAutoVpnOnNoWifi()) return
            val wasEmpty = synchronized(activeWifiNetworks) {
                val empty = activeWifiNetworks.isEmpty()
                activeWifiNetworks.add(network.toString())
                empty
            }
            if (wasEmpty) {
                onWifiConnected(ctx)
            }
        }

        override fun onLost(network: Network) {
            val ctx = appContext ?: return
            if (!AutomationManager.isAutoVpnOnNoWifi()) return
            val isNowEmpty = synchronized(activeWifiNetworks) {
                activeWifiNetworks.remove(network.toString())
                activeWifiNetworks.isEmpty()
            }
            if (isNowEmpty && !isWifiCurrentlyConnected(ctx)) {
                onWifiDisconnected(ctx)
            }
        }
    }

    fun syncRegistration(context: Context) {
        val appCtx = context.applicationContext
        appContext = appCtx
        if (AutomationManager.isAutoVpnOnNoWifi()) {
            register(appCtx)
        } else {
            unregister(appCtx)
        }
    }

    @Synchronized
    private fun register(context: Context) {
        if (registered) return
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        try {
            synchronized(activeWifiNetworks) {
                activeWifiNetworks.clear()
            }
            cm.registerNetworkCallback(request, callback)
            registered = true
            LogUtil.i(AppConfig.TAG, "WifiVpnAutomationMonitor: registered")
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "WifiVpnAutomationMonitor: register failed", e)
        }
    }

    @Synchronized
    private fun unregister(context: Context) {
        if (!registered) return
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        try {
            cm.unregisterNetworkCallback(callback)
        } catch (_: Exception) {
        } finally {
            registered = false
            synchronized(activeWifiNetworks) { activeWifiNetworks.clear() }
            LogUtil.i(AppConfig.TAG, "WifiVpnAutomationMonitor: unregistered")
        }
    }

    fun isWifiCurrentlyConnected(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val active = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(active) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private fun onWifiConnected(context: Context) {
        LogUtil.i(AppConfig.TAG, "Automation: Wi-Fi connected -> stopping VPN & disabling 4G")
        LauncherManager.stopService(context)
        scope.launch {
            toggleMobileData(context, enable = false)
        }
    }

    private fun onWifiDisconnected(context: Context) {
        LogUtil.i(AppConfig.TAG, "Automation: No Wi-Fi -> enabling 4G & starting VPN")
        scope.launch {
            toggleMobileData(context, enable = true)
        }
        val needsVpnPerm = SettingsManager.isVpnMode() && VpnService.prepare(context) != null
        if (!needsVpnPerm) {
            LauncherManager.startServiceFromToggle(context)
        } else {
            notifyAction(
                context,
                NOTIF_ID_DATA,
                "ANHVN Automation",
                "Không có Wi-Fi: Vui lòng mở app để cấp quyền VPN cho tự động kết nối."
            )
        }
    }

    private fun toggleMobileData(context: Context, enable: Boolean) {
        if (RootManager.isRootAvailable()) {
            val cmd = if (enable) "svc data enable" else "svc data disable"
            val res = RootProcessRunner.run(listOf("su", "-c", cmd), 5000, 1024)
            if (res.code == 0) {
                LogUtil.i(AppConfig.TAG, "Automation: $cmd succeeded via root")
                return
            }
        }
        // Non-root Android restricts toggling Mobile Data programmatically; offer a quick panel notification.
        val label = if (enable) "Mất Wi-Fi: Chạm để bật 4G/Dữ liệu di động" else "Đã có Wi-Fi: Chạm để tắt 4G"
        val panelAction = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Settings.Panel.ACTION_INTERNET_CONNECTIVITY
        } else {
            Settings.ACTION_DATA_ROAMING_SETTINGS
        }
        notifyAction(context, NOTIF_ID_DATA, "ANHVN Automation (4G)", label, Intent(panelAction))
    }

    fun notifyAction(
        context: Context,
        notifId: Int,
        title: String,
        message: String,
        targetIntent: Intent? = null
    ) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "ANHVN Automation",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            nm.createNotificationChannel(channel)
        }
        val pi = targetIntent?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            PendingIntent.getActivity(
                context,
                notifId,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
        if (pi != null) builder.setContentIntent(pi)
        nm.notify(notifId, builder.build())
    }
}
