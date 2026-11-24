package com.v2ray.ang.automation

import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.JsonUtil
import java.util.Calendar

data class AutomationSchedule(
    val id: Int,
    val enabled: Boolean = false,
    val hour: Int = 22,
    val minute: Int = 0,
    /** Days of week using Calendar constants: Calendar.MONDAY..Calendar.SUNDAY */
    val daysOfWeek: List<Int> = listOf(
        Calendar.MONDAY,
        Calendar.TUESDAY,
        Calendar.WEDNESDAY,
        Calendar.THURSDAY,
        Calendar.FRIDAY,
        Calendar.SATURDAY,
        Calendar.SUNDAY
    )
)

object AutomationManager {
    private const val SCHEDULE_COUNT = 3

    fun isAutoVpnOnNoWifi(): Boolean {
        return MmkvManager.decodeSettingsBool(AppConfig.PREF_AUTO_VPN_NO_WIFI, false)
    }

    fun setAutoVpnOnNoWifi(enabled: Boolean) {
        MmkvManager.encodeSettings(AppConfig.PREF_AUTO_VPN_NO_WIFI, enabled)
    }

    fun getWifiOffSchedules(): List<AutomationSchedule> {
        return loadSchedules(AppConfig.PREF_AUTO_WIFI_OFF_SCHEDULES, defaultHour = 23)
    }

    fun saveWifiOffSchedules(schedules: List<AutomationSchedule>) {
        MmkvManager.encodeSettings(
            AppConfig.PREF_AUTO_WIFI_OFF_SCHEDULES,
            JsonUtil.toJson(schedules.take(SCHEDULE_COUNT))
        )
    }

    fun getVibrateSchedules(): List<AutomationSchedule> {
        return loadSchedules(AppConfig.PREF_AUTO_VIBRATE_SCHEDULES, defaultHour = 22)
    }

    fun saveVibrateSchedules(schedules: List<AutomationSchedule>) {
        MmkvManager.encodeSettings(
            AppConfig.PREF_AUTO_VIBRATE_SCHEDULES,
            JsonUtil.toJson(schedules.take(SCHEDULE_COUNT))
        )
    }

    private fun loadSchedules(key: String, defaultHour: Int): List<AutomationSchedule> {
        val raw = MmkvManager.decodeSettingsString(key, null)
        val parsed = if (!raw.isNullOrBlank()) {
            try {
                JsonUtil.fromJson(raw, Array<AutomationSchedule>::class.java)?.toList().orEmpty()
            } catch (_: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
        return (0 until SCHEDULE_COUNT).map { idx ->
            parsed.getOrNull(idx)?.copy(id = idx) ?: AutomationSchedule(id = idx, hour = defaultHour)
        }
    }
}
