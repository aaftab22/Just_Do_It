package com.darksunTechnologies.justdoit


import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
class AboutUsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about_us)
    }

    fun workingOnIt(view: View) {
        Snackbar.make(view, "We are working on our social media for now just use email", Snackbar.LENGTH_LONG).show()
    }
}