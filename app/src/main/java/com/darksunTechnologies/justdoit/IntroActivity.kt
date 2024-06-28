package com.darksunTechnologies.justdoit

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager.widget.ViewPager.OnPageChangeListener
import com.darksunTechnologies.justdoit.adapters.PageAdapter
import com.darksunTechnologies.justdoit.databinding.ActivityIntroBinding

class IntroActivity : AppCompatActivity() {

    private lateinit var binding:ActivityIntroBinding
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityIntroBinding.inflate(layoutInflater)
        sharedPreferences = getSharedPreferences("AppPrefs", MODE_PRIVATE)


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

    // Method to set the 'isFirstTime' flag to false so that we can stop repetition of intoActivity
    private fun setFirstTimeFlag() {
        val editor = sharedPreferences.edit()
        editor.putBoolean("isFirstTime", false)
        editor.apply()

    }
}