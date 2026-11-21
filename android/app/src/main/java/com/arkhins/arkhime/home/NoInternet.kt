package com.arkhins.arkhime.home

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.arkhins.arkhime.MainActivity
import com.arkhins.arkhime.databinding.ActivityNoInternetBinding
import com.arkhins.arkhime.initActivity
import com.arkhins.arkhime.isOnline
import com.arkhins.arkhime.snackString
import com.arkhins.arkhime.themes.ThemeManager

// Everything in Arkhime comes from AniList, so without a connection there is only a retry screen.
class NoInternet : AppCompatActivity() {
    private lateinit var binding: ActivityNoInternetBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager(this).applyTheme()
        binding = ActivityNoInternetBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initActivity(this)

        binding.noInternetRetry.setOnClickListener {
            if (isOnline(this)) {
                startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK))
                finish()
            } else {
                snackString(getString(com.arkhins.arkhime.R.string.no_internet_connection))
            }
        }
    }
}
