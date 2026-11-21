package com.arkhins.arkhime.media

import android.os.Bundle
import android.text.InputFilter.LengthFilter
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import com.arkhins.arkhime.BottomSheetDialogFragment
import com.arkhins.arkhime.DatePickerFragment
import com.arkhins.arkhime.InputFilterMinMax
import com.arkhins.arkhime.R
import com.arkhins.arkhime.Refresh
import com.arkhins.arkhime.connections.PendingProgressUpdate
import com.arkhins.arkhime.connections.anilist.Anilist
import com.arkhins.arkhime.connections.anilist.api.FuzzyDate
import com.arkhins.arkhime.connections.mal.MAL
import com.arkhins.arkhime.databinding.BottomSheetMediaListSmallBinding
import com.arkhins.arkhime.navBarHeight
import com.arkhins.arkhime.others.getSerialized
import com.arkhins.arkhime.settings.saving.PrefManager
import com.arkhins.arkhime.settings.saving.PrefName
import com.arkhins.arkhime.snackString
import com.arkhins.arkhime.tryWith
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.Serializable


class MediaListDialogSmallFragment : BottomSheetDialogFragment() {

    private lateinit var media: Media

    companion object {
        fun newInstance(m: Media): MediaListDialogSmallFragment =
            MediaListDialogSmallFragment().apply {
                arguments = Bundle().apply {
                    putSerializable("media", m as Serializable)
                }
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            media = it.getSerialized("media")!!
        }
    }

    private var _binding: BottomSheetMediaListSmallBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetMediaListSmallBinding.inflate(inflater, container, false)
        return binding.root
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.mediaListContainer.updateLayoutParams<ViewGroup.MarginLayoutParams> { bottomMargin += navBarHeight }
        val scope = viewLifecycleOwner.lifecycleScope
        binding.mediaListDelete.setOnClickListener {
            scope.launch {
                media.deleteFromList(scope, onSuccess = {
                    Refresh.all()
                    snackString(getString(R.string.deleted_from_list))
                    dismissAllowingStateLoss()
                }, onError = { e ->
                    withContext(Dispatchers.Main) {
                        snackString(
                            getString(
                                R.string.delete_fail_reason, e.message
                            )
                        )
                    }
                }, onNotFound = {
                    snackString(getString(R.string.no_list_id))
                })
            }
        }

        binding.mediaListProgressBar.visibility = View.GONE
        binding.mediaListLayout.visibility = View.VISIBLE
        val statuses: Array<String> = resources.getStringArray(R.array.status)
        val statusStrings =
            if (media.manga == null) resources.getStringArray(R.array.status_anime) else resources.getStringArray(
                R.array.status_manga
            )
        val userStatus =
            if (media.userStatus != null) statusStrings[statuses.indexOf(media.userStatus).coerceAtLeast(0)] else statusStrings[0]

        binding.mediaListStatus.setText(userStatus)
        binding.mediaListStatus.setAdapter(
            ArrayAdapter(
                requireContext(),
                R.layout.item_dropdown,
                statusStrings
            )
        )


        var total: Int? = null
        binding.mediaListProgress.setText(if (media.userProgress != null) media.userProgress.toString() else "")
        if (media.anime != null) if (media.anime!!.totalEpisodes != null) {
            total = media.anime!!.totalEpisodes!!;binding.mediaListProgress.filters =
                arrayOf(
                    InputFilterMinMax(0.0, total.toDouble(), binding.mediaListStatus),
                    LengthFilter(total.toString().length)
                )
        } else if (media.manga != null) if (media.manga!!.totalChapters != null) {
            total = media.manga!!.totalChapters!!;binding.mediaListProgress.filters =
                arrayOf(
                    InputFilterMinMax(0.0, total.toDouble(), binding.mediaListStatus),
                    LengthFilter(total.toString().length)
                )
        }
        binding.mediaListProgressLayout.suffixText = " / ${total ?: '?'}"
        binding.mediaListProgressLayout.suffixTextView.updateLayoutParams {
            height = ViewGroup.LayoutParams.MATCH_PARENT
        }
        binding.mediaListProgressLayout.suffixTextView.gravity = Gravity.CENTER



        binding.mediaListScore.setText(
            if (media.userScore != 0) media.userScore.div(
                10.0
            ).toString() else ""
        )
        binding.mediaListScore.filters =
            arrayOf(InputFilterMinMax(0.0, 10.0), LengthFilter(10.0.toString().length))
        binding.mediaListScoreLayout.suffixTextView.updateLayoutParams {
            height = ViewGroup.LayoutParams.MATCH_PARENT
        }
        binding.mediaListScoreLayout.suffixTextView.gravity = Gravity.CENTER

        // Start and end dates (same behaviour as the full list editor)
        val start = DatePickerFragment(requireActivity(), media.userStartedAt)
        val end = DatePickerFragment(requireActivity(), media.userCompletedAt)
        binding.mediaListStart.setText(media.userStartedAt.toStringOrEmpty())
        binding.mediaListEnd.setText(media.userCompletedAt.toStringOrEmpty())
        binding.mediaListStart.setOnClickListener {
            tryWith(false) { if (!start.dialog.isShowing) start.dialog.show() }
        }
        binding.mediaListStart.setOnFocusChangeListener { _, focused ->
            tryWith(false) { if (focused && !start.dialog.isShowing) start.dialog.show() }
        }
        binding.mediaListEnd.setOnClickListener {
            tryWith(false) { if (!end.dialog.isShowing) end.dialog.show() }
        }
        binding.mediaListEnd.setOnFocusChangeListener { _, focused ->
            tryWith(false) { if (focused && !end.dialog.isShowing) end.dialog.show() }
        }
        start.dialog.setOnDismissListener { _binding?.mediaListStart?.setText(start.date.toStringOrEmpty()) }
        end.dialog.setOnDismissListener { _binding?.mediaListEnd?.setText(end.date.toStringOrEmpty()) }

        // Marking an entry Completed fills in the dates, like the full editor
        fun fillCompletionDates() {
            if (start.date.year == null) {
                start.date = FuzzyDate().getToday()
                binding.mediaListStart.setText(start.date.toString())
            }
            if (end.date.year == null) {
                end.date = FuzzyDate().getToday()
                binding.mediaListEnd.setText(end.date.toString())
            }
        }
        binding.mediaListStatus.setOnItemClickListener { _, _, i, _ ->
            if (i == 2) fillCompletionDates()
        }



        binding.mediaListIncrement.setOnClickListener {
            if (binding.mediaListStatus.text.toString() == statusStrings[0]) binding.mediaListStatus.setText(
                statusStrings[1],
                false
            )
            val init =
                if (binding.mediaListProgress.text.toString() != "") binding.mediaListProgress.text.toString()
                    .toInt() else 0
            if (init < (total ?: 5000)) {
                val progressText = "${init + 1}"
                binding.mediaListProgress.setText(progressText)
            }
            if (init + 1 == (total ?: 5000)) {
                binding.mediaListStatus.setText(statusStrings[2], false)
                fillCompletionDates()
            }
        }

        binding.mediaListDecrement.setOnClickListener {
            val init =
                if (binding.mediaListProgress.text.toString() != "") binding.mediaListProgress.text.toString()
                    .toInt() else 0
            if (init > 0) {
                val progressText = "${init - 1}"
                binding.mediaListProgress.setText(progressText)
            }
        }

        val isRescueMode = PrefManager.getVal<Boolean>(PrefName.RescueMode)
        if (isRescueMode) {
            binding.mediaListPrivate.apply { (parent as? ViewGroup)?.removeView(this) }
        } else {
            binding.mediaListPrivate.visibility = View.VISIBLE
        }
        binding.mediaListPrivate.isChecked = media.isListPrivate
        binding.mediaListPrivate.setOnCheckedChangeListener { _, checked ->
            media.isListPrivate = checked
        }
        val removeList = PrefManager.getCustomVal<Set<String>>("removeList", emptySet())
        var remove: Boolean? = null
        binding.mediaListShow.isChecked = media.id.toString() in removeList
        binding.mediaListShow.setOnCheckedChangeListener { _, checked ->
            remove = checked
        }
        binding.mediaListSave.setOnClickListener {
            val progressText = binding.mediaListProgress.text.toString()
            val scoreText = binding.mediaListScore.text.toString()
            val statusText = binding.mediaListStatus.text.toString()
            scope.launch {
                withContext(Dispatchers.IO) {
                    val progress = _binding?.mediaListProgress?.text.toString().toIntOrNull()
                    val progressVolumes = media.userProgressVolumes
                    val score = (_binding?.mediaListScore?.text.toString().toDoubleOrNull()?.times(10))?.toInt()
                    val statusText = _binding?.mediaListStatus?.text.toString()
                    val status = statuses[statusStrings.indexOf(statusText).coerceAtLeast(0)]
                    val startD = start.date
                    val endD = end.date
                    val rescueMode: Boolean = PrefManager.getVal(PrefName.RescueMode)
                    if (rescueMode) {
                        val pending = PendingProgressUpdate(
                            mediaId = media.id,
                            idMAL = media.idMAL,
                            isAnime = media.anime != null,
                            progress = progress ?: 0,
                            status = status,
                            score = score,
                            isPrivate = media.isListPrivate,
                            startDate = startD,
                            endDate = endD,
                        )
                        val existing: List<PendingProgressUpdate> =
                            PrefManager.getVal(PrefName.PendingProgressUpdates, listOf())
                        val updated = existing.filterNot { it.mediaId == media.id } + pending
                        PrefManager.setVal(PrefName.PendingProgressUpdates, updated)
                    } else {
                        Anilist.mutation.editList(
                            mediaID = media.id,
                            progress = progress,
                            progressVolumes = progressVolumes,
                            score = score,
                            status = status,
                            private = media.isListPrivate,
                            startedAt = startD,
                            completedAt = endD,
                        )
                    }
                    MAL.query.editList(
                        media.idMAL,
                        media.anime != null,
                        progress,
                        score,
                        status,
                        start = startD,
                        end = endD
                    )
                    // Show the change on the card straight away; the refresh then runs in the background
                    media.userProgress = progress
                    media.userStatus = status
                    media.userScore = score ?: 0
                    media.userStartedAt = startD
                    media.userCompletedAt = endD
                }
                if (remove == true) {
                    PrefManager.setCustomVal("removeList", removeList.plus(media.id.toString()))
                } else if (remove == false) {
                    PrefManager.setCustomVal("removeList", removeList.minus(media.id.toString()))
                }
                Refresh.all()
                snackString(getString(R.string.list_updated))
                dismissAllowingStateLoss()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
