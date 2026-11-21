package com.arkhins.arkhime.feeds.nyaa

import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.arkhins.arkhime.R
import com.arkhins.arkhime.databinding.FragmentNyaaSearchBinding
import com.arkhins.arkhime.databinding.ItemNyaaTorrentBinding
import com.arkhins.arkhime.feeds.FeedSource
import com.arkhins.arkhime.feeds.FeedStore
import com.arkhins.arkhime.feeds.TrackedFeed
import com.arkhins.arkhime.snackString
import com.google.android.material.chip.Chip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * nyaa.si search: used on its own (profile sheet → nyaa.si) and inside an anime or manga page,
 * where it starts out searching for that title.
 */
class NyaaSearchFragment : Fragment() {
    private var _binding: FragmentNyaaSearchBinding? = null
    private val binding get() = _binding!!

    private var category = NyaaCategory.ANIME
    private var filter = NyaaFilter.NONE
    private var sort = NyaaSort.NEWEST
    private var query = ""
    private var page = 1
    private var hasMore = true
    private var loading: Job? = null
    private val adapter = TorrentAdapter()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentNyaaSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val args = requireArguments()
        query = savedInstanceState?.getString("query") ?: args.getString(ARG_QUERY).orEmpty()
        category = NyaaCategory.from(savedInstanceState?.getString("category") ?: args.getString(ARG_CATEGORY))
        filter = NyaaFilter.from(savedInstanceState?.getInt("filter") ?: args.getInt(ARG_FILTER, 0))
        args.getInt(ARG_EXTRA_BOTTOM_PADDING, 0).takeIf { it > 0 }?.let {
            binding.nyaaRecycler.setPadding(
                binding.nyaaRecycler.paddingLeft, binding.nyaaRecycler.paddingTop,
                binding.nyaaRecycler.paddingRight, binding.nyaaRecycler.paddingBottom + it
            )
        }

        binding.nyaaSearchInput.setText(query)
        binding.nyaaSearchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) { submit(); true } else false
        }
        binding.nyaaSearchLayout.setEndIconOnClickListener { submit() }

        val categories = if (category.site == NyaaSite.SUKEBEI) {
            NyaaCategory.of(NyaaSite.SUKEBEI)
        } else if (args.getBoolean(ARG_MANGA_ONLY)) {
            listOf(NyaaCategory.MANGA, NyaaCategory.MANGA_ENGLISH, NyaaCategory.MANGA_RAW, NyaaCategory.ALL)
        } else if (args.getBoolean(ARG_ANIME_ONLY)) {
            listOf(NyaaCategory.ANIME, NyaaCategory.ANIME_ENGLISH, NyaaCategory.ANIME_RAW, NyaaCategory.ALL)
        } else NyaaCategory.of(category.site)
        categories.forEach { c ->
            binding.nyaaCategoryChips.addView(chip(c.label, c == category) { category = c; search(true) })
        }
        // Filter and sort are pop-up chips, like the sort button on the list screen
        val filterChip = (layoutInflater.inflate(R.layout.item_feed_chip, binding.nyaaFilterChips, false) as Chip).apply {
            id = View.generateViewId()
            isCheckable = false
            setChipIconResource(R.drawable.ic_round_filter_list_24)
            chipIconTint = binding.nyaaSort.chipIconTint
        }
        binding.nyaaFilterChips.addView(filterChip, 0)
        filterChip.text = filter.label
        filterChip.setOnClickListener { v ->
            popup(v, NyaaFilter.entries.map { it.label }) {
                filter = NyaaFilter.entries[it]
                filterChip.text = filter.label
                search(true)
            }
        }
        binding.nyaaSort.text = sort.label
        binding.nyaaSort.setOnClickListener { v ->
            popup(v, NyaaSort.entries.map { it.label }) {
                sort = NyaaSort.entries[it]
                binding.nyaaSort.text = sort.label
                search(true)
            }
        }

        binding.nyaaTrack.setOnClickListener { trackCurrent() }

        binding.nyaaRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.nyaaRecycler.adapter = adapter
        binding.nyaaRecycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                val lm = rv.layoutManager as LinearLayoutManager
                if (dy > 0 && hasMore && loading?.isActive != true &&
                    lm.findLastVisibleItemPosition() >= adapter.itemCount - 5
                ) search(false)
            }
        })
        binding.nyaaSwipeRefresh.setOnRefreshListener { search(true) }

        search(true)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("query", query)
        outState.putString("category", category.key)
        outState.putInt("filter", filter.code)
    }

    private fun popup(anchor: View, labels: List<String>, onPick: (Int) -> Unit) {
        PopupMenu(requireContext(), anchor).apply {
            labels.forEachIndexed { i, label -> menu.add(0, i, i, label) }
            setOnMenuItemClickListener { onPick(it.itemId); true }
            show()
        }
    }

    private fun chip(label: String, checked: Boolean, onPick: () -> Unit): Chip =
        (layoutInflater.inflate(R.layout.item_feed_chip, null, false) as Chip).apply {
            id = View.generateViewId()
            text = label
            isChecked = checked
            setOnClickListener { if (isChecked) onPick() }
        }

    private fun submit() {
        query = binding.nyaaSearchInput.text?.toString()?.trim().orEmpty()
        (requireContext().getSystemService(InputMethodManager::class.java))
            ?.hideSoftInputFromWindow(binding.nyaaSearchInput.windowToken, 0)
        search(true)
    }

    private fun trackCurrent() {
        val keywords = FeedStore.parseKeywords(query.replace(' ', ','))
        if (keywords.isEmpty()) {
            snackString(getString(R.string.track_needs_keywords))
            return
        }
        FeedStore.add(
            requireContext(),
            TrackedFeed(source = FeedSource.of(category.site), keywords = keywords, category = category.key, filter = filter.code)
        )
        snackString(getString(R.string.tracking_feed, keywords.joinToString(" · ")))
    }

    private fun search(reset: Boolean) {
        if (reset) {
            loading?.cancel()
            page = 1
            hasMore = true
        }
        if (!hasMore) return
        val requestPage = page
        if (reset && adapter.itemCount == 0) binding.nyaaProgress.isVisible = true
        binding.nyaaEmpty.isVisible = false
        loading = viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { Nyaa.search(query, category, filter, sort, requestPage) }
            }
            if (_binding == null) return@launch
            binding.nyaaProgress.isVisible = false
            binding.nyaaSwipeRefresh.isRefreshing = false
            result.onSuccess { items ->
                val known = if (reset) emptySet() else adapter.items.map { it.id }.toSet()
                val fresh = items.filterNot { it.id in known }
                hasMore = fresh.isNotEmpty() && items.size >= 50
                page = requestPage + 1
                adapter.submit(if (reset) fresh else adapter.items + fresh)
                if (reset) binding.nyaaRecycler.scrollToPosition(0)
                binding.nyaaEmpty.text = getString(R.string.nyaa_no_results)
            }.onFailure {
                hasMore = false
                if (reset) adapter.submit(emptyList())
                binding.nyaaEmpty.text = getString(R.string.nyaa_error)
            }
            binding.nyaaEmpty.isVisible = adapter.itemCount == 0
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private inner class TorrentAdapter : RecyclerView.Adapter<TorrentAdapter.Holder>() {
        var items: List<NyaaTorrent> = emptyList()
            private set

        fun submit(list: List<NyaaTorrent>) {
            items = list
            @Suppress("NotifyDataSetChanged")
            notifyDataSetChanged()
        }

        inner class Holder(val b: ItemNyaaTorrentBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            Holder(ItemNyaaTorrentBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val t = items[position]
            val ctx = holder.b.root.context
            holder.b.nyaaItemTitle.text = t.title
            holder.b.nyaaItemBadge.text = Nyaa.label(ctx, t)
            holder.b.nyaaItemTime.text =
                DateUtils.getRelativeTimeSpanString(t.pubDate, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
            holder.b.nyaaItemStats.text = ctx.getString(R.string.nyaa_stats, t.size, t.seeders, t.leechers)
            holder.b.root.setOnClickListener {
                NyaaTorrentSheet.newInstance(t).show(childFragmentManager, "nyaa_torrent")
            }
            holder.b.nyaaItemMagnet.setOnClickListener { TorrentActions.openMagnet(ctx, t) }
            holder.b.nyaaItemDownload.setOnClickListener { TorrentActions.downloadTorrentFile(ctx, t) }
        }
    }

    companion object {
        private const val ARG_QUERY = "query"
        private const val ARG_CATEGORY = "category"
        private const val ARG_FILTER = "filter"
        private const val ARG_ANIME_ONLY = "animeOnly"
        private const val ARG_MANGA_ONLY = "mangaOnly"
        private const val ARG_EXTRA_BOTTOM_PADDING = "extraBottomPadding"

        fun newInstance(
            query: String = "",
            category: NyaaCategory = NyaaCategory.ANIME,
            filter: Int = 0,
            animeOnly: Boolean = false,
            mangaOnly: Boolean = false,
            extraBottomPadding: Int = 0,
        ) = NyaaSearchFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_QUERY, query)
                putString(ARG_CATEGORY, category.key)
                putInt(ARG_FILTER, filter)
                putBoolean(ARG_ANIME_ONLY, animeOnly)
                putBoolean(ARG_MANGA_ONLY, mangaOnly)
                putInt(ARG_EXTRA_BOTTOM_PADDING, extraBottomPadding)
            }
        }
    }
}
