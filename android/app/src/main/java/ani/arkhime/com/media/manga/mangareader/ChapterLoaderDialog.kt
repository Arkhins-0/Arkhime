package ani.arkhime.com.media.manga.mangareader

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import ani.arkhime.com.BottomSheetDialogFragment
import ani.arkhime.com.R
import ani.arkhime.com.currActivity
import ani.arkhime.com.databinding.BottomSheetSelectorBinding
import ani.arkhime.com.getThemeColor
import ani.arkhime.com.media.MediaDetailsViewModel
import ani.arkhime.com.media.MediaSingleton
import ani.arkhime.com.media.manga.MangaChapter
import ani.arkhime.com.others.getSerialized
import ani.arkhime.com.tryWith
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.Serializable

class ChapterLoaderDialog : BottomSheetDialogFragment() {
    private var _binding: BottomSheetSelectorBinding? = null
    private val binding get() = _binding!!

    val model: MediaDetailsViewModel by activityViewModels()

    private val launch: Boolean by lazy { arguments?.getBoolean("launch", false) ?: false }
    private val chp: MangaChapter by lazy { arguments?.getSerialized("next")!! }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        var loaded = false
        binding.selectorAutoListContainer.visibility = View.VISIBLE
        binding.selectorListContainer.visibility = View.GONE

        binding.selectorTitle.text = getString(R.string.loading_chap_number, chp.number)
        binding.selectorCancel.setOnClickListener {
            dismiss()
        }

        model.getMedia().observe(viewLifecycleOwner) { m ->
            if (m != null && !loaded) {
                loaded = true
                binding.selectorAutoText.text = chp.title
                lifecycleScope.launch(Dispatchers.IO) {
                    if (model.loadMangaChapterImages(
                            chp,
                            m.selected!!
                        )
                    ) {
                        val activity = currActivity()
                        activity?.runOnUiThread {
                            tryWith { dismiss() }
                            if (launch) {
                                MediaSingleton.media = m
                                val intent = Intent(
                                    activity,
                                    MangaReaderActivity::class.java
                                )//.apply { putExtra("media", m) }
                                activity.startActivity(intent)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetSelectorBinding.inflate(inflater, container, false)
        val window = dialog?.window
        window?.statusBarColor = Color.TRANSPARENT
        window?.navigationBarColor =
            requireContext().getThemeColor(com.google.android.material.R.attr.colorSurface)
        return binding.root
    }

    override fun onDestroy() {
        _binding = null
        super.onDestroy()
    }

    companion object {
        fun newInstance(next: MangaChapter, launch: Boolean = false) = ChapterLoaderDialog().apply {
            arguments = bundleOf("next" to next as Serializable, "launch" to launch)
        }
    }
}