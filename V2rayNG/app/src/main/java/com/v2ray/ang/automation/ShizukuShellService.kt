package com.v2ray.ang.automation

import kotlin.system.exitProcess

class ShizukuShellService : IShizukuShellService.Stub() {
    override fun runCommand(command: String): Int {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            process.waitFor()
        } catch (_: Exception) {
            -1
        }
    }

    override fun destroy() {
        exitProcess(0)
    }
}
