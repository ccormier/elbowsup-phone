package com.keejii.elbowsup.ui

import androidx.appcompat.app.AlertDialog
import com.keejii.elbowsup.core.PauseSchedule
import com.keejii.elbowsup.core.ScheduleDraft
import com.keejii.elbowsup.core.ScheduleResult
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.extensions.toast
import org.fossify.phone.R
import org.fossify.phone.activities.SimpleActivity
import org.fossify.phone.databinding.DialogElbowsupScheduleBinding

/** Adds or edits a named pause schedule. [onDelete] is null for a new schedule. */
class ScheduleDialog(
    private val activity: SimpleActivity,
    private val draft: ScheduleDraft,
    private val onSave: (PauseSchedule) -> Unit,
    private val onDelete: (() -> Unit)?,
) {
    private val binding = DialogElbowsupScheduleBinding.inflate(activity.layoutInflater)
    private val window = WindowFields(
        activity,
        binding.elbowsupScheduleWindow,
        draft.days,
        draft.startMinute,
        draft.endMinute,
    )

    init {
        binding.elbowsupScheduleName.setText(draft.name)
        binding.elbowsupScheduleDialogEnabled.isChecked = draft.enabled

        val builder = activity.getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.ok, null)
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
        if (onDelete != null) builder.setNeutralButton(org.fossify.commons.R.string.delete, null)
        val title = if (draft.id == 0L) R.string.elbowsup_schedule_new else R.string.elbowsup_schedule_edit
        activity.setupDialogStuff(binding.root, builder, title) { dialog ->
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { save(dialog) }
            if (onDelete != null) {
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                    onDelete.invoke()
                    dialog.dismiss()
                }
            }
        }
    }

    private fun save(dialog: AlertDialog) {
        val edited = draft.copy(
            name = binding.elbowsupScheduleName.text?.toString().orEmpty(),
            enabled = binding.elbowsupScheduleDialogEnabled.isChecked,
            days = window.days,
            startMinute = window.startMinute,
            endMinute = window.endMinute,
        )
        when (val result = edited.toSchedule()) {
            is ScheduleResult.Invalid -> activity.toast(activity.problemText(result.problem))
            is ScheduleResult.Valid -> {
                onSave(result.schedule)
                dialog.dismiss()
            }
        }
    }
}
