package com.darksunTechnologies.justdoit

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.darksunTechnologies.justdoit.adapters.TaskAdapter
import com.darksunTechnologies.justdoit.databinding.ActivityMainBinding
import com.darksunTechnologies.justdoit.models.Task
import com.google.android.material.snackbar.Snackbar
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken


@SuppressLint("NotifyDataSetChanged")
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private var taskList:MutableList<Task> = mutableListOf(
        Task("Start with your first task", false),
        Task("Is it high priority task? turn of the switch on then", true),
    )

    private lateinit var myAdapter: TaskAdapter

    private val deleteItemFromList = {
        rowNumber:Int ->
        taskList.removeAt(rowNumber)
        saveTasksToSharedPreferences()
        myAdapter.notifyDataSetChanged()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.myToolbar)

        myAdapter = TaskAdapter(taskList, deleteItemFromList)
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

    private fun saveTasksToSharedPreferences() {
        val sharedPreferences = getSharedPreferences("Tasks", MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        val gson = Gson()
        val json = gson.toJson(taskList)
        editor.putString("taskList", json)
        editor.apply()
    }

    private fun loadTaskFromSharedPreferences() {
        val sharedPreferences = getSharedPreferences("Tasks", MODE_PRIVATE)
        val gson = Gson()

        val json = sharedPreferences.getString("taskList", null)
        val type = object : TypeToken<MutableList<Task>>() {}.type
        if (json != null) {
            taskList.clear()
            taskList.addAll(gson.fromJson(json, type))
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
        taskList.add(taskToAdd)

        myAdapter.notifyDataSetChanged()
        saveTasksToSharedPreferences()

        //  clear form and wait for new input
        binding.taskNameET.setText("")
        binding.highPrioritySwitch.isChecked = false
    }

    private fun deleteAll() {
        // delete all items in the list
        taskList.clear()

        saveTasksToSharedPreferences()
        myAdapter.notifyDataSetChanged()
        val snackBar = Snackbar.make(binding.root, "All items deleted!", Snackbar.LENGTH_LONG)
        snackBar.show()
    }
}