package com.darksunTechnologies.justdoit

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.darksunTechnologies.justdoit.databinding.BottomSheetSettingsBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * SettingsBottomSheet providing a premium, icon-driven interface for global actions.
 */
class SettingsBottomSheet(
    private val onAction: (Action) -> Unit
) : BottomSheetDialogFragment() {

    private var _binding: BottomSheetSettingsBinding? = null
    private val binding get() = _binding!!

    enum class Action {
        DELETE_ALL, BACKUP, RESTORE, ABOUT
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnDeleteAll.setOnClickListener {
            onAction(Action.DELETE_ALL)
            dismiss()
        }

        binding.btnBackup.setOnClickListener {
            onAction(Action.BACKUP)
            dismiss()
        }

        binding.btnRestore.setOnClickListener {
            onAction(Action.RESTORE)
            dismiss()
        }

        binding.btnAbout.setOnClickListener {
            onAction(Action.ABOUT)
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "SettingsBottomSheet"
    }
}
