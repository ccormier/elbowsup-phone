package com.keejii.elbowsup.ui

import android.app.Activity
import android.app.TimePickerDialog
import android.text.format.DateFormat
import com.google.android.material.chip.Chip
import com.keejii.elbowsup.core.MINUTES_PER_HOUR
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.phone.R
import org.fossify.phone.databinding.ViewElbowsupWindowBinding
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/** The day chips and start/end time rows shared by the rule and schedule editors. */
class WindowFields(
    private val activity: Activity,
    private val binding: ViewElbowsupWindowBinding,
    days: Set<DayOfWeek>,
    startMinute: Int,
    endMinute: Int,
) {
    var startMinute = startMinute
        private set
    var endMinute = endMinute
        private set

    private val chips = DayOfWeek.entries.associateWith { day ->
        val group = binding.elbowsupWindowDays
        val chip = activity.layoutInflater.inflate(R.layout.item_elbowsup_day_chip, group, false) as Chip
        chip.text = day.getDisplayName(TextStyle.SHORT, Locale.getDefault())
        chip.isChecked = day in days
        group.addView(chip)
        chip
    }

    val days: Set<DayOfWeek> get() = chips.filterValues { it.isChecked }.keys

    init {
        showTimes()
        binding.elbowsupWindowStart.setOnClickListener {
            pickTime(this.startMinute) { this.startMinute = it; showTimes() }
        }
        binding.elbowsupWindowEnd.setOnClickListener {
            pickTime(this.endMinute) { this.endMinute = it; showTimes() }
        }
    }

    fun setVisible(visible: Boolean) = binding.elbowsupWindowHolder.beVisibleIf(visible)

    private fun showTimes() {
        val starts = formatMinute(startMinute)
        val ends = formatMinute(endMinute)
        binding.elbowsupWindowStart.text = activity.getString(R.string.elbowsup_window_starts, starts)
        binding.elbowsupWindowEnd.text = activity.getString(R.string.elbowsup_window_ends, ends)
    }

    private fun pickTime(current: Int, onPicked: (Int) -> Unit) {
        TimePickerDialog(
            activity,
            { _, hour, minute -> onPicked(hour * MINUTES_PER_HOUR + minute) },
            current / MINUTES_PER_HOUR,
            current % MINUTES_PER_HOUR,
            DateFormat.is24HourFormat(activity),
        ).show()
    }
}
