package com.chupacabra.evchargeestimation.ui.screens

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.chupacabra.evchargeestimation.reminder.ChargeReminderReceiver
import com.chupacabra.evchargeestimation.ui.CalculatorUiState
import com.chupacabra.evchargeestimation.ui.components.GlassCard
import com.chupacabra.evchargeestimation.ui.components.NeonAccentBar
import com.chupacabra.evchargeestimation.ui.theme.NeonCyan
import com.chupacabra.evchargeestimation.ui.theme.NeonMint
import com.chupacabra.evchargeestimation.util.ChargeReminders
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CalculatorScreen(
    state: CalculatorUiState,
    onCurrentChange: (String) -> Unit,
    onHoursToFullChange: (String) -> Unit,
    onMinutesPartToFullChange: (String) -> Unit,
    onDesiredChange: (String) -> Unit,
    onClear: () -> Unit,
    onOpenCamera: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val scheme = MaterialTheme.colorScheme
    val dark = isSystemInDarkTheme()

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = scheme.primary,
        unfocusedBorderColor = scheme.outline.copy(alpha = 0.55f),
        focusedLabelColor = scheme.primary,
        cursorColor = scheme.primary,
        focusedContainerColor = scheme.surface.copy(alpha = if (dark) 0.35f else 0.6f),
        unfocusedContainerColor = scheme.surface.copy(alpha = if (dark) 0.25f else 0.45f)
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "EV Charge",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onBackground
                )
            }
            NeonAccentBar()
            Text(
                text = "See how long until your car reaches the charge you want. Type the numbers from the car screen, or scan them with the camera.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant
            )
        }

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "FROM THE CAR",
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.primary,
                    letterSpacing = 1.2.sp
                )

                OutlinedTextField(
                    value = state.currentPercent,
                    onValueChange = onCurrentChange,
                    label = { Text("Battery now") },
                    placeholder = { Text("e.g. 52") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = fieldColors,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next
                    ),
                    suffix = { Text("%", color = scheme.onSurfaceVariant) }
                )

                Text(
                    text = "Time the car shows to full",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    letterSpacing = 0.4.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = state.hoursToFull,
                        onValueChange = onHoursToFullChange,
                        label = { Text("Hours") },
                        placeholder = { Text("3") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.medium,
                        colors = fieldColors,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next
                        ),
                        suffix = { Text("h", color = scheme.onSurfaceVariant) }
                    )
                    OutlinedTextField(
                        value = state.minutesPartToFull,
                        onValueChange = onMinutesPartToFullChange,
                        label = { Text("Minutes") },
                        placeholder = { Text("25") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.medium,
                        colors = fieldColors,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next
                        ),
                        suffix = { Text("min", color = scheme.onSurfaceVariant) }
                    )
                }

                if (state.timeToFullLabel.isNotBlank()) {
                    Text(
                        text = "Car says ${state.timeToFullLabel} to 100%",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (dark) NeonCyan.copy(alpha = 0.95f) else scheme.primary
                    )
                }

                OutlinedTextField(
                    value = state.desiredPercent,
                    onValueChange = onDesiredChange,
                    label = { Text("I want to reach") },
                    placeholder = { Text("e.g. 80") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = fieldColors,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { focusManager.clearFocus() }
                    ),
                    suffix = { Text("%", color = scheme.onSurfaceVariant) }
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FilledTonalButton(
                onClick = onOpenCamera,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = scheme.secondaryContainer,
                    contentColor = scheme.onSecondaryContainer
                )
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Scan", fontWeight = FontWeight.SemiBold)
            }
            OutlinedButton(
                onClick = onClear,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(Icons.Default.Clear, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Clear")
            }
        }

        ResultCard(state = state, dark = dark)

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ResultCard(state: CalculatorUiState, dark: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val hasError = state.error != null && state.resultMinutes == null
    val hasResult = state.resultMinutes != null
    val canRemind = (state.resultMinutes ?: 0) > 0 && !hasError

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        accentBorder = hasResult || hasError,
        cornerRadius = 24.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "YOU'LL BE READY IN",
                style = MaterialTheme.typography.labelMedium,
                color = when {
                    hasError -> scheme.error
                    hasResult -> scheme.primary
                    else -> scheme.onSurfaceVariant
                },
                letterSpacing = 1.4.sp
            )
            Spacer(Modifier.height(12.dp))

            when {
                hasError -> {
                    Text(
                        text = state.error.orEmpty(),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = scheme.error
                    )
                }
                hasResult -> {
                    Text(
                        text = state.resultLabel,
                        fontSize = 42.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (dark) NeonMint else scheme.primary,
                        letterSpacing = (-0.5).sp
                    )
                    if (state.resultMinutes != null && state.resultMinutes > 0) {
                        Spacer(Modifier.height(4.dp))
                        val ready = ChargeReminders.readyAt(
                            state.resultMinutes,
                            state.desiredPercent.toIntOrNull() ?: 80
                        )
                        val clock = SimpleDateFormat("h:mm a", Locale.getDefault())
                            .format(Date(ready.endMillis))
                        Text(
                            text = "Around $clock",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (dark) NeonCyan.copy(alpha = 0.9f) else scheme.onSurfaceVariant
                        )
                    }
                    state.error?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant
                        )
                    }

                    if (canRemind) {
                        Spacer(Modifier.height(18.dp))
                        Text(
                            text = "Get a reminder",
                            style = MaterialTheme.typography.labelMedium,
                            color = scheme.onSurfaceVariant,
                            letterSpacing = 0.6.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        ReminderActions(
                            durationMinutes = state.resultMinutes!!,
                            desiredPercent = state.desiredPercent.toIntOrNull() ?: 80
                        )
                    }
                }
                else -> {
                    Text(
                        text = "— —",
                        fontSize = 42.sp,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onSurfaceVariant.copy(alpha = 0.45f)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Enter the numbers or tap Scan",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ReminderActions(
    durationMinutes: Int,
    desiredPercent: Int
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val lifecycleOwner = LocalLifecycleOwner.current
    val ready = ChargeReminders.readyAt(durationMinutes, desiredPercent)
    var pendingKind by remember { mutableStateOf<String?>(null) }
    var activeReminder by remember {
        mutableStateOf(ChargeReminders.getActiveReminder(context))
    }

    fun refreshActive() {
        activeReminder = ChargeReminders.getActiveReminder(context)
    }

    // Refresh when returning to the screen (e.g. after the reminder fired).
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshActive()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val kind = pendingKind
        pendingKind = null
        if (!granted) {
            Toast.makeText(
                context,
                "Allow notifications so we can remind you when charging is done",
                Toast.LENGTH_LONG
            ).show()
            return@rememberLauncherForActivityResult
        }
        if (kind != null) {
            handleInAppSchedule(context, ready, kind, onChanged = { refreshActive() })
        }
    }

    fun requestOrSchedule(kind: String) {
        if (ChargeReminders.needsNotificationPermission(context)) {
            pendingKind = kind
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                handleInAppSchedule(context, ready, kind, onChanged = { refreshActive() })
            }
        } else {
            handleInAppSchedule(context, ready, kind, onChanged = { refreshActive() })
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        activeReminder?.let { active ->
            Text(
                text = "${active.kindLabel} set for ${active.readyAtLabel}",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            TextButton(
                onClick = {
                    val cleared = ChargeReminders.clearReminder(context)
                    refreshActive()
                    Toast.makeText(
                        context,
                        if (cleared) "Reminder cleared" else "No reminder to clear",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            ) {
                Icon(
                    Icons.Default.Clear,
                    contentDescription = null,
                    tint = scheme.error,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("Clear reminder", color = scheme.error)
            }
            Spacer(Modifier.height(4.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            TextButton(onClick = { requestOrSchedule(ChargeReminderReceiver.KIND_TIMER) }) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Timer, contentDescription = null, tint = scheme.primary)
                    Spacer(Modifier.height(2.dp))
                    Text("Timer", fontSize = 12.sp, color = scheme.onSurface)
                }
            }
            TextButton(onClick = { requestOrSchedule(ChargeReminderReceiver.KIND_ALARM) }) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Alarm, contentDescription = null, tint = scheme.primary)
                    Spacer(Modifier.height(2.dp))
                    Text("Alarm", fontSize = 12.sp, color = scheme.onSurface)
                }
            }
            TextButton(
                onClick = {
                    if (!ChargeReminders.launchCalendar(context, ready)) {
                        Toast.makeText(
                            context,
                            "Couldn’t open Calendar on this phone",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Event, contentDescription = null, tint = scheme.primary)
                    Spacer(Modifier.height(2.dp))
                    Text("Calendar", fontSize = 12.sp, color = scheme.onSurface)
                }
            }
        }
    }
}

private fun handleInAppSchedule(
    context: android.content.Context,
    ready: ChargeReminders.ReadyAt,
    kind: String,
    onChanged: () -> Unit
) {
    when (
        val result = ChargeReminders.scheduleInAppReminder(context, ready, kind)
    ) {
        is ChargeReminders.ScheduleResult.Scheduled -> {
            val label = if (kind == ChargeReminderReceiver.KIND_TIMER) "Timer" else "Alarm"
            val exactNote = if (result.exact) "" else " (may be a few minutes off)"
            Toast.makeText(
                context,
                "$label set for ${result.readyAtLabel}$exactNote",
                Toast.LENGTH_LONG
            ).show()
            onChanged()
        }
        ChargeReminders.ScheduleResult.NeedNotificationPermission -> {
            Toast.makeText(
                context,
                "Allow notifications so we can remind you",
                Toast.LENGTH_LONG
            ).show()
        }
        ChargeReminders.ScheduleResult.Failed -> {
            Toast.makeText(
                context,
                "Couldn’t set the reminder. Try again.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
