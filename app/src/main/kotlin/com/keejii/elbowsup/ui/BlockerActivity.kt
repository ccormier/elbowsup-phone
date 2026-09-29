package com.keejii.elbowsup.ui

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import com.keejii.elbowsup.BlockerRuntime
import com.keejii.elbowsup.core.MINUTES_PER_HOUR
import com.keejii.elbowsup.core.PauseState
import com.keejii.elbowsup.core.TimedPause
import com.keejii.elbowsup.core.blockerStatus
import com.keejii.elbowsup.core.blockingSupported
import com.keejii.elbowsup.core.pauseState
import com.keejii.elbowsup.core.shouldCompleteSetup
import com.keejii.elbowsup.telecom.dialerRoleHeld
import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.hasPermission
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.helpers.PERMISSION_READ_CONTACTS
import org.fossify.commons.models.RadioItem
import org.fossify.phone.R
import org.fossify.phone.activities.SimpleActivity
import org.fossify.phone.databinding.ActivityElbowsupBlockerBinding
import java.time.ZonedDateTime
import java.util.Date

private const val PAUSE_15_MINUTES = 15
private const val PAUSE_1_HOUR = 60
private const val PAUSE_UNTIL_RESUME = 0

/** Status, setup, pause, the ordered rules and the pause schedules, on one scrolling screen. */
class BlockerActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityElbowsupBlockerBinding::inflate)
    private val runtime by lazy { BlockerRuntime.get(this) }
    private val rules by lazy { RulesSection(this, binding, runtime) }
    private val schedules by lazy { SchedulesSection(this, binding, runtime) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupEdgeToEdge(padBottomSystem = listOf(binding.elbowsupScrollview))
        setupMaterialScrollListener(binding.elbowsupScrollview, binding.elbowsupAppbar)
        binding.elbowsupSetupScreeningButton.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) setDefaultCallerIdApp()
        }
        binding.elbowsupSetupContactsButton.setOnClickListener {
            handlePermission(PERMISSION_READ_CONTACTS) { render() }
        }
        binding.elbowsupSetupDialerButton.setOnClickListener { launchSetDefaultDialerIntent() }
    }

    private val onRuntimeChanged: () -> Unit = { runOnUiThread { render() } }

    override fun onStart() {
        super.onStart()
        runtime.addListener(onRuntimeChanged)
    }

    override fun onStop() {
        runtime.removeListener(onRuntimeChanged)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.elbowsupAppbar, NavigationIcon.Arrow)
        render()
    }

    private fun screeningHeld(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        return getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) == true
    }

    private fun render() {
        val screening = screeningHeld()
        val contacts = hasPermission(PERMISSION_READ_CONTACTS)
        if (shouldCompleteSetup(runtime.snapshot().setupComplete, Build.VERSION.SDK_INT, screening, contacts)) {
            runtime.update { it.copy(setupComplete = true) }
        }
        val snapshot = runtime.snapshot()
        val now = ZonedDateTime.now()
        val pause = pauseState(
            snapshot.timedPause,
            snapshot.schedules,
            now.toInstant().toEpochMilli(),
            now.dayOfWeek,
            now.hour * MINUTES_PER_HOUR + now.minute,
        )
        val status = blockerStatus(snapshot.setupComplete, Build.VERSION.SDK_INT, screening, contacts, pause)
        val timeFormat = DateFormat.getTimeFormat(this)
        val until = snapshot.timedPause.untilEpochMillis?.let { timeFormat.format(Date(it)) }.orEmpty()

        binding.apply {
            elbowsupStatusHeadline.text = statusHeadline(status)
            elbowsupStatusDetail.text = statusDetail(status, until)
            val manuallyPaused = pause == PauseState.TIMED || pause == PauseState.UNTIL_RESUME
            elbowsupPauseButton.setText(if (manuallyPaused) R.string.elbowsup_resume else R.string.elbowsup_pause)
            elbowsupPauseButton.setOnClickListener { if (manuallyPaused) resume() else choosePause() }
        }
        renderSetup(screening, contacts)
        rules.render(snapshot.rules)
        schedules.render(snapshot.schedules)
        PauseNotifier.sync(this)

        updateTextColors(binding.elbowsupHolder)
        arrayOf(binding.elbowsupSetupLabel, binding.elbowsupRulesLabel, binding.elbowsupSchedulesLabel)
            .forEach { it.setTextColor(getProperPrimaryColor()) }
    }

    private fun renderSetup(screening: Boolean, contacts: Boolean) = binding.apply {
        val supported = blockingSupported(Build.VERSION.SDK_INT)
        val needScreening = supported && !screening
        val needDialer = supported && !dialerRoleHeld()
        elbowsupSetupScreeningRow.beVisibleIf(needScreening)
        elbowsupSetupContactsRow.beVisibleIf(!contacts)
        elbowsupSetupDialerRow.beVisibleIf(needDialer)
        elbowsupSetupHolder.beVisibleIf(needScreening || !contacts || needDialer)
    }

    private fun choosePause() {
        val items = arrayListOf(
            RadioItem(PAUSE_15_MINUTES, getString(R.string.elbowsup_pause_15_minutes)),
            RadioItem(PAUSE_1_HOUR, getString(R.string.elbowsup_pause_1_hour)),
            RadioItem(PAUSE_UNTIL_RESUME, getString(R.string.elbowsup_pause_until_resume)),
        )
        RadioGroupDialog(this, items, titleId = R.string.elbowsup_pause_title) { chosen ->
            val minutes = chosen as Int
            val pause = if (minutes == PAUSE_UNTIL_RESUME) {
                TimedPause.UNTIL_RESUME
            } else {
                TimedPause.forMinutes(minutes, System.currentTimeMillis())
            }
            runtime.update { it.copy(timedPause = pause) }
        }
    }

    private fun resume() {
        runtime.update { it.copy(timedPause = TimedPause.NONE) }
    }

    companion object {
        fun open(context: Context) = context.startActivity(Intent(context, BlockerActivity::class.java))
    }
}
