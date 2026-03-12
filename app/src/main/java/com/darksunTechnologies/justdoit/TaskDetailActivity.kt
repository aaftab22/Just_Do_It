package com.darksunTechnologies.justdoit

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.darksunTechnologies.justdoit.databinding.ActivityTaskDetailBinding

class TaskDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTaskDetailBinding

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

        binding.etTitle.text = name
        
        if (isHighPriority) {
            binding.priorityValue.text = "High"
            binding.priorityCard.setCardBackgroundColor(android.graphics.Color.parseColor("#FFE4E6"))
            binding.priorityValue.setTextColor(android.graphics.Color.parseColor("#E11D48"))
        } else {
            binding.priorityValue.text = "Normal"
            binding.priorityCard.setCardBackgroundColor(android.graphics.Color.parseColor("#F3F4F6"))
            binding.priorityValue.setTextColor(android.graphics.Color.parseColor("#4B5563"))
        }

        binding.etDescription.text = if (isHighPriority) "High Priority Task" else "Normal Priority Task"
    }
}