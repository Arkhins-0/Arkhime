package com.arkhins.arkhime.feeds

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.arkhins.arkhime.R
import com.arkhins.arkhime.databinding.ActivityFeedHostBinding
import com.arkhins.arkhime.databinding.ItemTrackedFeedBinding
import com.arkhins.arkhime.px
import com.arkhins.arkhime.feeds.nyaa.NyaaCategory
import com.arkhins.arkhime.feeds.nyaa.NyaaFilter
import com.arkhins.arkhime.initActivity
import com.arkhins.arkhime.navBarHeight
import com.arkhins.arkhime.snackString
import com.arkhins.arkhime.statusBarHeight
import com.arkhins.arkhime.themes.ThemeManager
import com.arkhins.arkhime.util.customAlertDialog
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/** The keyword feeds tracked for one source: add, mute, remove, or open one to see its matches. */
class TrackedFeedsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityFeedHostBinding
    private lateinit var source: FeedSource
    private val adapter = FeedAdapter()
    private lateinit var empty: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager(this).applyTheme()
        binding = ActivityFeedHostBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initActivity(this)
        binding.feedHostAppBar.updateLayoutParams<ViewGroup.MarginLayoutParams> { topMargin = statusBarHeight }

        source = runCatching { FeedSource.valueOf(intent.getStringExtra("source")!!) }.getOrDefault(FeedSource.NYAA)
        binding.feedHostTitle.text = "${getString(R.string.tracked_feeds_title)} · ${source.label}"
        binding.feedHostBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        binding.feedHostTracked.setImageResource(R.drawable.ic_round_add_circle_outline_24)
        binding.feedHostTracked.contentDescription = getString(R.string.add_feed)
        binding.feedHostTracked.setOnClickListener { addDialog() }
        binding.feedHostSecondary.isVisible = true
        binding.feedHostSecondary.setImageResource(R.drawable.ic_round_refresh_24)
        binding.feedHostSecondary.contentDescription = getString(R.string.feed_check_now)
        binding.feedHostSecondary.setOnClickListener {
            FeedCheckWorker.runNow(this)
            snackString(getString(R.string.feed_checking))
        }

        val recycler = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@TrackedFeedsActivity)
            adapter = this@TrackedFeedsActivity.adapter
            clipToPadding = false
            updatePadding(left = 8.px, top = 4.px, right = 8.px, bottom = navBarHeight + 96.px)
        }
        empty = TextView(this).apply {
            setText(R.string.feed_empty)
            gravity = android.view.Gravity.CENTER
            setPadding(32.px, 32.px, 32.px, 32.px)
            typeface = androidx.core.content.res.ResourcesCompat.getFont(context, R.font.jakarta_semi_bold)
        }
        binding.feedHostContainer.addView(FrameLayout(this).apply {
            addView(recycler)
            addView(empty, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        })
        FeedCheckWorker.ensureChannel(this)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val feeds = FeedStore.of(this, source)
        adapter.submit(feeds)
        empty.isVisible = feeds.isEmpty()
    }

    private fun addDialog() {
        val layout = TextInputLayout(this).apply {
            hint = getString(R.string.feed_keywords_hint)
            helperText = getString(R.string.feed_keywords_help)
        }
        val input = TextInputEditText(layout.context)
        layout.addView(input)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.px, 8.px, 24.px, 0)
            layout.boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            layout.setBoxCornerRadii(16f.px.toFloat(), 16f.px.toFloat(), 16f.px.toFloat(), 16f.px.toFloat())
            addView(layout)
        }
        val site = source.nyaaSite
        var category = NyaaCategory.defaultFor(site ?: com.arkhins.arkhime.feeds.nyaa.NyaaSite.NYAA)
        var filter = NyaaFilter.NONE
        if (site != null) {
            fun group(labels: List<String>, selected: Int, onPick: (Int) -> Unit) = ChipGroup(this).apply {
                isSingleSelection = true
                isSelectionRequired = true
                setPadding(0, 8.px, 0, 0)
                labels.forEachIndexed { i, label ->
                    addView((layoutInflater.inflate(R.layout.item_feed_chip, this, false) as Chip).apply {
                        id = android.view.View.generateViewId()
                        text = label
                        isChecked = i == selected
                        setOnClickListener { if (isChecked) onPick(i) }
                    })
                }
            }
            val categories = NyaaCategory.of(site)
            container.addView(group(categories.map { it.label }, categories.indexOf(category)) { category = categories[it] })
            container.addView(group(NyaaFilter.entries.map { it.label }, 0) { filter = NyaaFilter.entries[it] })
        }
        customAlertDialog().apply {
            setTitle(source.label)
            setCustomView(container)
            setPosButton(R.string.add_feed) {
                val keywords = FeedStore.parseKeywords(
                    input.text?.toString().orEmpty().let { if (it.contains(',')) it else it.replace(' ', ',') }
                )
                if (keywords.isEmpty()) {
                    snackString(getString(R.string.track_needs_keywords))
                } else {
                    FeedStore.add(
                        this@TrackedFeedsActivity,
                        TrackedFeed(
                            source = source,
                            keywords = keywords,
                            category = if (site != null) category.key else null,
                            filter = filter.code,
                        )
                    )
                    refresh()
                }
            }
            setNegButton(R.string.cancel)
            show()
        }
    }

    private inner class FeedAdapter : RecyclerView.Adapter<FeedAdapter.Holder>() {
        private var feeds: List<TrackedFeed> = emptyList()

        fun submit(list: List<TrackedFeed>) {
            feeds = list
            @Suppress("NotifyDataSetChanged")
            notifyDataSetChanged()
        }

        inner class Holder(val b: ItemTrackedFeedBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            Holder(ItemTrackedFeedBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount() = feeds.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val feed = feeds[position]
            val ctx = this@TrackedFeedsActivity
            holder.b.trackedLabel.text = feed.label
            holder.b.trackedCaption.text = when (feed.source) {
                FeedSource.NYAA, FeedSource.SUKEBEI -> listOf(feed.nyaaCategory.label, NyaaFilter.from(feed.filter).label)
                    .joinToString(" · ")
                FeedSource.CRUNCHYROLL -> getString(R.string.crunchyroll)
            }
            holder.b.trackedNew.isVisible = feed.newCount > 0
            holder.b.trackedNew.text = getString(R.string.feed_new_count, feed.newCount)
            holder.b.trackedNotify.setOnCheckedChangeListener(null)
            holder.b.trackedNotify.isChecked = feed.notify
            holder.b.trackedNotify.setOnCheckedChangeListener { _, checked ->
                FeedStore.update(ctx, feed.copy(notify = checked, lastSeen = System.currentTimeMillis()))
                refresh()
            }
            holder.b.trackedDelete.setOnClickListener {
                ctx.customAlertDialog().apply {
                    setTitle(getString(R.string.feed_remove_confirm, feed.label))
                    setPosButton(R.string.yes) { FeedStore.remove(ctx, feed.id); refresh() }
                    setNegButton(R.string.no)
                    show()
                }
            }
            holder.b.root.setOnClickListener {
                FeedStore.clearNew(ctx, feed.id)
                startActivity(FeedHostActivity.intent(ctx, feed.source, feed.query, feed.category, feed.filter))
            }
        }
    }

    companion object {
        fun intent(context: Context, source: FeedSource) =
            Intent(context, TrackedFeedsActivity::class.java).putExtra("source", source.name)
    }
}
