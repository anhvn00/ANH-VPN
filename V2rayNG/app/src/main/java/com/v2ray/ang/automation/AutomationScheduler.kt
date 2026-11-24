package com.v2ray.ang.automation

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.LogUtil
import java.util.Calendar

object AutomationScheduler {
    const val ACTION_WIFI_OFF = "com.anhvn.vpn.action.AUTOMATION_WIFI_OFF"
    const val ACTION_VIBRATE_ON = "com.anhvn.vpn.action.AUTOMATION_VIBRATE_ON"
    const val EXTRA_SCHEDULE_ID = "extra_schedule_id"

    private const val REQ_CODE_WIFI_BASE = 7100
    private const val REQ_CODE_VIBRATE_BASE = 7200

    fun rescheduleAll(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val wifiSchedules = AutomationManager.getWifiOffSchedules()
        wifiSchedules.forEach { schedule ->
            scheduleOne(context, alarmManager, schedule, ACTION_WIFI_OFF, REQ_CODE_WIFI_BASE + schedule.id)
        }

        val vibrateSchedules = AutomationManager.getVibrateSchedules()
        vibrateSchedules.forEach { schedule ->
            scheduleOne(context, alarmManager, schedule, ACTION_VIBRATE_ON, REQ_CODE_VIBRATE_BASE + schedule.id)
        }
    }

    private fun scheduleOne(
        context: Context,
        alarmManager: AlarmManager,
        schedule: AutomationSchedule,
        action: String,
        requestCode: Int
    ) {
        val intent = Intent(context, AutomationReceiver::class.java).apply {
            this.action = action
            putExtra(EXTRA_SCHEDULE_ID, schedule.id)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.cancel(pi)

        if (!schedule.enabled || schedule.daysOfWeek.isEmpty()) return

        val triggerAtMillis = computeNextTriggerMillis(schedule) ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            }
            LogUtil.i(
                AppConfig.TAG,
                "AutomationScheduler: scheduled $action #${schedule.id} at ${schedule.hour}:${schedule.minute} (ts=$triggerAtMillis)"
            )
        } catch (e: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "AutomationScheduler: failed to schedule $action #${schedule.id}", e)
        }
    }

    fun computeNextTriggerMillis(schedule: AutomationSchedule, nowMillis: Long = System.currentTimeMillis()): Long? {
        if (schedule.daysOfWeek.isEmpty()) return null
        val allowedDays = schedule.daysOfWeek.toSet()

        for (dayOffset in 0..7) {
            val candidate = Calendar.getInstance().apply {
                timeInMillis = nowMillis
                add(Calendar.DAY_OF_YEAR, dayOffset)
                set(Calendar.HOUR_OF_DAY, schedule.hour.coerceIn(0, 23))
                set(Calendar.MINUTE, schedule.minute.coerceIn(0, 59))
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val dow = candidate.get(Calendar.DAY_OF_WEEK)
            if (dow in allowedDays && candidate.timeInMillis > nowMillis + 1000L) {
                return candidate.timeInMillis
            }
        }
        return null
    }
}
