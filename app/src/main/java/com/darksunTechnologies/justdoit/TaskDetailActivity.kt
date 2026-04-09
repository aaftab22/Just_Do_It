package com.darksunTechnologies.justdoit

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.darksunTechnologies.justdoit.databinding.ActivityTaskDetailBinding
import com.darksunTechnologies.justdoit.models.Task
import com.darksunTechnologies.justdoit.notifications.AlarmHelper
import com.darksunTechnologies.justdoit.viewmodel.TaskViewModel
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@SuppressLint("SetTextI18n")
class TaskDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTaskDetailBinding
    private val taskViewModel: TaskViewModel by viewModels()

    // Edit mode (title + description only)
    private var isEditMode = false

    // Mutable state — auto-saved immediately on change
    private var selectedDueDateMillis: Long? = null
    private var selectedHour: Int = -1
    private var selectedMinute: Int = -1
    private var currentIsHighPriority: Boolean = false
    private var currentHasReminder: Boolean = false
    private var currentIsCompleted: Boolean = false
    private var taskCreatedAt: Long = System.currentTimeMillis()
    private var taskSource: String = "manual"


    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        binding = ActivityTaskDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Read intent extras into mutable state
        val name = intent.getStringExtra("task_name") ?: ""
        val description = intent.getStringExtra("task_description")
        currentIsHighPriority = intent.getBooleanExtra("task_priority", false)
        currentIsCompleted = intent.getBooleanExtra("task_completed", false)
        currentHasReminder = intent.getBooleanExtra("task_has_reminder", false)
        taskCreatedAt = intent.getLongExtra("task_created_at", System.currentTimeMillis())
        taskSource = intent.getStringExtra("task_source") ?: "manual"

        selectedDueDateMillis = intent.getLongExtra("task_due_date", -1L).takeIf { it != -1L }
        selectedDueDateMillis?.let {
            val cal = Calendar.getInstance().apply { timeInMillis = it }
            val h = cal.get(Calendar.HOUR_OF_DAY)
            val m = cal.get(Calendar.MINUTE)
            if (h != 0 || m != 0) {
                selectedHour = h
                selectedMinute = m
            }
        }

        // --- Toolbar ---
        setSupportActionBar(binding.myToolbar)
        supportActionBar?.title = ""
        binding.myToolbar.setNavigationOnClickListener { finish() }

        // --- Populate UI ---
        binding.etTitle.setText(name)

        binding.etDescription.setText(if (!description.isNullOrBlank()) description else "")
        if (description.isNullOrBlank()) {
            binding.etDescription.hint = "No description"
        }

        refreshPriorityChip()
        refreshStatusChip()
        refreshDueDateDisplay()
        refreshDueTimeDisplay()
        binding.switchReminder.isChecked = currentHasReminder
        binding.tvReminderStatus.text = if (currentHasReminder) "On" else "Off"


        // AUTO-SAVE: Due Date (immediate on picker OK)
        binding.tvDueDate.setOnClickListener {
            val picker = MaterialDatePicker.Builder
                .datePicker()
                .setTitleText("Select due date")
                .setSelection(selectedDueDateMillis ?: MaterialDatePicker.todayInUtcMilliseconds())
                .build()

            picker.addOnPositiveButtonClickListener { dateMillis ->
                // 1. Create a UTC calendar to read year/month/day exactly as picked
                val utcCal = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
                    timeInMillis = dateMillis
                }
                
                // 2. Create a local calendar and force it to match those UTC values
                val localCal = Calendar.getInstance().apply {
                    set(utcCal.get(Calendar.YEAR), utcCal.get(Calendar.MONTH), utcCal.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                
                selectedDueDateMillis = localCal.timeInMillis
                refreshDueDateDisplay()
                autoSave()
            }


            picker.show(supportFragmentManager, "DUE_DATE_PICKER")
        }


        // AUTO-SAVE: Due Time (immediate on picker OK)
        binding.tvDueTime.setOnClickListener {
            val picker = MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_12H)
                .setHour(if (selectedHour >= 0) selectedHour else 9)
                .setMinute(if (selectedMinute >= 0) selectedMinute else 0)
                .setTitleText("Select due time")
                .build()

            picker.addOnPositiveButtonClickListener {
                selectedHour = picker.hour
                selectedMinute = picker.minute
                refreshDueTimeDisplay()
                autoSave() // This will merge current selectedDueDateMillis + time in buildCurrentTask()
            }

            picker.show(supportFragmentManager, "DUE_TIME_PICKER")
        }

        // AUTO-SAVE: Priority chip (toggle on tap)
        binding.priorityCard.setOnClickListener {
            currentIsHighPriority = !currentIsHighPriority
            refreshPriorityChip()
            autoSave() // immediate save
        }

        // AUTO-SAVE: Status chip (toggle on tap)
        binding.statusCard.setOnClickListener {
            currentIsCompleted = !currentIsCompleted
            refreshStatusChip()
            autoSave() // immediate save
        }

        // AUTO-SAVE: Reminder toggle (on switch change)
        binding.switchReminder.setOnCheckedChangeListener { _, isChecked ->
            currentHasReminder = isChecked
            binding.tvReminderStatus.text = if (isChecked) "On" else "Off"
            autoSave() // immediate save
        }

        // MANUAL SAVE: Title + Description (via pencil/check)
        binding.btnEdit.setOnClickListener {
            if (isEditMode) {
                saveTask()
                disableEditMode()
            } else {
                enableEditMode()
            }
        }

        // --- Complete button (bottom bar) ---
        binding.actionComplete.setOnClickListener {
            val id = intent.getIntExtra("task_id", -1)
            if (id != -1) {
                currentIsCompleted = !currentIsCompleted
                autoSave()
                val msg = if (currentIsCompleted) "Task Completed!" else "Task Reactivated"
                android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show()
                finish()
            }
        }

        // --- Duplicate button ---
        binding.actionDuplicate.setOnClickListener {
            handleDuplicateTask()
        }

        // --- Delete button ---
        binding.actionDelete.setOnClickListener {
            val id = intent.getIntExtra("task_id", -1)
            if (id != -1) {
                val resultIntent = android.content.Intent().apply {
                    putExtra("deleted_task_id", id)
                    putExtra("deleted_task_name", intent.getStringExtra("task_name") ?: "")
                    putExtra("deleted_task_priority", intent.getBooleanExtra("task_priority", false))
                }
                setResult(RESULT_TASK_DELETED, resultIntent)
                finish()
            }
        }

        // If opened from "Task saved! EDIT" Snack bar, go straight to edit mode
        if (intent.getBooleanExtra("start_in_edit_mode", false)) {
            enableEditMode()
        }
    }

    // Build task from current state (single source of truth)
    private fun buildCurrentTask(): Task {
        val id = intent.getIntExtra("task_id", -1)
        val title = binding.etTitle.text.toString().trim()
        val desc = binding.etDescription.text.toString().trim()

        val finalDueDate: Long? = if (selectedDueDateMillis != null) {
            val cal = Calendar.getInstance().apply {
                timeInMillis = selectedDueDateMillis!!
                if (selectedHour >= 0) {
                    set(Calendar.HOUR_OF_DAY, selectedHour)
                    set(Calendar.MINUTE, selectedMinute)
                    set(Calendar.SECOND, 0)
                }
            }
            cal.timeInMillis
        } else null

        return Task(
            id = id,
            name = title.ifEmpty { intent.getStringExtra("task_name") ?: "" },
            description = desc,
            isHighPriority = currentIsHighPriority,
            isCompleted = currentIsCompleted,
            dueDate = finalDueDate,
            hasReminder = currentHasReminder,
            createdAt = taskCreatedAt,
            source = taskSource
        )
    }


     //Auto-save: called after date/time/priority/reminder changes.
     //Saves the full task state using current title+description from the EditTexts.
    private fun autoSave() {
        val id = intent.getIntExtra("task_id", -1)
        if (id == -1) return
        val task = buildCurrentTask()
        taskViewModel.updateTask(buildCurrentTask())
        AlarmHelper().scheduleReminder(this, task)
    }

     //Manual save: called when user taps the checkmark after editing title/description.
    private fun saveTask() {
        val id = intent.getIntExtra("task_id", -1)
        if (id == -1) return

        val newTitle = binding.etTitle.text.toString().trim()
        if (newTitle.isEmpty()) {
            binding.etTitle.error = "Title cannot be empty"
            return
        }

        taskViewModel.updateTask(buildCurrentTask())

        android.widget.Toast.makeText(this, "Task updated!", android.widget.Toast.LENGTH_SHORT).show()
    }

    // Edit mode helpers (title + description only)

    private fun enableEditMode() {
        isEditMode = true

        binding.etTitle.isFocusable = true
        binding.etTitle.isFocusableInTouchMode = true
        binding.etTitle.isCursorVisible = true
        binding.etTitle.requestFocus()

        binding.etDescription.isFocusable = true
        binding.etDescription.isFocusableInTouchMode = true
        binding.etDescription.isCursorVisible = true

        binding.btnEdit.setImageResource(R.drawable.ic_check)

        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(binding.etTitle, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun disableEditMode() {
        isEditMode = false

        binding.etTitle.isFocusable = false
        binding.etTitle.isFocusableInTouchMode = false
        binding.etTitle.isCursorVisible = false

        binding.etDescription.isFocusable = false
        binding.etDescription.isFocusableInTouchMode = false
        binding.etDescription.isCursorVisible = false

        binding.btnEdit.setImageResource(R.drawable.ic_edit)

        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.etTitle.windowToken, 0)
    }

    // UI refresh helpers
    private fun refreshPriorityChip() {
        if (currentIsHighPriority) {
            binding.priorityValue.text = "High"
            binding.priorityCard.setCardBackgroundColor("#FFE4E6".toColorInt())
            binding.priorityValue.setTextColor("#E11D48".toColorInt())
        } else {
            binding.priorityValue.text = "Normal"
            binding.priorityCard.setCardBackgroundColor("#F3F4F6".toColorInt())
            binding.priorityValue.setTextColor("#4B5563".toColorInt())
        }
    }

    private fun refreshStatusChip() {
        if (currentIsCompleted) {
            binding.statusValue.text = "Done"
            binding.statusCard.setCardBackgroundColor("#DCFCE7".toColorInt())
            binding.statusValue.setTextColor("#16A34A".toColorInt())
            binding.statusCard.strokeColor = "#16A34A".toColorInt()
        } else {
            binding.statusValue.text = "To Do"
            binding.statusCard.setCardBackgroundColor("#FFFFFF".toColorInt())
            binding.statusValue.setTextColor("#374151".toColorInt())
            binding.statusCard.strokeColor = "#D1D5DB".toColorInt()
        }
    }

    private fun refreshDueDateDisplay() {
        if (selectedDueDateMillis != null) {
            val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
            binding.tvDueDate.text = sdf.format(Date(selectedDueDateMillis!!))
        } else {
            binding.tvDueDate.text = "Tap to set date"
        }
    }

    private fun refreshDueTimeDisplay() {
        if (selectedHour >= 0 && selectedMinute >= 0) {
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, selectedHour)
                set(Calendar.MINUTE, selectedMinute)
            }
            val formatted = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(cal.time)
            binding.tvDueTime.text = formatted
        } else {
            binding.tvDueTime.text = "Tap to set time"
        }
    }

    private fun handleDuplicateTask() {
        val originalName = intent.getStringExtra("task_name") ?: "New Task"

        val duplicatedTask = Task(
            id = 0,
            name = "$originalName (Copy)",
            isHighPriority = currentIsHighPriority,
            isCompleted = currentIsCompleted,
            createdAt = System.currentTimeMillis()
        )

        taskViewModel.addTask(duplicatedTask)
        android.widget.Toast.makeText(this, "Task Duplicated!", android.widget.Toast.LENGTH_SHORT).show()
        finish()
    }

    companion object {
        const val RESULT_TASK_DELETED = 100
    }
}