package com.keejii.elbowsup.ui

import android.view.LayoutInflater
import com.keejii.elbowsup.BlockerRuntime
import com.keejii.elbowsup.core.PauseSchedule
import com.keejii.elbowsup.core.ScheduleDraft
import com.keejii.elbowsup.core.deleteSchedule
import com.keejii.elbowsup.core.draftOf
import com.keejii.elbowsup.core.upsertSchedule
import org.fossify.phone.activities.SimpleActivity
import org.fossify.phone.databinding.ActivityElbowsupBlockerBinding
import org.fossify.phone.databinding.ItemElbowsupScheduleBinding

/** The named pause schedules: rows and the add/edit/delete flows. */
class SchedulesSection(
    private val activity: SimpleActivity,
    private val binding: ActivityElbowsupBlockerBinding,
    private val runtime: BlockerRuntime,
) {
    init {
        binding.elbowsupAddSchedule.setOnClickListener { edit(ScheduleDraft()) }
    }

    fun render(schedules: List<PauseSchedule>) {
        binding.elbowsupSchedulesList.removeAllViews()
        val inflater = LayoutInflater.from(activity)
        schedules.forEach { schedule ->
            val row = ItemElbowsupScheduleBinding.inflate(inflater, binding.elbowsupSchedulesList, false)
            row.elbowsupScheduleTitle.text = schedule.name
            row.elbowsupScheduleSubtitle.text = windowText(schedule.window)
            row.elbowsupScheduleEnabled.isChecked = schedule.enabled
            row.elbowsupScheduleEnabled.setOnCheckedChangeListener { _, checked ->
                runtime.update { it.copy(schedules = upsertSchedule(it.schedules, schedule.copy(enabled = checked))) }
            }
            row.elbowsupScheduleHolder.setOnClickListener { edit(draftOf(schedule)) }
            binding.elbowsupSchedulesList.addView(row.root)
        }
    }

    private fun edit(draft: ScheduleDraft) {
        ScheduleDialog(
            activity = activity,
            draft = draft,
            onSave = { schedule ->
                val saved = if (schedule.id == 0L) schedule.copy(id = runtime.nextId()) else schedule
                runtime.update { it.copy(schedules = upsertSchedule(it.schedules, saved)) }
            },
            onDelete = if (draft.id == 0L) null else ({ delete(draft.id) }),
        )
    }

    private fun delete(id: Long) {
        runtime.update { it.copy(schedules = deleteSchedule(it.schedules, id)) }
    }
}
