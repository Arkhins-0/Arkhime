package com.arkhins.arkhime.media

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.arkhins.arkhime.R
import com.arkhins.arkhime.feeds.crunchyroll.CrunchyrollFragment
import com.arkhins.arkhime.feeds.nyaa.NyaaCategory
import com.arkhins.arkhime.feeds.nyaa.NyaaSearchFragment
import com.arkhins.arkhime.navBarHeight
import com.arkhins.arkhime.px
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

/**
 * The "Releases" tab of an anime or manga page: nyaa.si torrents searched by the title, and for
 * anime, the title's Crunchyroll episodes.
 */
class MediaReleasesFragment : Fragment() {
    private val model: MediaDetailsViewModel by activityViewModels()
    private val containerId = View.generateViewId()
    private var toggle: ChipGroup? = null
    private var crButtonId = View.NO_ID
    private var nyaaButtonId = View.NO_ID
    private var current: Int = View.NO_ID

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val ctx = requireContext()
        // Same chip row as the filters on the Updates screen
        val group = ChipGroup(ctx).apply {
            isSingleSelection = true
            isSelectionRequired = true
            isSingleLine = true
        }
        fun chip(text: Int) = (inflater.inflate(R.layout.item_feed_chip, group, false) as Chip).apply {
            id = View.generateViewId()
            setText(text)
        }
        val nyaa = chip(R.string.nyaa).also { nyaaButtonId = it.id }
        val cr = chip(R.string.crunchyroll).also { crButtonId = it.id }
        group.addView(nyaa)
        group.addView(cr)
        toggle = group
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(group, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { setMargins(12.px, 4.px, 12.px, 0) })
            addView(FrameLayout(ctx).apply { id = containerId },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        model.getMedia().observe(viewLifecycleOwner) { media ->
            if (media == null) return@observe
            val group = toggle ?: return@observe
            group.isVisible = media.anime != null
            group.findViewById<Chip>(nyaaButtonId)?.setText(if (media.isAdult) R.string.sukebei else R.string.nyaa)
            group.setOnCheckedStateChangeListener(null)
            val start = if (current != View.NO_ID) current else nyaaButtonId
            group.check(start)
            // Media updates arrive more than once; only rebuild when there's nothing shown yet, or for
            // Crunchyroll, whose episode list can arrive with a later update.
            show(media, start, force = childFragmentManager.findFragmentById(containerId) == null || start == crButtonId)
            group.setOnCheckedStateChangeListener { _, ids -> ids.firstOrNull()?.let { show(media, it) } }
        }
    }

    private fun show(media: Media, buttonId: Int, force: Boolean = false) {
        if (!force && buttonId == current && childFragmentManager.findFragmentById(containerId) != null) return
        current = buttonId
        val bottom = navBarHeight + 72.px
        val fragment = if (buttonId == crButtonId) {
            CrunchyrollFragment.newForMedia(
                media.id,
                media.externalLinks?.firstOrNull { it.site.equals("Crunchyroll", true) }?.url,
                media.streamingEpisodes.orEmpty()
                    .filter { it.site.equals("Crunchyroll", true) }
                    .map { Triple(it.title.orEmpty(), it.url, it.thumbnail) },
                bottom,
            )
        } else {
            val isAnime = media.anime != null
            // Adult titles are not allowed on nyaa.si; their releases are on sukebei.nyaa.si.
            NyaaSearchFragment.newInstance(
                query = nyaaQuery(media),
                category = when {
                    media.isAdult && isAnime -> NyaaCategory.SUKEBEI_ANIME
                    media.isAdult -> NyaaCategory.SUKEBEI_MANGA
                    isAnime -> NyaaCategory.ANIME
                    else -> NyaaCategory.MANGA
                },
                animeOnly = isAnime,
                mangaOnly = !isAnime,
                extraBottomPadding = bottom,
            )
        }
        childFragmentManager.beginTransaction().replace(containerId, fragment).commitAllowingStateLoss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        toggle = null
    }

    companion object {
        /** Release groups name files by the romaji title; punctuation only narrows nyaa's search. */
        fun nyaaQuery(media: Media): String =
            media.nameRomaji.replace(Regex("[:!?.,~\"'()\\[\\]]"), " ").replace(Regex("\\s+"), " ").trim()
    }
}
