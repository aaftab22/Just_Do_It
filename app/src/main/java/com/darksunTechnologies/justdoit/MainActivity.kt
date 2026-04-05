package com.darksunTechnologies.justdoit

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
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
        binding.btnMenu.setOnClickListener { view ->
            showOptionsMenu(view)
        }

        // Back button sends to background instead of closing
        onBackPressedDispatcher.addCallback(this) {
            moveTaskToBack(true)
        }

        // FAB opens Quick Capture bottom sheet
        binding.fabCapture.setOnClickListener {
            val sheet = QuickCaptureBottomSheet()
            sheet.show(supportFragmentManager, QuickCaptureBottomSheet.TAG)
        }

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
                            putExtra("task_description", task.description)
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

    private fun showOptionsMenu(view: View) {
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

    private val createBackupFileLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
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
}