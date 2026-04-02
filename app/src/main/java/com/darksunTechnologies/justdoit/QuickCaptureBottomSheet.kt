package com.darksunTechnologies.justdoit

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.activityViewModels
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.darksunTechnologies.justdoit.databinding.BottomSheetQuickCaptureBinding
import com.darksunTechnologies.justdoit.models.Task
import com.darksunTechnologies.justdoit.viewmodel.TaskViewModel
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * Quick Capture bottom sheet specialized for Tasks.
 * Supports text input, priority switch, and offline voice capture.
 * Optimized for one-handed, rapid data entry.
 */
class QuickCaptureBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetQuickCaptureBinding? = null
    private val binding get() = _binding!!

    private val taskViewModel: TaskViewModel by activityViewModels()

    private var voiceCaptureManager: VoiceCaptureManager? = null
    private var isListening = false

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startVoiceCapture()
        } else {
            Toast.makeText(requireContext(), "Microphone permission required for voice capture", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetQuickCaptureBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Force Task mode as Notes are removed
        binding.captureTypeToggle.visibility = View.GONE

        // Enable save button only when input is non-empty
        binding.captureInput.addTextChangedListener { text ->
            binding.btnSaveCapture.isEnabled = !text.isNullOrBlank()
        }

        // Save action
        binding.btnSaveCapture.setOnClickListener {
            save()
        }

        // Voice capture button logic
        binding.btnVoiceCapture.setOnClickListener {
            if (isListening) {
                stopVoiceCapture()
            } else {
                checkPermissionAndStartVoice()
            }
        }

        setupVoiceCaptureManager()

        // Auto-focus the input field
        binding.captureInput.requestFocus()
    }

    private fun setupVoiceCaptureManager() {
        voiceCaptureManager = VoiceCaptureManager(
            context = requireContext(),
            onResult = { resultText ->
                val currentText = binding.captureInput.text?.toString() ?: ""
                val newText = if (currentText.isEmpty()) resultText else "$currentText $resultText"
                binding.captureInput.setText(newText)
                binding.captureInput.setSelection(newText.length)
                stopVoiceCapture()
            },
            onError = { errorMessage ->
                Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_SHORT).show()
                stopVoiceCapture()
            },
            onReady = {
                isListening = true
                binding.captureInput.hint = "Listening..."
                binding.btnVoiceCapture.setIconResource(android.R.drawable.presence_audio_online)
            }
        )
    }

    private fun checkPermissionAndStartVoice() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                startVoiceCapture()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    private fun startVoiceCapture() {
        if (!isListening) {
            voiceCaptureManager?.startListening()
        }
    }

    private fun stopVoiceCapture() {
        if (isListening) {
            voiceCaptureManager?.stopListening()
            isListening = false
            binding.captureInput.hint = "What needs to be done?"
            binding.btnVoiceCapture.setIconResource(android.R.drawable.ic_btn_speak_now)
        }
    }

    private fun save() {
        val input = binding.captureInput.text?.toString()?.trim() ?: return
        if (input.isEmpty()) return

        val isHighPriority = binding.capturePrioritySwitch.isChecked
        val task = Task(
            name = input,
            isHighPriority = isHighPriority,
            source = "manual",
            createdAt = System.currentTimeMillis()
        )
        taskViewModel.addTask(task)

        dismiss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        voiceCaptureManager?.destroy()
        _binding = null
    }

    companion object {
        const val TAG = "QuickCaptureBottomSheet"
    }
}
