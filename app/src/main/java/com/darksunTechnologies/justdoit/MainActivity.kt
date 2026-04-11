package com.darksunTechnologies.justdoit

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.addTextChangedListener
import com.darksunTechnologies.justdoit.databinding.ActivityMainBinding
import com.darksunTechnologies.justdoit.viewmodel.TaskViewModel
import com.google.android.material.snackbar.Snackbar

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val taskViewModel: TaskViewModel by viewModels()
    private lateinit var shakeDetector: ShakeDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.appBarLayout) { view, insets ->
            val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            view.setPadding(0, statusBar.top, 0, 0)
            insets
        }

        taskViewModel.migrateFromSharedPrefsIfNeeded(this)

        // Integrated options menu inside the search bar
        binding.btnMenu.setOnClickListener {
            showOptionsMenu()
        }

        // Back button sends to background instead of closing
        onBackPressedDispatcher.addCallback(this) {
            moveTaskToBack(true)
        }

        // FAB opens Quick Capture bottom sheet
        binding.fabCapture
            .setOnClickListener {
                val sheet = QuickCaptureBottomSheet()
                sheet.show(supportFragmentManager, QuickCaptureBottomSheet.TAG)
            }

        if (Build.VERSION.SDK_INT >= 33) {
            notificationPermissionsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(AlarmManager::class.java)
            if (!alarmManager.canScheduleExactAlarms()) {
                AlertDialog.Builder(this)
                    .setTitle("Allow Alarms & Reminders")
                    .setMessage("Required to send task reminders on time.")
                    .setPositiveButton("Open Settings") { _, _ ->
                        startActivity(Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                    }
                    .setNegativeButton("Skip", null)
                    .show()
            }
        }

        createNotificationChannel(this)

        // Observer: show "Task saved! EDIT" Snackbar after Quick Capture
        taskViewModel.lastSavedTaskId.observe(this) { taskId ->
            if (taskId == null) return@observe

            Snackbar.make(binding.root, "Task saved!", Snackbar.LENGTH_LONG)
                .setAction("EDIT") {
                    val task = taskViewModel.tasks.value?.find { it.id == taskId.toInt() }
                    if (task != null) {
                        val intent = Intent(this, TaskDetailActivity::class.java).apply {
                            putExtra("task_id", task.id)
                            putExtra("task_name", task.name)
                            putExtra("task_priority", task.isHighPriority)
                            putExtra("task_completed", task.isCompleted)
                            putExtra("task_due_date", task.dueDate ?: -1L)
                            putExtra("task_has_reminder", task.hasReminder)
                            putExtra("task_created_at", task.createdAt)
                            putExtra("task_source", task.source)
                            putExtra("start_in_edit_mode", true)
                        }
                        startActivity(intent)
                    }
                }
                .show()

            taskViewModel.clearLastSavedTaskId() // consume the event
        }

        // Observer: show UNDO Snackbar when task is deleted from anywhere (including Detail screen)
        taskViewModel.showUndoDelete.observe(this) { triggered ->
            if (triggered != true) return@observe

            Snackbar.make(binding.root, "Task deleted", Snackbar.LENGTH_LONG)
                .setAction("UNDO") { taskViewModel.undoDelete() }
                .show()

            taskViewModel.clearUndoDelete() // consume the event
        }

        // Observe backup results
        taskViewModel.backupResult.observe(this) { result ->
            when (result) {
                is TaskViewModel.BackupResult.Success -> {
                    Snackbar.make(binding.root, result.message, Snackbar.LENGTH_LONG).show()
                }
                is TaskViewModel.BackupResult.Error -> {
                    Snackbar.make(binding.root, result.message, Snackbar.LENGTH_LONG).show()
                }
            }
        }

        // Search functionality
        binding.searchEditText.addTextChangedListener { text ->
            val query = text?.toString()?.trim() ?: ""
            taskViewModel.searchTasks(query)
        }

        // Initialize shake to create
        shakeDetector = ShakeDetector(this) {
            val sheet = QuickCaptureBottomSheet()
            if (supportFragmentManager.findFragmentByTag(QuickCaptureBottomSheet.TAG) == null) {
                sheet.show(supportFragmentManager, QuickCaptureBottomSheet.TAG)
            }
        }
    }

    private fun showOptionsMenu() {
        val sheet = SettingsBottomSheet { action ->
            when (action) {
                SettingsBottomSheet.Action.DELETE_ALL -> deleteAll()
                SettingsBottomSheet.Action.ABOUT -> startActivity(Intent(this, AboutUsActivity::class.java))
                SettingsBottomSheet.Action.BACKUP -> {
                    if (taskViewModel.tasks.value.isNullOrEmpty()) {
                        Snackbar.make(binding.root, "No tasks to backup", Snackbar.LENGTH_SHORT).show()
                    } else {
                        createBackupFileLauncher.launch("justdoit_tasks_backup.json")
                    }
                }
                SettingsBottomSheet.Action.RESTORE -> pickRestoreFileLauncher.launch(arrayOf("application/json"))
            }
        }
        sheet.show(supportFragmentManager, SettingsBottomSheet.TAG)
    }

    override fun onResume() {
        super.onResume()
        shakeDetector.start()
    }

    override fun onPause() {
        super.onPause()
        shakeDetector.stop()
    }

    private fun deleteAll() {
        AlertDialog.Builder(this)
            .setTitle("Delete all tasks?")
            .setMessage("This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                taskViewModel.clearAll()
                Snackbar.make(binding.root, "All tasks deleted!", Snackbar.LENGTH_LONG)
                    .setAction("UNDO") { taskViewModel.undoDeleteAll() }
                    .show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private val createBackupFileLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) {
                taskViewModel.backupToUri(this, uri)
            }
        }

    private val pickRestoreFileLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                taskViewModel.restoreFromUri(this, uri)
            }
        }

    fun createNotificationChannel(context: Context) {
            val channel = NotificationChannel(
                "task_reminders",
                "Task Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Channels for task scheduled reminders"
                enableLights(true)
                enableVibration(true)
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
    }

    private val notificationPermissionsLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (!isGranted)
            {
                AlertDialog.Builder(this)
                    .setTitle("Enable Notification")
                    .setMessage("Allow notifications to get task reminders on time.")
                    .setPositiveButton("Open Settings") { _, _ ->
                        startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, packageName)
                        })
                    }
                    .setNegativeButton("Skip Anyway", null)
                    .show()
            }
        }
}