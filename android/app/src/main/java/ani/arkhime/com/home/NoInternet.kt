package ani.arkhime.com.home

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import ani.arkhime.com.MainActivity
import ani.arkhime.com.databinding.ActivityNoInternetBinding
import ani.arkhime.com.initActivity
import ani.arkhime.com.isOnline
import ani.arkhime.com.snackString
import ani.arkhime.com.themes.ThemeManager

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
                snackString(getString(ani.arkhime.com.R.string.no_internet_connection))
            }
        }
    }
}
