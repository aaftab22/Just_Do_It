package com.darksunTechnologies.justdoit.fragments

import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.darksunTechnologies.justdoit.R
import com.darksunTechnologies.justdoit.TaskDetailActivity
import com.darksunTechnologies.justdoit.adapters.TaskAdapter
import com.darksunTechnologies.justdoit.viewmodel.TaskViewModel
import com.google.android.material.snackbar.Snackbar
import it.xabaras.android.recyclerview.swipedecorator.RecyclerViewSwipeDecorator

import androidx.recyclerview.widget.ConcatAdapter
import com.darksunTechnologies.justdoit.adapters.SectionHeaderAdapter

class TaskListFragment : Fragment() {

    private val viewModel: TaskViewModel by activityViewModels()
    
    // Adapters
    private lateinit var overdueHeader: SectionHeaderAdapter
    private lateinit var overdueAdapter: TaskAdapter
    private lateinit var activeHeader: SectionHeaderAdapter
    private lateinit var activeAdapter: TaskAdapter
    private lateinit var completedHeader: SectionHeaderAdapter
    private lateinit var completedAdapter: TaskAdapter
    
    // Expansion State
    private var overdueExpanded = true
    private var activeExpanded = true
    private var completedExpanded = false

    // Cached Lists
    private var currentOverdueTasks: List<com.darksunTechnologies.justdoit.models.Task> = emptyList()
    private var currentActiveTasks: List<com.darksunTechnologies.justdoit.models.Task> = emptyList()
    private var currentCompletedTasks: List<com.darksunTechnologies.justdoit.models.Task> = emptyList()

    private val taskDetailLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == TaskDetailActivity.RESULT_TASK_DELETED) {
            val data = result.data
            val id = data?.getIntExtra("deleted_task_id", -1) ?: -1
            val name = data?.getStringExtra("deleted_task_name") ?: ""
            val isHighPriority = data?.getBooleanExtra("deleted_task_priority", false) ?: false

            if (id != -1) {
                val task = com.darksunTechnologies.justdoit.models.Task(
                    id = id, name = name, isHighPriority = isHighPriority
                )
                viewModel.deleteTask(task)

                Snackbar.make(requireView(), "Task deleted", Snackbar.LENGTH_LONG)
                    .setAction("UNDO") { viewModel.undoDelete() }
                    .show()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_task_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recyclerView = view.findViewById<RecyclerView>(R.id.tasksRV)
        val emptyState = view.findViewById<View>(R.id.emptyState)

        setupAdapters(recyclerView)

        viewModel.tasks.observe(viewLifecycleOwner) { allTasks ->
            val now = System.currentTimeMillis()

            // 1. Split and Sort
            currentOverdueTasks = allTasks.filter { !it.isCompleted && it.dueDate != null && it.dueDate < now }
                .sortedBy { it.dueDate }
            
            currentActiveTasks = allTasks.filter { !it.isCompleted && (it.dueDate == null || it.dueDate >= now) }
                .sortedBy { it.dueDate ?: Long.MAX_VALUE }
            
            currentCompletedTasks = allTasks.filter { it.isCompleted }

            // 2. Update Headers
            overdueHeader.updateCount(currentOverdueTasks.size)
            activeHeader.updateCount(currentActiveTasks.size)
            completedHeader.updateCount(currentCompletedTasks.size)

            // 3. Submit Lists
            overdueAdapter.submitList(if (overdueExpanded) currentOverdueTasks else emptyList())
            activeAdapter.submitList(if (activeExpanded) currentActiveTasks else emptyList())
            completedAdapter.submitList(if (completedExpanded) currentCompletedTasks else emptyList())

            // 4. Visibility
            emptyState.visibility = if (allTasks.isNullOrEmpty()) View.VISIBLE else View.GONE
            recyclerView.visibility = if (allTasks.isNullOrEmpty()) View.GONE else View.VISIBLE
        }
    }

    private fun setupAdapters(recyclerView: RecyclerView) {
        val onTaskClick: (com.darksunTechnologies.justdoit.models.Task) -> Unit = { task ->
            val intent = Intent(requireContext(), TaskDetailActivity::class.java).apply {
                putExtra("task_id", task.id)
                putExtra("task_name", task.name)
                putExtra("task_priority", task.isHighPriority)
                putExtra("task_completed", task.isCompleted)
                putExtra("task_due_date", task.dueDate ?: -1L)
                putExtra("task_has_reminder", task.hasReminder)
                putExtra("task_created_at", task.createdAt)
                putExtra("task_source", task.source)
            }
            taskDetailLauncher.launch(intent)
        }

        val onTaskDelete: (com.darksunTechnologies.justdoit.models.Task) -> Unit = { task ->
            viewModel.deleteTask(task)
            Snackbar.make(requireView(), "Task deleted", Snackbar.LENGTH_LONG)
                .setAction("UNDO") { viewModel.undoDelete() }
                .show()
        }

        val onTaskToggle: (com.darksunTechnologies.justdoit.models.Task) -> Unit = { task ->
            viewModel.toggleComplete(task)
            val msg = if (task.isCompleted) "Task reactivated" else "Task completed \u2713"
            Snackbar.make(requireView(), msg, Snackbar.LENGTH_SHORT).show()
        }

        // Initialize adapters
        overdueAdapter = TaskAdapter(onTaskClick, onTaskDelete, onTaskToggle)
        activeAdapter = TaskAdapter(onTaskClick, onTaskDelete, onTaskToggle)
        completedAdapter = TaskAdapter(onTaskClick, onTaskDelete, onTaskToggle)

        overdueHeader = SectionHeaderAdapter("Overdue", 0, overdueExpanded) { expanded ->
            overdueExpanded = expanded
            overdueAdapter.submitList(if (expanded) currentOverdueTasks else emptyList())
        }
        activeHeader = SectionHeaderAdapter("Active", 0, activeExpanded) { expanded ->
            activeExpanded = expanded
            activeAdapter.submitList(if (expanded) currentActiveTasks else emptyList())
        }
        completedHeader = SectionHeaderAdapter("Completed", 0, completedExpanded) { expanded ->
            completedExpanded = expanded
            completedAdapter.submitList(if (expanded) currentCompletedTasks else emptyList())
        }

        // Attach Swipe
        ItemTouchHelper(overdueAdapter.getSwipeCallback()).attachToRecyclerView(recyclerView)
        ItemTouchHelper(activeAdapter.getSwipeCallback()).attachToRecyclerView(recyclerView)
        ItemTouchHelper(completedAdapter.getSwipeCallback()).attachToRecyclerView(recyclerView)

        recyclerView.adapter = ConcatAdapter(
            overdueHeader, overdueAdapter,
            activeHeader, activeAdapter,
            completedHeader, completedAdapter
        )
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
    }
}
