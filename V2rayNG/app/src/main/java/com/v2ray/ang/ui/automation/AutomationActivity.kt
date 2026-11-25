package com.v2ray.ang.ui.automation

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.v2ray.ang.R
import com.v2ray.ang.automation.AutomationManager
import com.v2ray.ang.automation.AutomationSchedule
import com.v2ray.ang.automation.AutomationScheduler
import com.v2ray.ang.automation.ShizukuHelper
import com.v2ray.ang.automation.WifiVpnAutomationMonitor
import com.v2ray.ang.ui.base.HelperBaseComponentActivity
import com.v2ray.ang.ui.compose.AppTopBar
import rikka.shizuku.Shizuku
import java.util.Calendar
import java.util.Locale

class AutomationActivity : HelperBaseComponentActivity() {

    @Composable
    override fun ScreenContent() {
        AutomationScreen(onBackClick = { finish() })
    }
}

private data class DayOption(val calendarDay: Int, val shortLabel: String)

private val WEEK_DAYS = listOf(
    DayOption(Calendar.MONDAY, "T2"),
    DayOption(Calendar.TUESDAY, "T3"),
    DayOption(Calendar.WEDNESDAY, "T4"),
    DayOption(Calendar.THURSDAY, "T5"),
    DayOption(Calendar.FRIDAY, "T6"),
    DayOption(Calendar.SATURDAY, "T7"),
    DayOption(Calendar.SUNDAY, "CN"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AutomationScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var autoVpnNoWifi by remember { mutableStateOf(AutomationManager.isAutoVpnOnNoWifi()) }
    var wifiSchedules by remember { mutableStateOf(AutomationManager.getWifiOffSchedules()) }
    var vibrateSchedules by remember { mutableStateOf(AutomationManager.getVibrateSchedules()) }

    var hasDndPermission by remember { mutableStateOf(checkDndAccess(context)) }
    var hasExactAlarmPerm by remember { mutableStateOf(checkExactAlarm(context)) }
    var shizukuRunning by remember { mutableStateOf(ShizukuHelper.isShizukuRunning()) }
    var shizukuGranted by remember { mutableStateOf(ShizukuHelper.hasPermission()) }

    DisposableEffect(lifecycleOwner) {
        val permListener = Shizuku.OnRequestPermissionResultListener { _, _ ->
            shizukuRunning = ShizukuHelper.isShizukuRunning()
            shizukuGranted = ShizukuHelper.hasPermission()
        }
        runCatching { Shizuku.addRequestPermissionResultListener(permListener) }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasDndPermission = checkDndAccess(context)
                hasExactAlarmPerm = checkExactAlarm(context)
                shizukuRunning = ShizukuHelper.isShizukuRunning()
                shizukuGranted = ShizukuHelper.hasPermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            runCatching { Shizuku.removeRequestPermissionResultListener(permListener) }
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Time picker target: Pair(isWifiOff, scheduleIndex)
    var editingTarget by remember { mutableStateOf<Pair<Boolean, Int>?>(null) }

    editingTarget?.let { (isWifiOff, index) ->
        val current = if (isWifiOff) wifiSchedules[index] else vibrateSchedules[index]
        val pickerState = rememberTimePickerState(
            initialHour = current.hour,
            initialMinute = current.minute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { editingTarget = null },
            title = {
                Text(
                    text = if (isWifiOff) "Chọn giờ tắt Wi-Fi (Lịch ${index + 1})"
                    else "Chọn giờ bật Chế độ rung (Lịch ${index + 1})"
                )
            },
            text = {
                TimePicker(state = pickerState)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (isWifiOff) {
                            wifiSchedules = wifiSchedules.mapIndexed { i, item ->
                                if (i == index) item.copy(hour = pickerState.hour, minute = pickerState.minute) else item
                            }
                            AutomationManager.saveWifiOffSchedules(wifiSchedules)
                        } else {
                            vibrateSchedules = vibrateSchedules.mapIndexed { i, item ->
                                if (i == index) item.copy(hour = pickerState.hour, minute = pickerState.minute) else item
                            }
                            AutomationManager.saveVibrateSchedules(vibrateSchedules)
                        }
                        AutomationScheduler.rescheduleAll(context)
                        editingTarget = null
                    }
                ) {
                    Text("Lưu")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingTarget = null }) {
                    Text("Huỷ")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.title_automation),
                onBackClick = onBackClick
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 0. Shizuku Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (shizukuGranted) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    }
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = when {
                            shizukuGranted -> "Shizuku: Đã cấp quyền ✅"
                            shizukuRunning -> "Shizuku: Đang chạy (Chưa cấp quyền)"
                            else -> "Shizuku: Chưa chạy"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (shizukuGranted) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = when {
                            shizukuGranted -> "Cho phép tự động bật/tắt 4G và tắt Wi-Fi hoàn toàn không cần Root."
                            shizukuRunning -> "Cấp quyền Shizuku để app tự động bật/tắt 4G và tắt Wi-Fi mà không cần Root."
                            else -> "Hãy mở ứng dụng Shizuku và khởi động dịch vụ, sau đó quay lại đây để cấp quyền."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (shizukuGranted) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (shizukuRunning && !shizukuGranted) {
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = { ShizukuHelper.requestPermission() }
                        ) {
                            Text("Cấp quyền Shizuku")
                        }
                    }
                }
            }

            // 1. Auto 4G & VPN when no Wi-Fi
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    var shizukuTestStatus by remember { mutableStateOf("") }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = "Tự động 4G & VPN theo Wi-Fi",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Tự động bật 4G và VPN khi mất Wi-Fi, tự tắt khi có Wi-Fi trở lại.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = autoVpnNoWifi,
                            onCheckedChange = { enabled ->
                                autoVpnNoWifi = enabled
                                AutomationManager.setAutoVpnOnNoWifi(enabled)
                                WifiVpnAutomationMonitor.syncRegistration(context, applyImmediately = true)
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Sử dụng Shizuku (hoặc Root) để tự động bật/tắt 4G + VPN ngay khi chuyển mạng Wi-Fi.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                shizukuTestStatus = "Đang bật 4G + VPN..."
                                WifiVpnAutomationMonitor.triggerEnable4gAndVpn(context) { ok, msg ->
                                    shizukuTestStatus = if (ok) "Bật OK ($msg)" else "Thất bại: $msg"
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Thử Bật 4G+VPN")
                        }
                        OutlinedButton(
                            onClick = {
                                shizukuTestStatus = "Đang tắt 4G + VPN..."
                                WifiVpnAutomationMonitor.triggerDisable4gAndVpn(context) { ok, msg ->
                                    shizukuTestStatus = if (ok) "Tắt OK ($msg)" else "Thất bại: $msg"
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Thử Tắt 4G+VPN")
                        }
                    }
                    if (shizukuTestStatus.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = shizukuTestStatus,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            if (!hasExactAlarmPerm && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PermissionWarningCard(
                    title = "Cần quyền Đặt báo thức chính xác",
                    description = "Cấp quyền để lịch tắt Wi-Fi và bật chế độ rung chạy đúng giờ từng phút.",
                    buttonText = "Cấp quyền ngay",
                    onClick = {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                Uri.parse("package:${context.packageName}")
                            )
                        )
                    }
                )
            }

            // 2. Auto Wi-Fi Off (3 schedules)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Tự động tắt Wi-Fi theo lịch",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "3 lịch trình độc lập theo thứ trong tuần và giờ:phút.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    wifiSchedules.forEachIndexed { index, schedule ->
                        if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                        ScheduleRowItem(
                            label = "Lịch ${index + 1}",
                            schedule = schedule,
                            onToggle = { enabled ->
                                wifiSchedules = wifiSchedules.mapIndexed { i, s ->
                                    if (i == index) s.copy(enabled = enabled) else s
                                }
                                AutomationManager.saveWifiOffSchedules(wifiSchedules)
                                AutomationScheduler.rescheduleAll(context)
                            },
                            onTimeClick = { editingTarget = true to index },
                            onDayToggle = { day ->
                                wifiSchedules = wifiSchedules.mapIndexed { i, s ->
                                    if (i == index) {
                                        val nextDays = if (day in s.daysOfWeek) {
                                            s.daysOfWeek - day
                                        } else {
                                            s.daysOfWeek + day
                                        }
                                        s.copy(daysOfWeek = nextDays)
                                    } else s
                                }
                                AutomationManager.saveWifiOffSchedules(wifiSchedules)
                                AutomationScheduler.rescheduleAll(context)
                            }
                        )
                    }
                }
            }

            // 3. Auto Vibrate Mode (3 schedules)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Tự động bật Chế độ rung theo lịch",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "3 lịch trình độc lập chuyển chuông điện thoại sang chế độ rung.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!hasDndPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = {
                                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                            }
                        ) {
                            Text("Cấp quyền Không làm phiền (DND)")
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    vibrateSchedules.forEachIndexed { index, schedule ->
                        if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                        ScheduleRowItem(
                            label = "Lịch ${index + 1}",
                            schedule = schedule,
                            onToggle = { enabled ->
                                vibrateSchedules = vibrateSchedules.mapIndexed { i, s ->
                                    if (i == index) s.copy(enabled = enabled) else s
                                }
                                AutomationManager.saveVibrateSchedules(vibrateSchedules)
                                AutomationScheduler.rescheduleAll(context)
                            },
                            onTimeClick = { editingTarget = false to index },
                            onDayToggle = { day ->
                                vibrateSchedules = vibrateSchedules.mapIndexed { i, s ->
                                    if (i == index) {
                                        val nextDays = if (day in s.daysOfWeek) {
                                            s.daysOfWeek - day
                                        } else {
                                            s.daysOfWeek + day
                                        }
                                        s.copy(daysOfWeek = nextDays)
                                    } else s
                                }
                                AutomationManager.saveVibrateSchedules(vibrateSchedules)
                                AutomationScheduler.rescheduleAll(context)
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ScheduleRowItem(
    label: String,
    schedule: AutomationSchedule,
    onToggle: (Boolean) -> Unit,
    onTimeClick: () -> Unit,
    onDayToggle: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 12.dp)
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .clickable { onTimeClick() }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = String.format(Locale.getDefault(), "%02d:%02d", schedule.hour, schedule.minute),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Switch(
                checked = schedule.enabled,
                onCheckedChange = onToggle
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            WEEK_DAYS.forEach { dayOption ->
                val selected = dayOption.calendarDay in schedule.daysOfWeek
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                        .clickable { onDayToggle(dayOption.calendarDay) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = dayOption.shortLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionWarningCard(
    title: String,
    description: String,
    buttonText: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = onClick) {
                Text(buttonText)
            }
        }
    }
}

private fun checkDndAccess(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
    return nm?.isNotificationPolicyAccessGranted == true
}

private fun checkExactAlarm(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
    return am?.canScheduleExactAlarms() == true
}
