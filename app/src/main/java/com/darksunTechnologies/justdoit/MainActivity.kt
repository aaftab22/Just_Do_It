package com.darksunTechnologies.justdoit

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.darksunTechnologies.justdoit.adapters.TaskAdapter
import com.darksunTechnologies.justdoit.databinding.ActivityMainBinding
import com.darksunTechnologies.justdoit.models.Task
import com.darksunTechnologies.justdoit.viewmodel.TaskViewModel
import com.google.android.material.snackbar.Snackbar

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val viewModel: TaskViewModel by viewModels()
    private lateinit var myAdapter: TaskAdapter
    private val deleteItemFromList: (Task) -> Unit = { task ->
        viewModel.deleteTask(task)

        val snackBar = Snackbar.make(binding.root, "Task Deleted", Snackbar.LENGTH_INDEFINITE)
        snackBar.setAction("UNDO") {
            viewModel.undoDelete()
            snackBar.dismiss()
        }
        snackBar.show()

        snackBar.view.postDelayed({ snackBar.dismiss() }, 8000)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.myToolbar)

        val overFlowIcon = binding.myToolbar.overflowIcon
        overFlowIcon?.setTint(
            ContextCompat.getColor(this, android.R.color.white)
        )

        myAdapter = TaskAdapter(deleteItemFromList)
        binding.tasksRV.adapter = myAdapter
        binding.tasksRV.layoutManager = LinearLayoutManager(this)

        viewModel.migrateFromSharedPrefsIfNeeded(this)

        onBackPressedDispatcher.addCallback(this) {
            moveTaskToBack(true)
        }

        attachSwipeToDelete()

        viewModel.tasks.observe(this) { list ->
            myAdapter.submitList(list)
        }

        viewModel.backupResult.observe(this) { result ->
            when (result) {
                is TaskViewModel.BackupResult.Success -> {
                    Snackbar.make(binding.root, result.message, Snackbar.LENGTH_LONG).show()
                }
                is TaskViewModel.BackupResult.Error -> {
                    Snackbar.make(binding.root, result.message, Snackbar.LENGTH_LONG).show()
                }
            }
        }

        this.binding.tasksRV.addItemDecoration(
            DividerItemDecoration(this, LinearLayoutManager.VERTICAL)
        )

        binding.btnAdd.isEnabled = false

        binding.taskNameET.addTextChangedListener { text ->
            binding.btnAdd.isEnabled = !text.isNullOrEmpty()
        }

        //add button onClickListener
        binding.btnAdd.setOnClickListener {
            addTask()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater: MenuInflater = menuInflater
        inflater.inflate(R.menu.option_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.delete_all_tab -> {
                deleteAll()
                true
            }
            R.id.about_tab -> {
                startActivity(Intent(this, AboutUsActivity::class.java))
                true
            }
            R.id.backup_tasks -> {
                if (viewModel.tasks.value.isNullOrEmpty()) {
                    Snackbar.make(binding.root, "No tasks to backup", Snackbar.LENGTH_SHORT).show()
                    return true
                }
                createBackupFileLauncher.launch("justdoit_tasks_backup.json")
                true
            }
            R.id.restore_tasks -> {
                pickRestoreFileLauncher.launch(arrayOf("application/json"))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun addTask() {
        val nameFromUI:String = binding.taskNameET.text.toString()
        val isHighPriorityFromUI = binding.highPrioritySwitch.isChecked

        val taskToAdd = Task(0,name = nameFromUI, isHighPriority = isHighPriorityFromUI)
        viewModel.addTask(taskToAdd)

        //  clear form and wait for new input
        binding.taskNameET.setText("")
        binding.highPrioritySwitch.isChecked = false
    }

    private fun deleteAll() {
        // delete all items in the list
        AlertDialog.Builder(this)
            .setTitle("Delete all tasks?")
            .setMessage("This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.clearAll()

                Snackbar.make(binding.root, "All items deleted!", Snackbar.LENGTH_LONG)
                    .setAction("UNDO") {
                        viewModel.undoDeleteAll()
                    }
                    .show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun attachSwipeToDelete() {
        val swipeCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val task = myAdapter.currentList[position]

                viewModel.deleteTask(task)

                Snackbar.make(binding.root, "Task Deleted", Snackbar.LENGTH_LONG)
                    .setAction("UNDO") {
                        viewModel.undoDelete()
                    }
                    .show()
            }
        }
        ItemTouchHelper(swipeCallback).attachToRecyclerView(binding.tasksRV)
    }

    private val createBackupFileLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) {
                viewModel.backupToUri(this, uri)
            }
        }

    private val pickRestoreFileLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                viewModel.restoreFromUri(this, uri)
            }
        }
}