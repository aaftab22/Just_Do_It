package com.darksunTechnologies.justdoit

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.darksunTechnologies.justdoit.adapters.TaskAdapter
import com.darksunTechnologies.justdoit.databinding.ActivityMainBinding
import com.darksunTechnologies.justdoit.models.Task
import com.darksunTechnologies.justdoit.viewmodel.TaskViewModel
import com.google.android.material.snackbar.Snackbar
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import androidx.core.content.edit

@SuppressLint("NotifyDataSetChanged")
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: TaskViewModel by viewModels()
    private lateinit var myAdapter: TaskAdapter

    private val deleteItemFromList = {
        rowNumber:Int ->
        viewModel.removeTaskAt(rowNumber)
        saveTasksToSharedPreferences()
        myAdapter.notifyDataSetChanged()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.myToolbar)

        val overFlowicon = binding.myToolbar.overflowIcon
        overFlowicon?.setTint(
            ContextCompat.getColor(this, android.R.color.white)
        )

        myAdapter = TaskAdapter(viewModel.taskList, deleteItemFromList)
        binding.tasksRV.adapter = myAdapter
        binding.tasksRV.layoutManager = LinearLayoutManager(this)
        this.binding.tasksRV.addItemDecoration(
            DividerItemDecoration(
                this,
                LinearLayoutManager.VERTICAL
            )
        )

        loadTaskFromSharedPreferences()
        //add button onClickListener
        binding.btnAdd.setOnClickListener {
            addTask()
        }
    }

    @Deprecated("Deprecated in Java", ReplaceWith("moveTaskToBack(true)"))
    @SuppressLint("MissingSuperCall")
    override fun onBackPressed() {
        moveTaskToBack(true)
    }

    private fun saveTasksToSharedPreferences() {
        val sharedPreferences = getSharedPreferences("Tasks", MODE_PRIVATE)
        sharedPreferences.edit {
            val gson = Gson()
            val json = gson.toJson(viewModel.taskList)
            putString("taskList", json)
        }
    }

    private fun loadTaskFromSharedPreferences() {
        val sharedPreferences = getSharedPreferences("Tasks", MODE_PRIVATE)
        val gson = Gson()

        val json = sharedPreferences.getString("taskList", null)
        val type = object : TypeToken<MutableList<Task>>() {}.type
        if (json != null) {
            viewModel.taskList.clear()
            viewModel.taskList.addAll(gson.fromJson(json, type))
        }
        myAdapter.notifyDataSetChanged()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater: MenuInflater = menuInflater
        inflater.inflate(R.menu.option_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle item selection.
        return when (item.itemId) {
            R.id.delete_all_tab -> {
                // do something
                deleteAll()
                true
            }
            R.id.about_tab -> {
                startActivity(Intent(this, AboutUsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun addTask() {
        val nameFromUI:String = binding.taskNameET.text.toString()
//        val isHighPriorityFromUI:Boolean = binding.highPrioritySwitch.isChecked
        val isHighPriorityFromUI = binding.highPrioritySwitch.isChecked

        if (nameFromUI.isBlank()) {
            Snackbar.make(binding.root, "Please enter a task", Snackbar.LENGTH_LONG).show()
            return
        }

        val taskToAdd = Task(nameFromUI, isHighPriorityFromUI)
        viewModel.addTask(taskToAdd)

        myAdapter.notifyDataSetChanged()
        saveTasksToSharedPreferences()

        //  clear form and wait for new input
        binding.taskNameET.setText("")
        binding.highPrioritySwitch.isChecked = false
    }

    private fun deleteAll() {
        // delete all items in the list
        viewModel.clearAll()

        saveTasksToSharedPreferences()
        myAdapter.notifyDataSetChanged()
        val snackBar = Snackbar.make(binding.root, "All items deleted!", Snackbar.LENGTH_LONG)
        snackBar.show()
    }
}