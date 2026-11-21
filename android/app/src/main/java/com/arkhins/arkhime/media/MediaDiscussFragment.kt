package com.arkhins.arkhime.media

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.arkhins.arkhime.R
import com.arkhins.arkhime.connections.anilist.Anilist
import com.arkhins.arkhime.forum.ForumThreadItem
import com.arkhins.arkhime.navBarHeight
import com.arkhins.arkhime.px
import com.arkhins.arkhime.util.ActivityMarkdownCreator
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.xwray.groupie.GroupieAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * AniList forum threads attached to this anime or manga, the same ones AniList shows under
 * "Forum" on a media page. "Episode discussions" narrows it to the per-episode (or per-chapter) threads.
 */
class MediaDiscussFragment : Fragment() {
    private val model: MediaDetailsViewModel by activityViewModels()
    private val adapter = GroupieAdapter()
    private var mediaId = 0
    private var episodesOnly = true
    private var page = 1
    private var hasNext = true
    private var job: Job? = null

    private lateinit var refresh: SwipeRefreshLayout
    private lateinit var progress: ProgressBar
    private lateinit var empty: TextView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val ctx = requireContext()
        val chips = ChipGroup(ctx).apply {
            isSingleSelection = true
            isSelectionRequired = true
            listOf(true to R.string.discuss_episodes_only, false to R.string.discuss_all_threads).forEach { (eps, label) ->
                addView((inflater.inflate(R.layout.item_feed_chip, this, false) as Chip).apply {
                    id = View.generateViewId()
                    setText(label)
                    isChecked = eps == episodesOnly
                    setOnClickListener { if (isChecked) { episodesOnly = eps; load(true) } }
                })
            }
            if (Anilist.token != null) {
                addView(Chip(ctx).apply {
                    setText(R.string.create_thread)
                    setChipIconResource(R.drawable.ic_round_edit_note_24)
                    setOnClickListener {
                        startActivity(Intent(ctx, ActivityMarkdownCreator::class.java).apply {
                            putExtra("type", "thread")
                            putIntegerArrayListExtra("categories", arrayListOf(if (model.getMedia().value?.anime != null) 1 else 2))
                            putIntegerArrayListExtra("mediaCategories", arrayListOf(mediaId))
                        })
                    }
                })
            }
        }
        val recycler = RecyclerView(ctx).apply {
            layoutManager = LinearLayoutManager(ctx)
            adapter = this@MediaDiscussFragment.adapter
            clipToPadding = false
            setPadding(8.px, 4.px, 8.px, navBarHeight + 80.px)
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                    val lm = rv.layoutManager as LinearLayoutManager
                    if (dy > 0 && hasNext && job?.isActive != true &&
                        lm.findLastVisibleItemPosition() >= adapter!!.itemCount - 4
                    ) load(false)
                }
            })
        }
        progress = ProgressBar(ctx)
        empty = TextView(ctx).apply {
            setText(R.string.discuss_empty)
            gravity = Gravity.CENTER
            typeface = androidx.core.content.res.ResourcesCompat.getFont(ctx, R.font.jakarta_semi_bold)
            setPadding(32.px, 32.px, 32.px, 32.px)
            isVisible = false
        }
        refresh = SwipeRefreshLayout(ctx).apply {
            addView(FrameLayout(ctx).apply {
                addView(recycler)
                addView(progress, FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
                addView(empty, FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
            })
            setOnRefreshListener { load(true) }
        }
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(android.widget.HorizontalScrollView(ctx).apply {
                isHorizontalScrollBarEnabled = false
                setPadding(12.px, 4.px, 12.px, 0)
                clipToPadding = false
                addView(chips)
            })
            addView(refresh, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        model.getMedia().observe(viewLifecycleOwner) { media ->
            if (media == null || media.id == mediaId || media.id == 0) return@observe
            mediaId = media.id
            load(true)
        }
    }

    private fun load(reset: Boolean) {
        if (mediaId == 0) return
        if (reset) {
            job?.cancel()
            page = 1
            hasNext = true
            adapter.clear()
            progress.isVisible = true
            empty.isVisible = false
        }
        if (!hasNext) return
        val requestPage = page
        job = viewLifecycleOwner.lifecycleScope.launch {
            val res = withContext(Dispatchers.IO) {
                Anilist.query.getThreads(
                    mediaCategoryId = mediaId,
                    categoryId = if (episodesOnly) RELEASE_DISCUSSION else null,
                    sort = if (episodesOnly) "CREATED_AT_DESC" else "IS_STICKY, REPLIED_AT_DESC",
                    page = requestPage,
                )
            }
            if (view == null) return@launch
            progress.isVisible = false
            refresh.isRefreshing = false
            val threads = res?.data?.page?.threads.orEmpty()
            hasNext = res?.data?.page?.pageInfo?.hasNextPage == true
            page = requestPage + 1
            adapter.addAll(threads.map { ForumThreadItem(it, requireContext()) })
            empty.isVisible = adapter.itemCount == 0 && !hasNext
        }
    }

    companion object {
        /** AniList's "Release Discussion" category, where the per-episode and per-chapter threads live. */
        private const val RELEASE_DISCUSSION = 5
    }
}
