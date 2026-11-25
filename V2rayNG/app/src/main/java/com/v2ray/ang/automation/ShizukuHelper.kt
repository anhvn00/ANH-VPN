package com.v2ray.ang.automation

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.util.LogUtil
import rikka.shizuku.Shizuku
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object ShizukuHelper {

    @Volatile
    private var shellService: IShizukuShellService? = null

    fun isShizukuRunning(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (_: Throwable) {
            false
        }
    }

    fun hasPermission(): Boolean {
        if (!isShizukuRunning()) return false
        return try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) {
            false
        }
    }

    fun requestPermission(requestCode: Int = 9001) {
        if (!isShizukuRunning()) return
        try {
            if (!Shizuku.isPreV11() && Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(requestCode)
            }
        } catch (e: Throwable) {
            LogUtil.e(AppConfig.TAG, "ShizukuHelper: requestPermission failed", e)
        }
    }

    fun setMobileDataEnabled(context: Context, enable: Boolean): Boolean {
        val cmd = if (enable) "svc data enable" else "svc data disable"
        return runCommand(context, cmd)
    }

    fun setWifiEnabled(context: Context, enable: Boolean): Boolean {
        val cmd = if (enable) "svc wifi enable" else "svc wifi disable"
        return runCommand(context, cmd)
    }

    @Synchronized
    fun runCommand(context: Context, command: String): Boolean {
        if (!hasPermission()) return false
        val service = getOrBindService(context) ?: return false
        return try {
            val code = service.runCommand(command)
            LogUtil.i(AppConfig.TAG, "ShizukuHelper: '$command' exited with $code")
            code == 0
        } catch (e: Throwable) {
            LogUtil.e(AppConfig.TAG, "ShizukuHelper: runCommand failed", e)
            shellService = null
            false
        }
    }

    private fun getOrBindService(context: Context): IShizukuShellService? {
        val existing = shellService
        if (existing != null && existing.asBinder().isBinderAlive) {
            return existing
        }

        val latch = CountDownLatch(1)
        var bound: IShizukuShellService? = null

        val args = Shizuku.UserServiceArgs(
            ComponentName(context.packageName, ShizukuShellService::class.java.name)
        )
            .daemon(false)
            .processNameSuffix("shizuku_shell")
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE)

        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                if (binder != null && binder.pingBinder()) {
                    bound = IShizukuShellService.Stub.asInterface(binder)
                    shellService = bound
                }
                latch.countDown()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                shellService = null
            }
        }

        return try {
            Shizuku.bindUserService(args, connection)
            latch.await(4, TimeUnit.SECONDS)
            bound ?: shellService
        } catch (e: Throwable) {
            LogUtil.e(AppConfig.TAG, "ShizukuHelper: bindUserService failed", e)
            null
        }
    }
}
