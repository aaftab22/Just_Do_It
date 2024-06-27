package com.darksunTechnologies.justdoit

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity

class SplashScreen : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash_screen)

        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed({

            startActivity(Intent(applicationContext, IntroActivity::class.java))

        }, 2000)

        Handler().postDelayed({

        }, 2000)
    }
}