package com.arkhins.arkhime.others

import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.arkhins.arkhime.BottomSheetDialogFragment
import com.arkhins.arkhime.FileUrl
import com.arkhins.arkhime.R
import com.arkhins.arkhime.databinding.BottomSheetImageBinding
import com.arkhins.arkhime.openLinkInBrowser
import com.arkhins.arkhime.saveImageToDownloads
import com.arkhins.arkhime.setSafeOnClickListener
import com.arkhins.arkhime.shareImage
import com.arkhins.arkhime.snackString
import com.arkhins.arkhime.toast
import com.arkhins.arkhime.util.StoragePermissions.Companion.downloadsPermission
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import com.davemorrissey.labs.subscaleview.ImageSource
import kotlinx.coroutines.launch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class ImageViewDialog : BottomSheetDialogFragment() {

    private var _binding: BottomSheetImageBinding? = null
    private val binding get() = _binding!!

    private var reload = false
    private var _title: String? = null
    private var _image: FileUrl? = null
    private var _image2: FileUrl? = null

    var onReloadPressed: ((ImageViewDialog) -> Unit)? = null
    var trans1: List<BitmapTransformation>? = null
    var trans2: List<BitmapTransformation>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            _title = it.getString("title")?.replace(Regex("[\\\\/:*?\"<>|]"), "")
            reload = it.getBoolean("reload")
            _image = it.getSerialized("image")!!
            _image2 = it.getSerialized("image2")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetImageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val (title, image, image2) = Triple(_title, _image, _image2)
        if (image == null || title == null) {
            dismiss()
            snackString(getString(R.string.error_getting_image_data))
            return
        }
        if (reload) {
            binding.bottomImageReload.visibility = View.VISIBLE
            binding.bottomImageReload.setSafeOnClickListener {
                onReloadPressed?.invoke(this)
            }
        }

        binding.bottomImageTitle.text = title
        binding.bottomImageReload.setOnLongClickListener {
            openLinkInBrowser(image.url)
            if (image2 != null) openLinkInBrowser(image2.url)
            true
        }
        val context = requireContext()

        viewLifecycleOwner.lifecycleScope.launch {
            val binding = _binding ?: return@launch

            val preloaded = preloadedBitmap
            preloadedBitmap = null
            val bitmap = if (preloaded != null && !preloaded.isRecycled) {
                preloaded
            } else {
                loadImageBitmap(context, image)
            }

            if (bitmap != null) {
                binding.bottomImageShare.isEnabled = true
                binding.bottomImageSave.isEnabled = true
                binding.bottomImageSave.setOnClickListener {
                    if (downloadsPermission(context as AppCompatActivity))
                        saveImageToDownloads(title, bitmap, requireActivity())
                }
                binding.bottomImageShare.setOnClickListener {
                    shareImage(title, bitmap, requireContext())
                }

                binding.bottomImageView.setImage(ImageSource.cachedBitmap(bitmap))
                ObjectAnimator.ofFloat(binding.bottomImageView, "alpha", 0f, 1f).setDuration(400L)
                    .start()
                binding.bottomImageProgress.visibility = View.GONE
            } else {
                toast(context.getString(R.string.loading_image_failed))
                binding.bottomImageNo.visibility = View.VISIBLE
                binding.bottomImageProgress.visibility = View.GONE
            }
        }
    }

    override fun onDestroyView() {
        _binding?.bottomImageView?.recycle()
        _binding = null
        super.onDestroyView()
    }

    companion object {
        var preloadedBitmap: android.graphics.Bitmap? = null

        private suspend fun loadImageBitmap(context: android.content.Context, link: FileUrl): android.graphics.Bitmap? =
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    val source: Any = if (link.headers.isEmpty()) link.url
                    else com.bumptech.glide.load.model.GlideUrl(link.url) { link.headers }
                    com.bumptech.glide.Glide.with(context).asBitmap().load(source).submit().get()
                }.getOrNull()
            }

        fun newInstance(
            title: String,
            image: FileUrl,
            showReload: Boolean = false,
            image2: FileUrl?,
            bitmap: android.graphics.Bitmap? = null
        ) = ImageViewDialog().apply {
            preloadedBitmap = bitmap
            arguments = Bundle().apply {
                putString("title", title)
                putBoolean("reload", showReload)
                putSerializable("image", image)
                putSerializable("image2", image2)
            }
        }

        fun newInstance(activity: FragmentActivity, title: String?, image: String?): Boolean {
            ImageViewDialog().apply {
                arguments = Bundle().apply {
                    putString("title", title ?: return false)
                    putSerializable("image", FileUrl(image ?: return false))
                }
                show(activity.supportFragmentManager, "image")
            }
            return true
        }
    }
}
