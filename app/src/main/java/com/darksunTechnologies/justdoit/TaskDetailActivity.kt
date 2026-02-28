package com.darksunTechnologies.justdoit

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.darksunTechnologies.justdoit.databinding.ActivityTaskDetailBinding

class TaskDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTaskDetailBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityTaskDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val name = intent.getStringExtra("task_name")
        val isHighPriority = intent.getBooleanExtra("task_priority", false)

        binding.tvTitle.text = name
        binding.tvContent.text = if (isHighPriority) "High Priority Task" else "Normal Priority Task"
    }
}