package com.darksunTechnologies.justdoit

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.lifecycleScope
import androidx.viewpager.widget.ViewPager.OnPageChangeListener
import com.darksunTechnologies.justdoit.adapters.PageAdapter
import com.darksunTechnologies.justdoit.databinding.ActivityIntroBinding
import com.darksunTechnologies.justdoit.datastore.OnboardingPreferences
import com.darksunTechnologies.justdoit.datastore.dataStore
import kotlinx.coroutines.launch

class IntroActivity : AppCompatActivity() {

    private lateinit var binding:ActivityIntroBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityIntroBinding.inflate(layoutInflater)

        setContentView(binding.root)

        val viewPager = binding.productImagesViewpager
        val tabLayout = binding.viewPagerIndicator

        val adapter = PageAdapter(this)
        viewPager.adapter = adapter
        tabLayout.setupWithViewPager(viewPager)

        viewPager.addOnPageChangeListener(object : OnPageChangeListener {
            override fun onPageScrolled(
                position: Int,
                positionOffset: Float,
                positionOffsetPixels: Int
            ) {
            }

            override fun onPageSelected(position: Int) {
                if (position == adapter.count - 1) {
                    // Last page reached, hide next button, show "Got It" button
                    binding.nextBtn.visibility = View.GONE
                    binding.skipBtn.visibility = View.GONE
                    binding.gotItBtn.visibility = View.VISIBLE
                } else {
                    // Not the last page, show next button, hide "Got It" button
                    binding.nextBtn.visibility = View.VISIBLE
                    binding.skipBtn.visibility = View.VISIBLE
                    binding.gotItBtn.visibility = View.GONE
                }
            }

            override fun onPageScrollStateChanged(state: Int) {}
        })

        binding.nextBtn.setOnClickListener {
            val nextPage = viewPager.currentItem + 1
            if (nextPage < adapter.count) {
                viewPager.currentItem = nextPage
            }
        }

        // Handle "Got It" button click
        binding.gotItBtn.setOnClickListener {
            setFirstTimeFlag()
            startActivity(Intent(this@IntroActivity, MainActivity::class.java))
            finish()
        }

        //Handle "Skip" button click
        binding.skipBtn.setOnClickListener {
            setFirstTimeFlag()
            val intent = Intent(this@IntroActivity, MainActivity::class.java)
            startActivity(intent)
        }
    }

    private fun setFirstTimeFlag() {
        lifecycleScope.launch {
            dataStore.edit { prefs ->
                prefs[OnboardingPreferences.IS_FIRST_TIME] = false
            }
        }
    }
}