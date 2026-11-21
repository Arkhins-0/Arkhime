package com.arkhins.arkhime.profile

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.arkhins.arkhime.databinding.ActivitySingleStatBinding
import com.arkhins.arkhime.getThemeColor
import com.arkhins.arkhime.initActivity
import com.arkhins.arkhime.themes.ThemeManager
import com.arkhins.arkhime.toast
import com.github.aachartmodel.aainfographics.aachartcreator.AAOptions

class SingleStatActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySingleStatBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager(this).applyTheme()
        initActivity(this)
        binding = ActivitySingleStatBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val chartOptions = chartOptions
        if (chartOptions != null) {
            chartOptions.chart?.backgroundColor = getThemeColor(android.R.attr.windowBackground)
            binding.chartView.aa_drawChartWithChartOptions(chartOptions)
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            toast("No chart data")
            finish()
        }
    }

    companion object {
        var chartOptions: AAOptions? = null  // I cba to pass this through an intent
    }
}