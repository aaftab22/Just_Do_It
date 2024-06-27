package com.darksunTechnologies.justdoit.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.viewpager.widget.PagerAdapter
import com.darksunTechnologies.justdoit.R

class PageAdapter(private val mContext: Context) : PagerAdapter() {
    private val mLayoutInflater: LayoutInflater = LayoutInflater.from(mContext)

    override fun getCount(): Int {
        return 3
    }

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val itemView = mLayoutInflater.inflate(getLayout(position), container, false)
        container.addView(itemView)
        return itemView
    }

    override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
        container.removeView(`object` as View)
    }

    override fun isViewFromObject(view: View, `object`: Any): Boolean {
        return view === `object`
    }

    private fun getLayout(position: Int): Int {
        return when (position) {
            0 -> R.layout.page1
            1 -> R.layout.page2
            2 -> R.layout.page3
            else -> R.layout.page1
        }
    }
}
