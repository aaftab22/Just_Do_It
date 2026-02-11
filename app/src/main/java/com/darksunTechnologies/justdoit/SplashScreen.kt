package com.darksunTechnologies.justdoit

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.darksunTechnologies.justdoit.datastore.OnboardingPreferences
import kotlinx.coroutines.launch
import com.darksunTechnologies.justdoit.datastore.dataStore
import kotlinx.coroutines.flow.first

@SuppressLint("CustomSplashScreen")
class SplashScreen : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash_screen)

        if (!isTaskRoot) {
            // If not, the activity must be finishing
            finish()
            return
        }

        lifecycleScope.launch {
            val isFirstTime = dataStore.data.first()[OnboardingPreferences.IS_FIRST_TIME] ?: true

            Handler(Looper.getMainLooper()).postDelayed({
                if (isFirstTime) {
                    startActivity(Intent(this@SplashScreen, IntroActivity::class.java))
                } else {
                    startActivity(Intent(this@SplashScreen, MainActivity::class.java))
                }
                finish()
            }, 1500)
        }

    } //on create closed
}