package com.darksunTechnologies.justdoit.fragments

import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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

class TaskListFragment : Fragment() {

    private val viewModel: TaskViewModel by activityViewModels()
    private lateinit var myAdapter: TaskAdapter

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

        myAdapter = TaskAdapter()
        recyclerView.adapter = myAdapter
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        attachSwipeActions(recyclerView)

        viewModel.tasks.observe(viewLifecycleOwner) { list ->
            myAdapter.submitList(list)
            emptyState.visibility = if (list.isNullOrEmpty()) View.VISIBLE else View.GONE
            recyclerView.visibility = if (list.isNullOrEmpty()) View.GONE else View.VISIBLE
        }
    }

    private fun attachSwipeActions(recyclerView: RecyclerView) {
        val swipeCallback = object :
            ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                val task = myAdapter.currentList[position]

                when (direction) {
                    ItemTouchHelper.LEFT -> {
                        // DELETE
                        viewModel.deleteTask(task)
                        Snackbar.make(requireView(), "Task deleted", Snackbar.LENGTH_LONG)
                            .setAction("UNDO") { viewModel.undoDelete() }
                            .show()
                    }
                    ItemTouchHelper.RIGHT -> {
                        // TOGGLE COMPLETE
                        viewModel.toggleComplete(task)
                        val msg = if (task.isCompleted) "Task reactivated" else "Task completed ✓"
                        Snackbar.make(requireView(), msg, Snackbar.LENGTH_SHORT).show()
                    }
                }
            }

            override fun onChildDraw(
                c: Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                RecyclerViewSwipeDecorator.Builder(
                    c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive
                )
                    // Swipe LEFT → Red delete
                    .addSwipeLeftBackgroundColor(Color.parseColor("#EF4444"))
                    .addSwipeLeftActionIcon(R.drawable.delete)
                    .setSwipeLeftActionIconTint(Color.WHITE)
                    .addSwipeLeftCornerRadius(1, 12f)
                    // Swipe RIGHT → Green complete
                    .addSwipeRightBackgroundColor(Color.parseColor("#22C55E"))
                    .addSwipeRightActionIcon(R.drawable.ic_check)
                    .setSwipeRightActionIconTint(Color.WHITE)
                    .addSwipeRightCornerRadius(1, 12f)
                    .create()
                    .decorate()

                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }
        }

        ItemTouchHelper(swipeCallback).attachToRecyclerView(recyclerView)
    }
}
