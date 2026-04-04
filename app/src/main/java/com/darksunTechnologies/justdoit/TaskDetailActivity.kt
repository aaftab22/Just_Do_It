package com.darksunTechnologies.justdoit

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.darksunTechnologies.justdoit.databinding.ActivityTaskDetailBinding
import com.darksunTechnologies.justdoit.viewmodel.TaskViewModel
import androidx.core.graphics.toColorInt

class TaskDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTaskDetailBinding
    private val taskViewModel: TaskViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        binding = ActivityTaskDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val name = intent.getStringExtra("task_name")
        val isHighPriority = intent.getBooleanExtra("task_priority", false)
        val description = intent.getStringExtra("task_description")
        val isCompleted = intent.getBooleanExtra("task_completed", false)

        setSupportActionBar(binding.myToolbar)

        binding.etTitle.text = name

        binding.myToolbar.setNavigationOnClickListener {
            finish()
        }

        supportActionBar?.title = ""
        
        if (isHighPriority) {
            binding.priorityValue.text = "High"
            binding.priorityCard.setCardBackgroundColor("#FFE4E6".toColorInt())
            binding.priorityValue.setTextColor("#E11D48".toColorInt())
        } else {
            binding.priorityValue.text = "Normal"
            binding.priorityCard.setCardBackgroundColor("#F3F4F6".toColorInt())
            binding.priorityValue.setTextColor("#4B5563".toColorInt())
        }

        // Description
        binding.etDescription.text = if (!description.isNullOrBlank()) description else "No description"

        // Status chip — reflects completion state
        if (isCompleted) {
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

        binding.actionComplete.setOnClickListener {
            val id = intent.getIntExtra("task_id", -1)
            if (id != -1) {
                val name = intent.getStringExtra("task_name") ?: ""
                val isHighPriority = intent.getBooleanExtra("task_priority", false)
                val isCompleted = intent.getBooleanExtra("task_completed", false)
                
                // Toggle completion status
                taskViewModel.toggleComplete(com.darksunTechnologies.justdoit.models.Task(
                    id = id, 
                    name = name, 
                    isHighPriority = isHighPriority,
                    isCompleted = isCompleted
                ))
                
                val msg = if (isCompleted) "Task Reactivated" else "Task Completed!"
                android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show()
                finish()
            }
        }

        binding.actionDuplicate.setOnClickListener {
            handleDuplicateTask()
        }

        binding.actionDelete.setOnClickListener {
            val id = intent.getIntExtra("task_id", -1)
            if (id != -1) {
                val name = intent.getStringExtra("task_name") ?: ""
                val isHighPriority = intent.getBooleanExtra("task_priority", false)
                taskViewModel.deleteTask(com.darksunTechnologies.justdoit.models.Task(id = id, name = name, isHighPriority = isHighPriority))
                android.widget.Toast.makeText(this, "Task Deleted!", android.widget.Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun handleDuplicateTask() {
        val originalName = intent.getStringExtra("task_name") ?: "New Task"
        val isHighPriority = intent.getBooleanExtra("task_priority", false)
        val isCompleted = intent.getBooleanExtra("task_completed", false)
        
        val duplicatedTask = com.darksunTechnologies.justdoit.models.Task(
            id = 0, 
            name = "$originalName (Copy)", 
            isHighPriority = isHighPriority,
            isCompleted = isCompleted,
            createdAt = System.currentTimeMillis()
        )
        
        taskViewModel.addTask(duplicatedTask)
        
        android.widget.Toast.makeText(this, "Task Duplicated!", android.widget.Toast.LENGTH_SHORT).show()
        finish()
    }
}