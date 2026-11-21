package com.arkhins.arkhime.feeds

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import com.arkhins.arkhime.R
import com.arkhins.arkhime.databinding.ActivityFeedHostBinding
import com.arkhins.arkhime.feeds.crunchyroll.CrunchyrollFragment
import com.arkhins.arkhime.feeds.nyaa.NyaaSearchFragment
import com.arkhins.arkhime.initActivity
import com.arkhins.arkhime.navBarHeight
import com.arkhins.arkhime.statusBarHeight
import com.arkhins.arkhime.themes.ThemeManager

/** Full-screen nyaa.si or Crunchyroll browser, opened from the home profile sheet or a notification. */
class FeedHostActivity : AppCompatActivity() {
    private lateinit var binding: ActivityFeedHostBinding
    private lateinit var source: FeedSource

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager(this).applyTheme()
        binding = ActivityFeedHostBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initActivity(this)
        binding.feedHostAppBar.updateLayoutParams<android.view.ViewGroup.MarginLayoutParams> { topMargin = statusBarHeight }
        binding.feedHostContainer.updatePadding(bottom = navBarHeight)

        source = runCatching { FeedSource.valueOf(intent.getStringExtra(EXTRA_SOURCE)!!) }.getOrDefault(FeedSource.NYAA)
        binding.feedHostTitle.text = source.label
        refreshTrackedIcon()
        binding.feedHostBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        binding.feedHostTracked.setOnClickListener {
            startActivity(TrackedFeedsActivity.intent(this, source))
        }
        intent.getLongExtra(EXTRA_FEED_ID, -1L).takeIf { it != -1L }?.let { FeedStore.clearNew(this, it) }

        if (savedInstanceState == null) {
            val query = intent.getStringExtra(EXTRA_QUERY).orEmpty()
            val fragment = when (source) {
                FeedSource.NYAA, FeedSource.SUKEBEI -> NyaaSearchFragment.newInstance(
                    query = query,
                    category = TrackedFeed(
                        source = source, keywords = emptyList(), category = intent.getStringExtra(EXTRA_CATEGORY)
                    ).nyaaCategory,
                    filter = intent.getIntExtra(EXTRA_FILTER, 0),
                )
                FeedSource.CRUNCHYROLL -> CrunchyrollFragment.newInstance(query)
            }
            supportFragmentManager.beginTransaction().replace(R.id.feedHostContainer, fragment).commit()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshTrackedIcon()
    }

    /** A filled bell when a tracked feed for this source has new matches */
    private fun refreshTrackedIcon() {
        binding.feedHostTracked.setImageResource(
            if (FeedStore.of(this, source).any { it.newCount > 0 }) R.drawable.ic_round_notifications_active_24
            else R.drawable.ic_round_notifications_none_24
        )
    }

    companion object {
        private const val EXTRA_SOURCE = "source"
        private const val EXTRA_QUERY = "query"
        private const val EXTRA_CATEGORY = "category"
        private const val EXTRA_FILTER = "filter"
        private const val EXTRA_FEED_ID = "feedId"

        fun intent(
            context: Context,
            source: FeedSource,
            query: String = "",
            category: String? = null,
            filter: Int = 0,
            feedId: Long = -1L,
        ) = Intent(context, FeedHostActivity::class.java)
            .putExtra(EXTRA_SOURCE, source.name)
            .putExtra(EXTRA_QUERY, query)
            .putExtra(EXTRA_CATEGORY, category)
            .putExtra(EXTRA_FILTER, filter)
            .putExtra(EXTRA_FEED_ID, feedId)
    }
}
