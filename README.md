<h1 align="center">Just Do It 🤖 — AI-Powered Smart Task Manager</h1>

<p align="center">
  <strong>An intelligent, privacy-first Android To-Do app powered entirely by On-Device AI (Gemini Nano).</strong>
</p>
   
<p align="center">
  <img src="https://img.shields.io/badge/Kotlin-100%%-blueviolet?style=for-the-badge&logo=kotlin" alt="Kotlin">
  <img src="https://img.shields.io/badge/Android-MVVM-green?style=for-the-badge&logo=android" alt="Android MVVM">
  <img src="https://img.shields.io/badge/AI-Gemini%20Nano-orange?style=for-the-badge" alt="Gemini Nano">
  <img src="https://img.shields.io/badge/Offline-100%25_Private-success?style=for-the-badge" alt="100% Private">
</p>

## 🚀 Overview

**Just Do It** isn't just another To-Do list. It is an experimental sandbox showcasing advanced Android engineering and on-device machine learning. 

The core feature is the **Hybrid-Batch Engine**: a sophisticated background parsing system that intercepts incoming notifications (WhatsApp, Telegram, etc.), intelligently identifies actionable tasks using **Google's Gemini Nano via ML Kit**, and adds them to a "Suggested Tasks" inbox—all entirely offline and without sending a single byte of user data to the cloud.

---

## 🧠 The Engineering Challenge: The Hybrid-Batch Engine

**The Problem:** Running large language models (LLMs) on mobile devices is computationally expensive. Android's Power Management strictly limits background usage of the Neural Processing Unit (NPU), resulting in `ErrorCode 30` (Background NPU Block) if an app tries to run inference while minimized.

**The Solution:** I designed a custom **Hybrid-Batch Architecture** to bypass OS restrictions while maximizing battery efficiency and user privacy:

1. **The Instant Regex Bouncer (Tier 1):** 
   Incoming notifications are instantly scanned by a lightweight, zero-battery Regex engine. High-confidence tasks are extracted instantly.
2. **SQLite Deferred Queuing:** 
   Low-confidence, complex messages (e.g., *"Don't forget we need to meet up for the presentation tomorrow evening around 6"*) are caught by the `SmartParseRouter` and silently saved to a local Room Database queue.
3. **Foreground Service Escalation (WorkManager):** 
   Using `androidx.work`, the app waits until the device is **charging and idle**. It then spins up an `AiBatchProcessorWorker`, promotes it to a Foreground Service to gain direct NPU access, and batch-processes the queue.
4. **Foreground App-Open Burst:** 
   If the user opens the app before the device is idle, `MainActivity` triggers a burst inference. A high-contrast UI overlay blocks interaction while Gemini Nano chews through the pending queue in real-time.
5. **Inbox Triage UI:** 
   Processed tasks don't spam the user. They quietly land in a "Suggested Tasks" UI section with an `🤖 AI Suggestion` badge, allowing the user to accept or discard them with a single swipe.

---

## 🛠️ Tech Stack & Architecture

Built with modern Android development best practices:

* **Language:** 100% Kotlin
* **Architecture:** MVVM (Model-View-ViewModel) + Repository Pattern
* **Concurrency:** Kotlin Coroutines & Flow
* **Local Storage:** Room Database (SQLite) with seamless schema migrations
* **Background Work:** WorkManager & BroadcastReceivers
* **Machine Learning:** Google ML Kit GenAI API (Gemini Nano)
* **UI:** ViewBinding, Material Components, custom Shimmer/Progress overlays
* **Permissions:** Notification Listener Service implementation

---

## 📸 Core Features

* **Smart Capture:** Automatically extracts tasks, dates, times, and priority levels from your notifications contextually.
* **100% Offline AI:** No API keys, no cloud latency, no privacy risks. Your data never leaves the device.
* **Interactive Batch Notifications:** Replaces notification spam with a single "Smart Capture" tray notification featuring custom **Snooze** and **Silence** intent actions.
* **Swipe-to-Action:** Beautifully decorated `RecyclerView` with `ItemTouchHelper` for rapid task triage.

---

## 👨‍💻 Note to Employers / Recruiters

This repository demonstrates my ability to:
1. **Solve complex, platform-specific problems:** Navigating Android's strict background execution limits and power management policies to deliver a seamless ML experience.
2. **Build scalable architectures:** Using Room, Coroutines, and WorkManager to create a robust, fault-tolerant offline queuing system.
3. **Focus on UX and Privacy:** Ensuring that cutting-edge AI features don't drain battery, compromise user data, or spam the user with notifications.

I am passionate about pushing the boundaries of what mobile devices can do natively. If you're looking for an Android Engineer who understands both the UI layer and the deep system constraints of the Android OS, let's connect!

---

*This project is built using the Gemini Nano experimental APIs and requires a compatible device (e.g., Pixel 8 Pro, Galaxy S24) with AICore enabled.*
