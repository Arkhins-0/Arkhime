package com.arkhins.arkhime.media.user

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.arkhins.arkhime.databinding.FragmentListBinding
import com.arkhins.arkhime.media.Media
import com.arkhins.arkhime.media.MediaAdaptor
import com.arkhins.arkhime.media.OtherDetailsViewModel

class ListFragment : Fragment() {
    private var _binding: FragmentListBinding? = null
    private val binding get() = _binding!!
    private var pos: Int? = null
    private var calendar = false
    private var grid: Boolean? = null
    private var list: MutableList<Media>? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            pos = it.getInt("list")
            calendar = it.getBoolean("calendar")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val screenWidth = resources.displayMetrics.run { widthPixels / density }
        val density = resources.displayMetrics.density
        val listModel: ListViewModel? = if (calendar) null else activityViewModels<ListViewModel>().value
        var seriesAdapter: SeriesListAdapter? = null

        fun update() {
            val list = list ?: return
            val model = listModel
            val editing = model?.editMode?.value == true
            val grouped = model?.groupBySeries?.value == true && model.seriesIndex.isNotEmpty()
            if (model != null && (editing || grouped)) {
                val adapter = seriesAdapter ?: SeriesListAdapter(
                    requireActivity(),
                    requireActivity().intent.getBooleanExtra("anime", true),
                    model::patch
                ).also { seriesAdapter = it }
                adapter.edits = model.edits.value.orEmpty()
                adapter.editMode = editing
                if (grouped) adapter.showSeries(seriesInTab(list, model.seriesIndex)) else adapter.showTitles(list)
                if (binding.listRecyclerView.adapter !== adapter) {
                    binding.listRecyclerView.layoutManager = LinearLayoutManager(requireContext())
                    binding.listRecyclerView.adapter = adapter
                }
                // Room for the save bar
                binding.listRecyclerView.updatePadding(bottom = ((if (editing) 96 else 8) * density).toInt())
                return
            }
            val grid = grid ?: return
            val type = if (grid) 0 else 1
            val current = binding.listRecyclerView.adapter as? MediaAdaptor
            val currentList = current?.mediaList
            if (current != null && current.type == type && currentList != null) {
                // Refreshed data with the same layout: update in place and keep the scroll position
                if (currentList !== list) {
                    currentList.clear()
                    currentList.addAll(list)
                }
                current.notifyDataSetChanged()
                binding.listRecyclerView.updatePadding(bottom = (8 * density).toInt())
                return
            }
            // The adapter keeps its own copy, so later refreshes can update it in place
            val adapter = MediaAdaptor(type, ArrayList(list), requireActivity(), true)
            binding.listRecyclerView.layoutManager =
                GridLayoutManager(
                    requireContext(),
                    if (grid) (screenWidth / 120f).toInt() else 1
                )
            binding.listRecyclerView.adapter = adapter
            binding.listRecyclerView.updatePadding(bottom = (8 * density).toInt())
        }

        if (listModel == null) {
            val model: OtherDetailsViewModel by activityViewModels()
            model.getCalendar().observe(viewLifecycleOwner) {
                if (it != null) {
                    list = it.values.toList().getOrNull(pos!!)
                    update()
                }
            }
            grid = true
        } else {
            listModel.getLists().observe(viewLifecycleOwner) {
                if (it != null) {
                    list = it.values.toList().getOrNull(pos!!)
                    update()
                }
            }
            listModel.grid.observe(viewLifecycleOwner) {
                grid = it
                update()
            }
            listModel.editMode.observe(viewLifecycleOwner) { update() }
            listModel.edits.observe(viewLifecycleOwner) { seriesAdapter?.edits = it }
        }
    }

    fun randomOptionClick() {
        (binding.listRecyclerView.adapter as? MediaAdaptor)?.randomOptionClick()
    }

    companion object {
        fun newInstance(pos: Int, calendar: Boolean = false): ListFragment =
            ListFragment().apply {
                arguments = Bundle().apply {
                    putInt("list", pos)
                    putBoolean("calendar", calendar)
                }
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}