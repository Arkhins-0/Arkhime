package ani.arkhime.com.media.user

import android.annotation.SuppressLint
import android.content.Intent
import android.text.InputType
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.RecyclerView
import ani.arkhime.com.R
import ani.arkhime.com.databinding.ItemSeriesEntryBinding
import ani.arkhime.com.databinding.ItemSeriesHeaderBinding
import ani.arkhime.com.loadImage
import ani.arkhime.com.media.Media
import ani.arkhime.com.media.MediaDetailsActivity
import ani.arkhime.com.util.customAlertDialog
import com.google.android.material.slider.Slider
import java.io.Serializable
import java.util.Locale

/**
 * The list as series rows (header + every title in the series) or as plain titles, with inline
 * controls for batch editing. Changes go to [onPatch]; nothing is saved here.
 */
class SeriesListAdapter(
    private val activity: FragmentActivity,
    isAnime: Boolean,
    private val onPatch: (Media, EntryPatch) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private sealed interface Row {
        class Header(val series: Series) : Row
        class Entry(val media: Media) : Row
    }

    private var series: List<Series>? = null
    private var titles: List<Media> = emptyList()
    private var rows: List<Row> = emptyList()
    private val collapsed = HashSet<Int>()

    private val statusKeys = activity.resources.getStringArray(R.array.status)
    private val statusLabels = activity.resources
        .getStringArray(if (isAnime) R.array.status_anime else R.array.status_manga)
        .map { it.lowercase(Locale.ROOT).replaceFirstChar { c -> c.titlecase(Locale.ROOT) } }
    private val unitLabel = if (isAnime) "ep" else "ch"
    private val density = activity.resources.displayMetrics.density

    var edits: Map<Int, EntryPatch> = emptyMap()
        @SuppressLint("NotifyDataSetChanged")
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    var editMode = false
        @SuppressLint("NotifyDataSetChanged")
        set(value) {
            if (field == value) return
            field = value
            notifyDataSetChanged()
        }

    fun showSeries(list: List<Series>) {
        series = list
        rebuild()
    }

    fun showTitles(list: List<Media>) {
        series = null
        titles = list
        rebuild()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun rebuild() {
        rows = series?.flatMap { s ->
            listOf(Row.Header(s)) + if (s.id in collapsed) emptyList() else s.entries.map { Row.Entry(it) }
        } ?: titles.map { Row.Entry(it) }
        notifyDataSetChanged()
    }

    override fun getItemCount() = rows.size

    override fun getItemViewType(position: Int) = if (rows[position] is Row.Header) HEADER else ENTRY

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == HEADER) {
            HeaderHolder(ItemSeriesHeaderBinding.inflate(inflater, parent, false))
        } else {
            EntryHolder(ItemSeriesEntryBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is Row.Header -> (holder as HeaderHolder).bind(row.series)
            is Row.Entry -> (holder as EntryHolder).bind(row.media)
        }
    }

    private inner class HeaderHolder(val b: ItemSeriesHeaderBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(s: Series) {
            val listed = s.entries.count { it.statusWith(edits[it.id]) != null }
            val missing = s.entries.size - listed
            val completion = s.completion(edits)
            b.seriesTitle.text = s.title
            b.seriesCount.text = if (missing > 0) "$listed +$missing" else "$listed"
            b.seriesCompletion.isVisible = completion > 0
            b.seriesCompletion.text = "${(completion * 100).toInt()}%"
            b.seriesBar.isVisible = completion > 0
            b.seriesBar.progress = (completion * 1000).toInt()
            b.seriesChevron.rotation = if (s.id in collapsed) -90f else 0f
            b.seriesHeader.setOnClickListener {
                if (!collapsed.remove(s.id)) collapsed.add(s.id)
                rebuild()
            }
        }
    }

    private inner class EntryHolder(val b: ItemSeriesEntryBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(media: Media) {
            val patch = edits[media.id]
            val status = media.statusWith(patch)
            val progress = media.progressWith(patch)
            val score = media.scoreWith(patch)
            val total = media.totalUnits
            val listed = status != null
            val progressText = "$progress / ${total ?: "?"}"

            b.seriesEntryCover.loadImage(media.cover)
            b.seriesEntryCover.alpha = if (listed) 1f else 0.55f
            b.seriesEntryTitle.text = media.userPreferredName
            b.seriesEntryMeta.text = meta(media)
            b.seriesEntryCard.strokeWidth = if (patch != null) (2 * density).toInt() else 0

            b.seriesEntryState.isVisible = !editMode
            b.seriesEntryState.text = if (listed) {
                listOfNotNull(label(status), progressText, scoreText(score)?.let { "★ $it" }).joinToString(" · ")
            } else {
                activity.getString(R.string.series_not_on_list)
            }
            b.seriesEntryControls.isVisible = editMode && listed
            b.seriesEntryAdd.isVisible = editMode && !listed
            b.seriesEntryBar.isVisible = listed && total != null && total > 0
            if (total != null && total > 0) {
                b.seriesEntryBar.progress = (progress.coerceAtMost(total) * 1000) / total
            }

            b.seriesEntryStatus.text = label(status)
            b.seriesEntryProgress.text = progressText
            b.seriesEntryScore.text = "★ ${scoreText(score) ?: "–"}"
            b.seriesEntryMinus.isEnabled = progress > 0
            b.seriesEntryPlus.isEnabled = total == null || progress < total

            val open = { openDetails(media) }
            b.seriesEntryCover.setOnClickListener { open() }
            b.seriesEntryTitle.setOnClickListener { open() }
            b.root.setOnClickListener { if (!editMode) open() }

            b.seriesEntryStatus.setOnClickListener { view ->
                PopupMenu(activity, view).apply {
                    statusLabels.forEachIndexed { i, text -> menu.add(0, i, i, text) }
                    setOnMenuItemClickListener { item ->
                        onPatch(media, EntryPatch(status = statusKeys[item.itemId]))
                        true
                    }
                }.show()
            }
            b.seriesEntryMinus.setOnClickListener {
                if (progress > 0) onPatch(media, EntryPatch(progress = progress - 1))
            }
            b.seriesEntryPlus.setOnClickListener {
                if (total == null || progress < total) onPatch(media, EntryPatch(progress = progress + 1))
            }
            b.seriesEntryProgress.setOnClickListener { askProgress(media, progress, total) }
            b.seriesEntryScore.setOnClickListener { askScore(media, score) }
            b.seriesEntryAdd.setOnClickListener { onPatch(media, EntryPatch(status = "PLANNING")) }
        }
    }

    private fun label(status: String?): String? =
        statusKeys.indexOf(status).takeIf { it >= 0 }?.let { statusLabels[it] }

    /** POINT_100 -> "8.5", "7"; null when unscored */
    private fun scoreText(score: Int): String? {
        if (score <= 0) return null
        val tenths = score / 10.0
        return if (tenths % 1.0 == 0.0) tenths.toInt().toString() else String.format(Locale.ROOT, "%.1f", tenths)
    }

    private fun meta(media: Media): String {
        val format = media.format?.let {
            if (it.length <= 3) it else it.replace('_', ' ').lowercase(Locale.ROOT)
                .replaceFirstChar { c -> c.titlecase(Locale.ROOT) }
        }
        val units = media.totalUnits?.let { "$it $unitLabel" + if (it == 1 || unitLabel == "ch") "" else "s" }
        return listOfNotNull(format, media.startDate?.year?.toString(), units).joinToString(" · ")
    }

    private fun openDetails(media: Media) {
        activity.startActivity(
            Intent(activity, MediaDetailsActivity::class.java).putExtra("media", media as Serializable)
        )
    }

    private fun padded(view: android.view.View) = FrameLayout(activity).apply {
        val pad = (20 * density).toInt()
        setPadding(pad, (8 * density).toInt(), pad, 0)
        addView(view)
    }

    private fun askProgress(media: Media, current: Int, total: Int?) {
        val input = EditText(activity).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(current.toString())
            setSelectAllOnFocus(true)
            hint = total?.let { "0 – $it" }
        }
        activity.customAlertDialog().apply {
            setTitle(activity.getString(R.string.series_set_progress))
            setCustomView(padded(input))
            setPosButton(activity.getString(R.string.ok)) {
                val value = input.text.toString().toIntOrNull() ?: return@setPosButton
                val clamped = if (total != null) value.coerceIn(0, total) else value.coerceAtLeast(0)
                onPatch(media, EntryPatch(progress = clamped))
            }
            setNegButton(activity.getString(R.string.cancel))
        }.show()
    }

    private fun askScore(media: Media, current: Int) {
        val slider = Slider(activity).apply {
            valueFrom = 0f
            valueTo = 10f
            stepSize = 0.5f
            // Nearest half point, the slider's step
            value = Math.round(current.coerceIn(0, 100) / 5f) / 2f
        }
        activity.customAlertDialog().apply {
            setTitle(activity.getString(R.string.series_set_score))
            setCustomView(padded(slider))
            setPosButton(activity.getString(R.string.ok)) {
                onPatch(media, EntryPatch(score = (slider.value * 10).toInt()))
            }
            setNeutralButton(activity.getString(R.string.series_clear_score)) {
                onPatch(media, EntryPatch(score = 0))
            }
            setNegButton(activity.getString(R.string.cancel))
        }.show()
    }

    private companion object {
        const val HEADER = 0
        const val ENTRY = 1
    }
}
