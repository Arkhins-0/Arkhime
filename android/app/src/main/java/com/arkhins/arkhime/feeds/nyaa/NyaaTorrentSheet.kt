package com.arkhins.arkhime.feeds.nyaa

import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.format.DateUtils
import android.text.style.StyleSpan
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.text.inSpans
import androidx.core.view.isVisible
import androidx.core.view.setPadding
import androidx.lifecycle.lifecycleScope
import com.arkhins.arkhime.BottomSheetDialogFragment
import com.arkhins.arkhime.R
import com.arkhins.arkhime.buildMarkwon
import com.arkhins.arkhime.databinding.BottomSheetNyaaTorrentBinding
import com.arkhins.arkhime.px
import eu.kanade.tachiyomi.util.system.getSerializableCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Torrent info: stats, magnet / .torrent actions, then the file list, description and comments. */
class NyaaTorrentSheet : BottomSheetDialogFragment() {
    private var _binding: BottomSheetNyaaTorrentBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetNyaaTorrentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val torrent = requireArguments().getSerializableCompat<NyaaTorrent>("torrent") ?: return dismiss()
        val ctx = requireContext()

        binding.torrentTitle.text = torrent.title
        binding.torrentInfo.text = info(torrent, null)

        binding.torrentMagnet.setOnClickListener { TorrentActions.openMagnet(ctx, torrent) }
        binding.torrentDownload.setOnClickListener { TorrentActions.downloadTorrentFile(ctx, torrent) }
        binding.torrentCopy.setOnClickListener { TorrentActions.copyMagnet(torrent) }
        binding.torrentShare.setOnClickListener { TorrentActions.share(ctx, torrent) }
        binding.torrentOpenPage.setOnClickListener { TorrentActions.openPage(ctx, torrent) }

        viewLifecycleOwner.lifecycleScope.launch {
            val details = withContext(Dispatchers.IO) { runCatching { Nyaa.details(torrent) }.getOrNull() }
            if (_binding == null) return@launch
            binding.torrentDetailsProgress.isVisible = false
            if (details == null) return@launch
            binding.torrentInfo.text = info(torrent, details)

            if (details.files.isNotEmpty()) {
                binding.torrentFilesHeader.isVisible = true
                binding.torrentFilesHeader.text = getString(R.string.files)
                binding.torrentFiles.isVisible = true
                binding.torrentFiles.text = details.files.joinToString("\n") { "• $it" }
            }
            details.description?.let {
                binding.torrentDescriptionHeader.isVisible = true
                binding.torrentDescription.isVisible = true
                runCatching { buildMarkwon(ctx, true, this@NyaaTorrentSheet).setMarkdown(binding.torrentDescription, it) }
                    .onFailure { _ -> binding.torrentDescription.text = it }
            }
            if (details.comments.isNotEmpty()) {
                binding.torrentCommentsHeader.isVisible = true
                binding.torrentCommentsHeader.text = getString(R.string.nyaa_comments)
                details.comments.forEach { c ->
                    binding.torrentComments.addView(TextView(ctx).apply {
                        setPadding(0)
                        setPadding(0, 0, 0, 12.px)
                        textSize = 13f
                        setTextIsSelectable(true)
                        text = SpannableStringBuilder()
                            .inSpans(StyleSpan(Typeface.BOLD)) { append(c.user) }
                            .append("  ").append(c.date).append("\n").append(c.text).append("\n")
                    })
                }
            }
        }
    }

    private fun info(t: NyaaTorrent, d: NyaaDetails?): CharSequence = buildString {
        val date = DateUtils.formatDateTime(requireContext(), t.pubDate,
            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR)
        appendLine(listOfNotNull(Nyaa.label(requireContext(), t), d?.submitter, date).joinToString(" · "))
        append(getString(R.string.nyaa_stats, t.size, t.seeders, t.leechers))
        append(" · ").append(getString(R.string.nyaa_downloads, t.downloads))
        d?.information?.let { append("\n").append(it) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(torrent: NyaaTorrent) = NyaaTorrentSheet().apply {
            arguments = Bundle().apply { putSerializable("torrent", torrent) }
        }
    }
}
