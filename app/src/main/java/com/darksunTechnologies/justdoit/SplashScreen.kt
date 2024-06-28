package com.darksunTechnologies.justdoit

import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AppCompatActivity

@SuppressLint("CustomSplashScreen")
class SplashScreen : AppCompatActivity() {

    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash_screen)

        sharedPreferences = getSharedPreferences("AppPrefs", MODE_PRIVATE)

        if (!isTaskRoot) {
            // If not, the activity must be finishing
            finish()
            return
        }

        val isFirstTime = sharedPreferences.getBoolean("isFirstTime", true)
        Log.d("SplashScreen", "isFirstTime before: $isFirstTime")

        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed({
            if (isFirstTime) {
                startActivity(Intent(this, IntroActivity::class.java))
            } else {
                startActivity(Intent(this, MainActivity::class.java))
            }
            finish()
        }, 1500)

    } //on create closed
}