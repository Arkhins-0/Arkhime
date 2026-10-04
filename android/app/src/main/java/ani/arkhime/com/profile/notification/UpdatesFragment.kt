package ani.arkhime.com.profile.notification

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ani.arkhime.com.R
import ani.arkhime.com.databinding.FragmentUpdatesBinding
import ani.arkhime.com.databinding.ItemUpdateBinding
import ani.arkhime.com.loadImage
import ani.arkhime.com.media.MediaDetailsActivity
import ani.arkhime.com.notifications.updates.UpdateEvent
import ani.arkhime.com.notifications.updates.UpdateStore
import ani.arkhime.com.notifications.updates.UpdateType
import ani.arkhime.com.notifications.updates.UpdatesTask
import ani.arkhime.com.settings.saving.PrefManager
import ani.arkhime.com.settings.saving.PrefName
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Site-wide releases and additions plus changes to titles on the list, kept on the device by
 * [UpdatesTask]. Shows the newest 100, then 50 more per "Load more".
 */
class UpdatesFragment : Fragment() {
    private var _binding: FragmentUpdatesBinding? = null
    private val binding get() = _binding!!
    private var all: List<UpdateEvent> = emptyList()
    private var filter: UpdateType? = null
    private var shown = FIRST_PAGE
    private val adapter = UpdatesAdapter()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentUpdatesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.updatesRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.updatesRecyclerView.adapter = adapter

        addFilterChip(null, getString(R.string.update_all))
        UpdateType.entries.forEach { addFilterChip(it, getString(it.label)) }

        binding.updatesSwipeRefresh.setOnRefreshListener { refresh() }
        load()
        // Nothing stored yet (first open): fetch now instead of waiting for the background check
        if (all.isEmpty()) refresh()
    }

    override fun onResume() {
        super.onResume()
        PrefManager.setVal(PrefName.UnreadUpdates, 0)
    }

    private fun addFilterChip(type: UpdateType?, label: String) {
        val chip = Chip(requireContext()).apply {
            text = label
            isCheckable = true
            isChecked = type == filter
            setOnClickListener {
                filter = type
                shown = FIRST_PAGE
                render()
                binding.updatesRecyclerView.scrollToPosition(0)
            }
        }
        binding.updatesFilters.addView(chip)
    }

    private fun load() {
        all = UpdateStore.all(requireContext())
        render()
    }

    private fun refresh() {
        binding.updatesProgress.isVisible = all.isEmpty()
        binding.updatesSwipeRefresh.isRefreshing = all.isNotEmpty()
        val appContext = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            // The user is looking at the list, so no pushes for what this finds
            withContext(Dispatchers.IO) { UpdatesTask(push = false).execute(appContext) }
            val b = _binding ?: return@launch
            b.updatesProgress.isVisible = false
            b.updatesSwipeRefresh.isRefreshing = false
            PrefManager.setVal(PrefName.UnreadUpdates, 0)
            load()
        }
    }

    private fun render() {
        val matching = all.filter { filter == null || it.type == filter }
        adapter.submit(matching.take(shown), remaining = (matching.size - shown).coerceAtLeast(0))
        binding.updatesEmpty.isVisible = matching.isEmpty() && !binding.updatesProgress.isVisible
    }

    private fun open(event: UpdateEvent) {
        if (event.mediaId <= 0) return
        startActivity(Intent(requireContext(), MediaDetailsActivity::class.java).putExtra("mediaId", event.mediaId))
    }

    private inner class UpdatesAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private var items: List<UpdateEvent> = emptyList()
        private var remaining = 0

        @SuppressLint("NotifyDataSetChanged")
        fun submit(list: List<UpdateEvent>, remaining: Int) {
            items = list
            this.remaining = remaining
            notifyDataSetChanged()
        }

        override fun getItemCount() = items.size + if (remaining > 0) 1 else 0
        override fun getItemViewType(position: Int) = if (position < items.size) ITEM else MORE

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            if (viewType == ITEM) {
                return object : RecyclerView.ViewHolder(
                    ItemUpdateBinding.inflate(LayoutInflater.from(parent.context), parent, false).root
                ) {}
            }
            val button = MaterialButton(parent.context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
            val frame = FrameLayout(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                val pad = (12 * resources.displayMetrics.density).toInt()
                setPadding(pad, pad, pad, pad)
                addView(button, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }
            return object : RecyclerView.ViewHolder(frame) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            if (getItemViewType(position) == MORE) {
                val button = (holder.itemView as FrameLayout).getChildAt(0) as MaterialButton
                button.text = getString(R.string.update_load_more, remaining)
                button.setOnClickListener {
                    shown += NEXT_PAGE
                    render()
                }
                return
            }
            val event = items[position]
            val b = ItemUpdateBinding.bind(holder.itemView)
            b.updateCover.loadImage(event.cover)
            b.updateType.text = getString(event.type.label)
            b.updateTitle.text = event.title
            b.updateText.text = event.text
            b.updateTime.text = DateUtils.getRelativeTimeSpanString(
                event.time * 1000, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS
            )
            b.root.setOnClickListener { open(event) }
        }
    }

    private companion object {
        const val FIRST_PAGE = 100
        const val NEXT_PAGE = 50
        const val ITEM = 0
        const val MORE = 1
    }
}
