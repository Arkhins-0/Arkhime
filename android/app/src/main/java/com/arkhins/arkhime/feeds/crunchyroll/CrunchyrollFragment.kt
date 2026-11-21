package com.arkhins.arkhime.feeds.crunchyroll

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.arkhins.arkhime.R
import com.arkhins.arkhime.databinding.FragmentCrunchyrollBinding
import com.arkhins.arkhime.databinding.ItemFeedRowBinding
import com.arkhins.arkhime.feeds.FeedSource
import com.arkhins.arkhime.feeds.FeedStore
import com.arkhins.arkhime.feeds.TrackedFeed
import com.arkhins.arkhime.loadImage
import com.arkhins.arkhime.media.MediaDetailsActivity
import com.arkhins.arkhime.snackString
import com.google.android.material.chip.Chip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Crunchyroll releases. On its own it lists what's out now, what's coming up and a title search.
 * Inside an anime page ([newForMedia]) it lists that title's Crunchyroll episodes.
 */
class CrunchyrollFragment : Fragment() {
    private var _binding: FragmentCrunchyrollBinding? = null
    private val binding get() = _binding!!

    private enum class Mode { RECENT, UPCOMING, SEARCH, MEDIA }

    private var mode = Mode.RECENT
    private var query = ""
    private var job: Job? = null
    private val adapter = RowAdapter()

    data class Row(
        val image: String?,
        val title: String,
        val subtitle: String,
        val caption: String?,
        val url: String?,
        val mediaId: Int?,
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCrunchyrollBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val args = requireArguments()
        binding.crRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.crRecycler.adapter = adapter
        args.getInt(ARG_EXTRA_BOTTOM_PADDING, 0).takeIf { it > 0 }?.let {
            binding.crRecycler.setPadding(
                binding.crRecycler.paddingLeft, binding.crRecycler.paddingTop,
                binding.crRecycler.paddingRight, binding.crRecycler.paddingBottom + it
            )
        }

        if (args.containsKey(ARG_MEDIA_ID)) {
            mode = Mode.MEDIA
            binding.crSearchLayout.isVisible = false
            binding.crChipsScroll.isVisible = false
            binding.crSwipeRefresh.setOnRefreshListener { loadMedia(args) }
            loadMedia(args)
            return
        }

        query = args.getString(ARG_QUERY).orEmpty()
        if (query.isNotBlank()) mode = Mode.SEARCH
        binding.crSearchInput.setText(query)
        binding.crSearchInput.setOnEditorActionListener { _, id, _ ->
            if (id == EditorInfo.IME_ACTION_SEARCH) { submit(); true } else false
        }
        binding.crSearchLayout.setEndIconOnClickListener { submit() }

        listOf(Mode.RECENT to R.string.cr_recent, Mode.UPCOMING to R.string.cr_upcoming, Mode.SEARCH to R.string.cr_search)
            .forEach { (m, label) ->
                binding.crModeChips.addView(
                    (layoutInflater.inflate(R.layout.item_feed_chip, null, false) as Chip).apply {
                        id = View.generateViewId()
                        tag = m
                        text = getString(label)
                        isChecked = m == mode
                        setOnClickListener { if (isChecked) { mode = m; load() } }
                    }
                )
            }
        binding.crTrack.setOnClickListener { track() }
        binding.crSwipeRefresh.setOnRefreshListener { load() }
        load()
    }

    private fun submit() {
        query = binding.crSearchInput.text?.toString()?.trim().orEmpty()
        requireContext().getSystemService(InputMethodManager::class.java)
            ?.hideSoftInputFromWindow(binding.crSearchInput.windowToken, 0)
        mode = Mode.SEARCH
        for (i in 0 until binding.crModeChips.childCount) {
            (binding.crModeChips.getChildAt(i) as Chip).let { it.isChecked = it.tag == Mode.SEARCH }
        }
        load()
    }

    private fun track() {
        val text = binding.crSearchInput.text?.toString().orEmpty()
        val keywords = FeedStore.parseKeywords(text)
        if (keywords.isEmpty()) {
            snackString(getString(R.string.track_needs_keywords))
            binding.crSearchInput.requestFocus()
            return
        }
        FeedStore.add(requireContext(), TrackedFeed(source = FeedSource.CRUNCHYROLL, keywords = keywords))
        snackString(getString(R.string.tracking_feed, keywords.joinToString(" · ")))
    }

    private fun load() {
        job?.cancel()
        binding.crEmpty.isVisible = false
        binding.crProgress.isVisible = adapter.itemCount == 0
        job = viewLifecycleOwner.lifecycleScope.launch {
            val rows = withContext(Dispatchers.IO) {
                runCatching {
                    when (mode) {
                        Mode.RECENT -> {
                            val now = System.currentTimeMillis() / 1000
                            Crunchyroll.aired(now - 2 * 86400L, now)?.airingSchedules.orEmpty().map(::airingRow)
                        }
                        Mode.UPCOMING -> Crunchyroll.upcoming()?.airingSchedules.orEmpty().map(::airingRow)
                        Mode.SEARCH -> if (query.isBlank()) emptyList() else Crunchyroll.search(query).map(::mediaRow)
                        Mode.MEDIA -> emptyList()
                    }
                }
            }
            show(rows.getOrElse { emptyList() }, rows.exceptionOrNull()?.message)
        }
    }

    private fun loadMedia(args: Bundle) {
        val episodes = args.getStringArrayList(ARG_EP_TITLES).orEmpty()
        val urls = args.getStringArrayList(ARG_EP_URLS).orEmpty()
        val thumbs = args.getStringArrayList(ARG_EP_THUMBS).orEmpty()
        val series = args.getString(ARG_SERIES_URL)
        binding.crOpenSeries.isVisible = series != null
        binding.crOpenSeries.setOnClickListener { open(series) }
        show(
            episodes.indices.map { i ->
                Row(thumbs.getOrNull(i), episodes[i].substringAfter(" - ", episodes[i]),
                    episodes[i].substringBefore(" - ", ""), null, urls.getOrNull(i), null)
            }.reversed(),
            null,
            if (series == null) getString(R.string.cr_not_on_crunchyroll) else null,
        )
    }

    private fun show(rows: List<Row>, error: String?, emptyText: String? = null) {
        if (_binding == null) return
        binding.crProgress.isVisible = false
        binding.crSwipeRefresh.isRefreshing = false
        adapter.submit(rows)
        binding.crEmpty.text = error ?: emptyText ?: getString(R.string.cr_no_results)
        binding.crEmpty.isVisible = rows.isEmpty() && (mode != Mode.MEDIA || emptyText != null || error != null)
    }

    private fun airingRow(a: CrAiring): Row {
        val m = a.media
        val epTitle = Regex("Episode\\s+${a.episode}\\b", RegexOption.IGNORE_CASE)
        val episodeUrl = m?.crunchyrollEpisodes?.firstOrNull { epTitle.containsMatchIn(it.title.orEmpty()) }?.url
        val whenText = DateUtils.getRelativeTimeSpanString(
            a.airingAt * 1000, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS
        ).toString()
        return Row(
            image = m?.bannerImage ?: m?.coverImage?.large,
            title = m?.name.orEmpty(),
            subtitle = getString(R.string.cr_episode_in, a.episode, whenText),
            caption = listOfNotNull(m?.format, m?.episodes?.let { "$it eps" }).joinToString(" · "),
            url = episodeUrl ?: m?.crunchyrollUrl,
            mediaId = m?.id,
        )
    }

    private fun mediaRow(m: CrMedia) = Row(
        image = m.coverImage?.large,
        title = m.name,
        subtitle = m.nextAiringEpisode?.let {
            getString(R.string.cr_episode_in, it.episode,
                DateUtils.getRelativeTimeSpanString(it.airingAt * 1000, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS))
        } ?: getString(R.string.cr_episodes, m.crunchyrollEpisodes.size),
        caption = listOfNotNull(m.format, m.season?.lowercase()?.replaceFirstChar { it.uppercase() }, m.seasonYear?.toString())
            .joinToString(" · "),
        url = m.crunchyrollUrl ?: m.crunchyrollEpisodes.lastOrNull()?.url,
        mediaId = m.id,
    )

    private fun open(url: String?) {
        if (url.isNullOrBlank()) return
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.onFailure { snackString(it.message) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private inner class RowAdapter : RecyclerView.Adapter<RowAdapter.Holder>() {
        private var rows: List<Row> = emptyList()

        fun submit(list: List<Row>) {
            rows = list
            @Suppress("NotifyDataSetChanged")
            notifyDataSetChanged()
        }

        inner class Holder(val b: ItemFeedRowBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            Holder(ItemFeedRowBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount() = rows.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val r = rows[position]
            holder.b.feedRowImage.loadImage(r.image)
            holder.b.feedRowTitle.text = r.title
            holder.b.feedRowSubtitle.text = r.subtitle
            holder.b.feedRowCaption.isVisible = !r.caption.isNullOrBlank()
            holder.b.feedRowCaption.text = r.caption
            holder.b.feedRowAction.isVisible = r.url != null
            holder.b.feedRowAction.setOnClickListener { open(r.url) }
            holder.b.root.setOnClickListener {
                if (r.mediaId != null) {
                    startActivity(Intent(requireContext(), MediaDetailsActivity::class.java).putExtra("mediaId", r.mediaId))
                } else open(r.url)
            }
        }
    }

    companion object {
        private const val ARG_QUERY = "query"
        private const val ARG_MEDIA_ID = "mediaId"
        private const val ARG_SERIES_URL = "seriesUrl"
        private const val ARG_EP_TITLES = "epTitles"
        private const val ARG_EP_URLS = "epUrls"
        private const val ARG_EP_THUMBS = "epThumbs"
        private const val ARG_EXTRA_BOTTOM_PADDING = "extraBottomPadding"

        fun newInstance(query: String = "") = CrunchyrollFragment().apply {
            arguments = Bundle().apply { putString(ARG_QUERY, query) }
        }

        fun newForMedia(
            mediaId: Int,
            seriesUrl: String?,
            episodes: List<Triple<String, String?, String?>>,
            extraBottomPadding: Int = 0,
        ) = CrunchyrollFragment().apply {
            arguments = Bundle().apply {
                putInt(ARG_MEDIA_ID, mediaId)
                putString(ARG_SERIES_URL, seriesUrl)
                putStringArrayList(ARG_EP_TITLES, ArrayList(episodes.map { it.first }))
                putStringArrayList(ARG_EP_URLS, ArrayList(episodes.map { it.second.orEmpty() }))
                putStringArrayList(ARG_EP_THUMBS, ArrayList(episodes.map { it.third.orEmpty() }))
                putInt(ARG_EXTRA_BOTTOM_PADDING, extraBottomPadding)
            }
        }
    }
}
