package com.kairo.app.ui.onboarding

import com.kairo.app.util.beta.BetaEvent
import com.kairo.app.util.beta.Events
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kairo.app.R
import com.kairo.app.alarm.AlarmHealth
import com.kairo.app.alarm.HealthIssue
import com.kairo.app.data.local.Role
import com.kairo.app.data.prefs.UserPrefs
import com.kairo.app.ui.PreviewData
import com.kairo.app.ui.alarms.rememberAlarmHealth
import com.kairo.app.ui.alarms.rememberHealthFixer
import com.kairo.app.ui.components.TimePickerDialog
import com.kairo.app.ui.containerFactory
import com.kairo.app.ui.design.KairoTheme
import com.kairo.app.ui.design.Radius
import com.kairo.app.ui.design.Spacing
import com.kairo.app.ui.design.components.GlassCard
import com.kairo.app.ui.design.components.KairoTextButton
import com.kairo.app.ui.design.components.KairoTextField
import com.kairo.app.ui.design.components.PermissionCard
import com.kairo.app.ui.design.components.PrimaryButton
import com.kairo.app.ui.design.components.TimeField
import com.kairo.app.ui.design.components.ToggleRow
import com.kairo.app.ui.design.laneStyle
import com.kairo.app.util.formatMinuteOfDay

enum class OnboardingStep { WELCOME, NAME, TIMES, LANES, PERMISSIONS }

@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel = viewModel(
        factory = containerFactory { c ->
            OnboardingViewModel(
                c.userPrefsRepository,
                c.roleRepository,
                loadSampleWeek = c::loadSampleWeek,
                createWakeAlarm = { wake, label ->
                    c.alarmRepository.save(
                        com.kairo.app.data.local.Alarm(
                            label = label, hour = wake / 60, minute = wake % 60,
                            daysOfWeekMask = com.kairo.app.alarm.AlarmDays.WEEKDAYS, type = com.kairo.app.data.local.AlarmType.WAKE,
                        ),
                    )
                },
            )
        },
    ),
) {
    val wakeLabel = stringResource(R.string.alarm_wake_label)
    val lanes by viewModel.lanes.collectAsStateWithLifecycle()
    val hidden by viewModel.hidden.collectAsStateWithLifecycle()
    val health = rememberAlarmHealth(null)
    val fix = rememberHealthFixer()
    val context = LocalContext.current
    var micGranted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    val micPrompt = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { micGranted = it }
    OnboardingContent(
        lanes = lanes,
        hiddenLanes = hidden,
        health = health,
        micGranted = micGranted,
        onRename = viewModel::rename,
        onHide = viewModel::setHidden,
        onFix = fix,
        onAllowMic = { micPrompt.launch(Manifest.permission.RECORD_AUDIO) },
        onFinish = { name, wake, sleep, alarm, sample ->
            Events.record(BetaEvent.ONBOARDING_DONE)
            // A new user has just seen the tour; "What's new" is for people updating.
            com.kairo.app.util.beta.BetaSupport.markWhatsNewSeen(context)
            viewModel.finish(name, wake, sleep, alarm, sample, wakeLabel)
        },
    )
}

@Composable
fun OnboardingContent(
    lanes: List<Role>,
    hiddenLanes: Set<Long>,
    health: AlarmHealth,
    micGranted: Boolean,
    onRename: (Role, String) -> Unit,
    onHide: (Role, Boolean) -> Unit,
    onFix: (HealthIssue) -> Unit,
    onAllowMic: () -> Unit,
    onFinish: (name: String, wake: Int, sleep: Int, wakeAlarm: Boolean, sample: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    startStep: OnboardingStep = OnboardingStep.WELCOME,
) {
    var step by rememberSaveable { mutableStateOf(startStep) }
    var name by rememberSaveable { mutableStateOf("") }
    var wake by rememberSaveable { mutableIntStateOf(UserPrefs.DEFAULT_WAKE_MINUTE) }
    var sleep by rememberSaveable { mutableIntStateOf(UserPrefs.DEFAULT_SLEEP_MINUTE) }
    var wakeAlarm by rememberSaveable { mutableStateOf(true) }
    var sample by rememberSaveable { mutableStateOf(false) }
    var showNameError by rememberSaveable { mutableStateOf(false) }

    val next: () -> Unit = {
        when {
            step == OnboardingStep.NAME && name.isBlank() -> showNameError = true
            step == OnboardingStep.PERMISSIONS -> onFinish(name, wake, sleep, wakeAlarm, sample)
            else -> step = OnboardingStep.entries[step.ordinal + 1]
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .background(KairoTheme.colors.background)
            .safeDrawingPadding()
            .imePadding(),
    ) {
        StepIndicator(step, Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.lg))
        Box(Modifier.weight(1f)) {
            AnimatedContent(
                targetState = step,
                transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) },
                label = "onboarding",
            ) { current ->
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Spacing.screen),
                    verticalArrangement = Arrangement.spacedBy(Spacing.lg),
                ) {
                    when (current) {
                        OnboardingStep.WELCOME -> WelcomeStep()
                        OnboardingStep.NAME -> NameStep(name, { name = it; showNameError = false }, showNameError, next)
                        OnboardingStep.TIMES -> TimesStep(wake, { wake = it }, sleep, { sleep = it }, wakeAlarm, { wakeAlarm = it })
                        OnboardingStep.LANES -> LanesStep(lanes, hiddenLanes, onRename, onHide)
                        OnboardingStep.PERMISSIONS -> PermissionsStep(health, micGranted, onFix, onAllowMic, sample, { sample = it })
                    }
                    Spacer(Modifier.height(Spacing.lg))
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (step != OnboardingStep.WELCOME) {
                KairoTextButton(stringResource(R.string.action_back), { step = OnboardingStep.entries[step.ordinal - 1] }, color = KairoTheme.colors.textSecondary)
            }
            Spacer(Modifier.weight(1f))
            PrimaryButton(
                stringResource(
                    when (step) {
                        OnboardingStep.WELCOME -> R.string.onboarding_begin
                        OnboardingStep.PERMISSIONS -> R.string.onboarding_finish
                        else -> R.string.action_next
                    },
                ),
                onClick = next,
            )
        }
    }
}

@Composable
private fun StepIndicator(step: OnboardingStep, modifier: Modifier = Modifier) {
    val colors = KairoTheme.colors
    val total = OnboardingStep.entries.size
    val label = stringResource(R.string.onboarding_step_of, step.ordinal + 1, total)
    Row(
        modifier.fillMaxWidth().semantics {
            contentDescription = label
            progressBarRangeInfo = ProgressBarRangeInfo((step.ordinal + 1).toFloat(), 1f..total.toFloat(), total)
        },
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        OnboardingStep.entries.forEach { s ->
            Box(
                Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(Radius.full)
                    .background(if (s.ordinal <= step.ordinal) colors.primary else colors.surface3),
            )
        }
    }
}

@Composable
private fun StepTitle(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(title, style = KairoTheme.type.headlineLarge, modifier = Modifier.semantics { heading() })
        Text(body, style = KairoTheme.type.bodyLarge, color = KairoTheme.colors.textSecondary)
    }
}

/** "Meet KAIRO": the logo over a large, slowly breathing orb glow. */
@Composable
private fun WelcomeStep() {
    val reduced = KairoTheme.reducedMotion
    val scale = if (reduced) {
        1f
    } else {
        val t = rememberInfiniteTransition(label = "welcome")
        val v by t.animateFloat(0.97f, 1.03f, infiniteRepeatable(tween(2_400), RepeatMode.Reverse), label = "breathe")
        v
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        Spacer(Modifier.height(Spacing.lg))
        val glow = KairoTheme.colors.primary
        Box(
            Modifier.size(300.dp).drawBehind {
                // A faint halo only: the logo's ring is see-through, so anything solid would show inside it.
                drawCircle(
                    Brush.radialGradient(listOf(glow.copy(alpha = 0.16f * scale), Color.Transparent), radius = size.minDimension / 2),
                    radius = size.minDimension / 2,
                )
            },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painterResource(R.drawable.kairo_logo_mark),
                contentDescription = stringResource(R.string.cd_kairo_logo),
                modifier = Modifier.size(260.dp).graphicsLayer { scaleX = scale; scaleY = scale },
            )
        }
        Text(stringResource(R.string.onboarding_meet), style = KairoTheme.type.displaySmall, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.onboarding_meet_body), style = KairoTheme.type.bodyLarge, color = KairoTheme.colors.textSecondary, textAlign = TextAlign.Center)
    }
}

@Composable
private fun NameStep(name: String, onName: (String) -> Unit, showError: Boolean, onDone: () -> Unit) {
    StepTitle(stringResource(R.string.onboarding_name_title), stringResource(R.string.onboarding_name_body))
    KairoTextField(
        value = name,
        onValueChange = onName,
        label = stringResource(R.string.profile_name_label),
        error = if (showError) stringResource(R.string.profile_name_required) else null,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = { onDone() }),
    )
}

@Composable
private fun TimesStep(wake: Int, onWake: (Int) -> Unit, sleep: Int, onSleep: (Int) -> Unit, wakeAlarm: Boolean, onWakeAlarm: (Boolean) -> Unit) {
    val context = LocalContext.current
    var picking by remember { mutableStateOf<Boolean?>(null) } // true = wake, false = sleep
    StepTitle(stringResource(R.string.onboarding_times_title), stringResource(R.string.onboarding_times_body))
    TimeField(stringResource(R.string.profile_wake_label), formatMinuteOfDay(context, wake), { picking = true }, Modifier.fillMaxWidth())
    TimeField(stringResource(R.string.profile_sleep_label), formatMinuteOfDay(context, sleep), { picking = false }, Modifier.fillMaxWidth())
    GlassCard {
        ToggleRow(
            stringResource(R.string.onboarding_wake_alarm, formatMinuteOfDay(context, wake)),
            wakeAlarm,
            onWakeAlarm,
            subtitle = stringResource(R.string.onboarding_wake_alarm_sub),
            icon = Icons.Outlined.Alarm,
        )
    }
    picking?.let { isWake ->
        TimePickerDialog(
            initialMinute = if (isWake) wake else sleep,
            onDismiss = { picking = null },
            onConfirm = { if (isWake) onWake(it) else onSleep(it); picking = null },
        )
    }
}

@Composable
private fun LanesStep(lanes: List<Role>, hidden: Set<Long>, onRename: (Role, String) -> Unit, onHide: (Role, Boolean) -> Unit) {
    StepTitle(stringResource(R.string.onboarding_lanes_title), stringResource(R.string.onboarding_lanes_body))
    LaneEditor(lanes, hidden, onRename, onHide)
}

/** Rename a lane or switch it off. Shared by onboarding and Settings > Profile. */
@Composable
fun LaneEditor(lanes: List<Role>, hidden: Set<Long>, onRename: (Role, String) -> Unit, onHide: (Role, Boolean) -> Unit) {
    val drafts = remember { mutableStateMapOf<Long, String>() }
    val colors = KairoTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        lanes.forEach { role ->
            val lane = laneStyle(role)
            val on = role.id !in hidden
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(12.dp).clip(Radius.full).background(lane.color))
                    Spacer(Modifier.width(Spacing.md))
                    KairoTextField(
                        value = drafts[role.id] ?: role.name,
                        onValueChange = {
                            drafts[role.id] = it
                            if (it.isNotBlank()) onRename(role, it)
                        },
                        label = stringResource(R.string.onboarding_lane_name),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(Spacing.md))
                    Switch(
                        checked = on,
                        onCheckedChange = { onHide(role, !it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = lane.color, checkedThumbColor = colors.onPrimary, uncheckedTrackColor = colors.surface3),
                        modifier = Modifier.semantics { contentDescription = role.name },
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionsStep(
    health: AlarmHealth,
    micGranted: Boolean,
    onFix: (HealthIssue) -> Unit,
    onAllowMic: () -> Unit,
    sample: Boolean,
    onSample: (Boolean) -> Unit,
) {
    val allow = stringResource(R.string.permission_allow)
    val allowed = stringResource(R.string.permission_allowed)
    val later = stringResource(R.string.permission_later)
    var skipped by rememberSaveable { mutableStateOf(setOf<String>()) }
    StepTitle(stringResource(R.string.onboarding_permissions_title), stringResource(R.string.onboarding_permissions_body))
    if ("notifications" !in skipped || health.notifications) {
        PermissionCard(
            Icons.Outlined.Notifications, stringResource(R.string.permission_notifications), stringResource(R.string.permission_notifications_why),
            health.notifications, allow, allowed, { onFix(HealthIssue.NOTIFICATIONS) }, laterLabel = later, onLater = { skipped = skipped + "notifications" },
        )
    }
    if ("mic" !in skipped || micGranted) {
        PermissionCard(
            Icons.Outlined.Mic, stringResource(R.string.permission_mic), stringResource(R.string.permission_mic_why),
            micGranted, allow, allowed, onAllowMic, laterLabel = later, onLater = { skipped = skipped + "mic" },
        )
    }
    val alarmsOk = health.exactAlarms && health.fullScreen
    if ("alarms" !in skipped || alarmsOk) {
        PermissionCard(
            Icons.Outlined.Alarm, stringResource(R.string.permission_alarms), stringResource(R.string.permission_alarms_why),
            alarmsOk, allow, allowed,
            { onFix(if (!health.exactAlarms) HealthIssue.EXACT_ALARMS else HealthIssue.FULL_SCREEN) },
            laterLabel = later, onLater = { skipped = skipped + "alarms" },
        )
    }
    GlassCard {
        ToggleRow(stringResource(R.string.onboarding_sample), sample, onSample, subtitle = stringResource(R.string.onboarding_sample_sub))
    }
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Text(stringResource(R.string.onboarding_permissions_later), style = KairoTheme.type.bodySmall, color = KairoTheme.colors.textTertiary)
    }
}

private val previewHealth = AlarmHealth(exactAlarms = true, fullScreen = false, notifications = false, batteryUnrestricted = true, alarmVolumeAudible = true, nextAlarm = null)

@Composable
private fun PreviewOnboarding(step: OnboardingStep) {
    KairoTheme {
        OnboardingContent(PreviewData.roles, setOf(4L), previewHealth, false, { _, _ -> }, { _, _ -> }, {}, {}, { _, _, _, _, _ -> }, startStep = step)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF05070F, heightDp = 820)
@Composable
private fun OnboardingWelcomePreview() = PreviewOnboarding(OnboardingStep.WELCOME)

@Preview(showBackground = true, backgroundColor = 0xFF05070F, heightDp = 820)
@Composable
private fun OnboardingNamePreview() = PreviewOnboarding(OnboardingStep.NAME)

@Preview(showBackground = true, backgroundColor = 0xFF05070F, heightDp = 820)
@Composable
private fun OnboardingTimesPreview() = PreviewOnboarding(OnboardingStep.TIMES)

@Preview(showBackground = true, backgroundColor = 0xFF05070F, heightDp = 820)
@Composable
private fun OnboardingLanesPreview() = PreviewOnboarding(OnboardingStep.LANES)

@Preview(showBackground = true, backgroundColor = 0xFF05070F, heightDp = 820)
@Composable
private fun OnboardingPermissionsPreview() = PreviewOnboarding(OnboardingStep.PERMISSIONS)
