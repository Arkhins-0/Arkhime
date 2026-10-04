package ani.arkhime.com.media

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.text.bold
import androidx.core.text.color
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updateMargins
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import ani.arkhime.com.GesturesListener
import ani.arkhime.com.MainActivity
import ani.arkhime.com.R
import ani.arkhime.com.Refresh
import ani.arkhime.com.ZoomOutPageTransformer
import ani.arkhime.com.blurImage
import ani.arkhime.com.connections.anilist.Anilist
import ani.arkhime.com.connections.mal.MAL
import ani.arkhime.com.copyToClipboard
import ani.arkhime.com.databinding.ActivityMediaBinding
import ani.arkhime.com.getThemeColor
import ani.arkhime.com.initActivity
import ani.arkhime.com.loadImage
import ani.arkhime.com.navBarHeight
import ani.arkhime.com.openLinkInBrowser
import ani.arkhime.com.others.AndroidBug5497Workaround
import ani.arkhime.com.others.ImageViewDialog
import ani.arkhime.com.others.getSerialized
import ani.arkhime.com.settings.saving.PrefManager
import ani.arkhime.com.settings.saving.PrefName
import ani.arkhime.com.snackString
import ani.arkhime.com.statusBarHeight
import ani.arkhime.com.themes.ThemeManager
import ani.arkhime.com.util.LauncherWrapper
import com.flaviofaria.kenburnsview.RandomTransitionGenerator
import com.google.android.material.appbar.AppBarLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import nl.joery.animatedbottombar.AnimatedBottomBar
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds


class MediaDetailsActivity : AppCompatActivity(), AppBarLayout.OnOffsetChangedListener {
    lateinit var launcher: LauncherWrapper
    lateinit var binding: ActivityMediaBinding

    /** False while the activity is bailing out of [onCreate] without any media. */
    val bindingReady get() = ::binding.isInitialized
    val safeBinding get() = if (::binding.isInitialized) binding else null
    private val scope = lifecycleScope
    private val model: MediaDetailsViewModel by viewModels()
    var selected = 0
    lateinit var navBar: AnimatedBottomBar
    var anime = true
    private var adult = false
    lateinit var media: Media

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::media.isInitialized && media.name != "No media found") {
            outState.putInt("saved_media_id", media.id)
            mediaCache.put(media.id, media)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val savedId = savedInstanceState?.getInt("saved_media_id", -1)?.takeIf { it != -1 }
        var media: Media = intent.getSerialized("media")
            ?: savedId?.let { mediaCache.get(it) }
            ?: savedInstanceState?.getSerialized("saved_media")
            ?: mediaSingleton
            ?: emptyMedia()
        val id = intent.getIntExtra("mediaId", -1).takeIf { it != -1 }
            ?: savedId
            ?: -1
        if (id != -1 && media.name == "No media found") {
            val rescueMode: Boolean = PrefManager.getVal(PrefName.RescueMode)
            runBlocking(Dispatchers.IO) {
                if (rescueMode) {
                    val animeNode = MAL.query.getAnimeDetails(id)
                    if (animeNode != null) {
                        media = Media(animeNode, true)
                    } else {
                        val mangaNode = MAL.query.getMangaDetails(id)
                        media = if (mangaNode != null) Media(mangaNode, false)
                        else emptyMedia()
                    }
                } else {
                    media = Anilist.query.getMedia(id, false) ?: emptyMedia()
                }
            }
        }
        this.media = media
        if (media.name == "No media found") {
            snackString(media.name)
            finish()
            return
        }
        intent.putExtra("media", media as java.io.Serializable)
        intent.putExtra("mediaId", media.id)
        val contract = ActivityResultContracts.OpenDocumentTree()
        launcher = LauncherWrapper(this, contract)

        mediaSingleton = null
        ThemeManager(this).applyTheme(MediaSingleton.bitmap)
        initActivity(this)
        MediaSingleton.bitmap = null

        binding = ActivityMediaBinding.inflate(layoutInflater)
        setContentView(binding.root)
        screenWidth = resources.displayMetrics.widthPixels.toFloat()
        navBar = binding.mediaBottomBar

        supportFragmentManager.addOnBackStackChangedListener {
            }

        // Ui init


        binding.mediaBottomBarContainer?.setPadding(0, 0, 0, navBarHeight)

        // Only the info page remains, so there is no bottom tab bar to show
        navBar.visibility = View.GONE
        binding.mediaBottomBarContainer?.visibility = View.GONE
        binding.mediaBanner.updateLayoutParams { height += statusBarHeight }
        binding.mediaBannerNoKen.updateLayoutParams { height += statusBarHeight }
        binding.mediaClose.updateLayoutParams<ViewGroup.MarginLayoutParams> { topMargin += statusBarHeight }
        binding.mediaHome.updateLayoutParams<ViewGroup.MarginLayoutParams> { topMargin += statusBarHeight }
        binding.incognito.updateLayoutParams<ViewGroup.MarginLayoutParams> { topMargin += statusBarHeight }
        binding.mediaCollapsing.minimumHeight = statusBarHeight

        binding.mediaTitle.isSelected = true

        mMaxScrollSize = binding.mediaAppBar.totalScrollRange
        binding.mediaAppBar.addOnOffsetChangedListener(this)

        binding.mediaHome.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra("goToHome", true)
            }
            startActivity(intent)
            finish()
        }

        binding.mediaClose.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        val bannerAnimations: Boolean = PrefManager.getVal(PrefName.BannerAnimations)
        if (bannerAnimations) {
            val adi = AccelerateDecelerateInterpolator()
            val generator = RandomTransitionGenerator(
                (10000 + 15000 * ((PrefManager.getVal(PrefName.AnimationSpeed) as Float))).toLong(),
                adi
            )
            binding.mediaBanner.setTransitionGenerator(generator)
        }
        val banner =
            if (bannerAnimations) binding.mediaBanner else binding.mediaBannerNoKen
        val viewPager = binding.mediaViewPager
        viewPager.offscreenPageLimit = 2
        viewPager.isUserInputEnabled = false
        viewPager.setPageTransformer(ZoomOutPageTransformer())


        media.selected = model.loadSelected(media)

        binding.mediaCoverImage.loadImage(media.cover)
        binding.mediaCoverImage.setOnLongClickListener {
            val coverTitle = getString(R.string.cover, media.userPreferredName)
            ImageViewDialog.newInstance(
                this,
                coverTitle,
                media.cover
            )
        }

        blurImage(banner, media.banner ?: media.cover)
        val gestureDetector = GestureDetector(this, object : GesturesListener() {
            override fun onDoubleClick(event: MotionEvent) {
                if (!(PrefManager.getVal(PrefName.BannerAnimations) as Boolean))
                    snackString(getString(R.string.enable_banner_animations))
                else {
                    binding.mediaBanner.restart()
                    binding.mediaBanner.performClick()
                }
            }

            override fun onLongClick(event: MotionEvent) {
                val bannerTitle = getString(R.string.banner, media.userPreferredName)
                ImageViewDialog.newInstance(
                    this@MediaDetailsActivity,
                    bannerTitle,
                    media.banner ?: media.cover
                )
                banner.performClick()
            }
        })
        banner.setOnTouchListener { _, motionEvent -> gestureDetector.onTouchEvent(motionEvent);true }
        if (PrefManager.getVal(PrefName.Incognito)) {
            val mediaTitle = "    ${media.userPreferredName}"
            binding.mediaTitle.text = mediaTitle
            binding.incognito.visibility = View.VISIBLE
        } else {
            binding.mediaTitle.text = media.userPreferredName
        }
        binding.mediaTitle.setOnLongClickListener {
            copyToClipboard(media.userPreferredName)
            true
        }
        binding.mediaTitleCollapse.text = media.userPreferredName
        binding.mediaTitleCollapse.setOnLongClickListener {
            copyToClipboard(media.userPreferredName)
            true
        }
        binding.mediaStatus.text = media.status ?: ""
        val rescueMode: Boolean = PrefManager.getVal(PrefName.RescueMode)

        fun fav(media: Media):  PopImageButton? {
            //Fav Button
            return if (Anilist.userid != null && !rescueMode) {
                if (media.isFav) binding.mediaFav.setImageDrawable(
                    AppCompatResources.getDrawable(
                        this,
                        R.drawable.ic_round_favorite_24
                    )
                )

                PopImageButton(
                    scope,
                    binding.mediaFav,
                    R.drawable.ic_round_favorite_24,
                    R.drawable.ic_round_favorite_border_24,
                    R.color.bg_opp,
                    R.color.violet_400,
                    media.isFav
                ) {
                    media.isFav = it
                    Anilist.mutation.toggleFav(media.anime != null, media.id)
                    Refresh.all()
                }
            } else {
                binding.mediaFav.visibility = View.GONE
                null
            }
        }
        var isFavSyncRunning = false
        fun syncMediaFavStateIfNeeded(favButton: PopImageButton?) {
            if (rescueMode || Anilist.userid == null || favButton == null || media.isFav || isFavSyncRunning) return
            isFavSyncRunning = true
            scope.launch {
                try {
                    val favType = if (media.anime != null) {
                        ani.arkhime.com.connections.anilist.AnilistMutations.FavType.ANIME
                    } else {
                        ani.arkhime.com.connections.anilist.AnilistMutations.FavType.MANGA
                    }
                    val isUserFav = withContext(Dispatchers.IO) {
                        Anilist.query.isUserFav(favType, media.id)
                    }
                    if (isUserFav) {
                        media.isFav = true
                        if (!favButton.clicked) favButton.clicked()
                    }
                } finally {
                    isFavSyncRunning = false
                }
            }
        }

        @SuppressLint("ResourceType")
        fun total() {
            val text = SpannableStringBuilder().apply {

                val white =
                    this@MediaDetailsActivity.getThemeColor(com.google.android.material.R.attr.colorOnBackground)
                if (media.userStatus != null) {
                    append(if (media.anime != null) getString(R.string.watched_num) else getString(R.string.read_num))
                    val colorSecondary =
                        getThemeColor(com.google.android.material.R.attr.colorSecondary)
                    bold { color(colorSecondary) { append("${media.userProgress}") } }
                    append(
                        if (media.anime != null) getString(R.string.episodes_out_of) else getString(
                            R.string.chapters_out_of
                        )
                    )
                } else {
                    append(
                        if (media.anime != null) getString(R.string.episodes_total_of) else getString(
                            R.string.chapters_total_of
                        )
                    )
                }
                if (media.anime != null) {
                    if (media.anime!!.nextAiringEpisode != null) {
                        bold { color(white) { append("${media.anime!!.nextAiringEpisode}") } }
                        append(" / ")
                    }
                    bold { color(white) { append("${media.anime!!.totalEpisodes ?: "??"}") } }
                } else
                    bold { color(white) { append("${media.manga!!.totalChapters ?: "??"}") } }
            }
            binding.mediaTotal.text = text
        }

        fun progress() {
            val statuses: Array<String> = resources.getStringArray(R.array.status)
            val statusStrings =
                if (media.manga == null) resources.getStringArray(R.array.status_anime) else resources.getStringArray(
                    R.array.status_manga
                )
            val userStatus =
                if (media.userStatus != null) statusStrings[statuses.indexOf(media.userStatus).coerceAtLeast(0)] else statusStrings[0]

            if (media.userStatus != null) {
                binding.mediaTotal.visibility = View.VISIBLE
                binding.mediaAddToList.text = userStatus
            } else {
                binding.mediaAddToList.setText(R.string.add_list)
            }
            total()
            binding.mediaAddToList.setOnClickListener {
                if (rescueMode) {
                    if (MAL.token != null) {
                        if (supportFragmentManager.findFragmentByTag("dialog") == null)
                            MediaListDialogFragment().show(supportFragmentManager, "dialog")
                    } else snackString("Please login to MAL")
                } else if (Anilist.userid != null) {
                    if (supportFragmentManager.findFragmentByTag("dialog") == null)
                        MediaListDialogFragment().show(supportFragmentManager, "dialog")
                } else snackString(getString(R.string.please_login_anilist))
            }
            binding.mediaAddToList.setOnLongClickListener {
                PrefManager.setCustomVal(
                    "${media.id}_progressDialog",
                    true,
                )
                snackString(getString(R.string.auto_update_reset))
                true
            }
        }
        progress()

        model.getMedia().observe(this) {
            if (it != null) {
                val oldId = media.id
                media = it


                scope.launch {
                    val favIcon = fav(it)
                    syncMediaFavStateIfNeeded(favIcon)
                    if (media.isFav != favIcon?.clicked) favIcon?.clicked()
                }


                binding.mediaNotify.setOnClickListener {
                    val i = Intent(Intent.ACTION_SEND)
                    i.type = "text/plain"
                    i.putExtra(Intent.EXTRA_TEXT, media.shareLink)
                    startActivity(Intent.createChooser(i, media.userPreferredName))
                }
                binding.mediaNotify.setOnLongClickListener {
                    openLinkInBrowser(media.shareLink)
                    true
                }
                binding.mediaCover.setOnClickListener {
                    openLinkInBrowser(media.shareLink)
                }

                if (oldId == 0 && media.id != 0) {
                    if (media.format?.startsWith("LOCAL") == true) {
                        binding.mediaCoverImage.loadImage(media.cover)
                        blurImage(if (bannerAnimations) binding.mediaBanner else binding.mediaBannerNoKen, media.banner ?: media.cover)
                    }
                }
                progress()
            }
        }
        adult = media.isAdult
        if (media.manga != null) anime = false
        viewPager.adapter = ViewPagerAdapter(supportFragmentManager, lifecycle)
        selected = 0
        binding.mediaTitle.translationX = -screenWidth
        binding.commentInputLayout.isVisible = false

        val live = Refresh.activity.getOrPut(this.hashCode()) { MutableLiveData(true) }
        live.observe(this) {
            if (it) {
                scope.launch(Dispatchers.IO) {
                    model.loadMedia(media)
                    live.postValue(false)
                }
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (!::navBar.isInitialized) return
        val rightMargin = if (resources.configuration.orientation ==
            Configuration.ORIENTATION_LANDSCAPE
        ) navBarHeight else 0
        val bottomMargin = if (resources.configuration.orientation ==
            Configuration.ORIENTATION_LANDSCAPE
        ) 0 else navBarHeight
        val params: ViewGroup.MarginLayoutParams =
            navBar.layoutParams as ViewGroup.MarginLayoutParams
        params.updateMargins(right = rightMargin, bottom = bottomMargin)
    }

    override fun onResume() {
        super.onResume()
        if (!::binding.isInitialized) return
        binding.root.requestLayout()
    }

    // ViewPager: the info page is the only page
    private class ViewPagerAdapter(fragmentManager: FragmentManager, lifecycle: Lifecycle) :
        FragmentStateAdapter(fragmentManager, lifecycle) {
        override fun getItemCount(): Int = 1
        override fun createFragment(position: Int): Fragment = MediaInfoFragment()
    }

    //Collapsing UI Stuff
    private var isCollapsed = false
    private val percent = 45
    private var mMaxScrollSize = 0
    private var screenWidth: Float = 0f

    override fun onOffsetChanged(appBar: AppBarLayout, i: Int) {
        if (!::binding.isInitialized) return
        if (mMaxScrollSize == 0) mMaxScrollSize = appBar.totalScrollRange
        val percentage = abs(i) * 100 / mMaxScrollSize

        binding.mediaCover.visibility =
            if (binding.mediaCover.scaleX == 0f) View.GONE else View.VISIBLE
        val duration = (200 * (PrefManager.getVal(PrefName.AnimationSpeed) as Float)).toLong()
        if (percentage >= percent && !isCollapsed) {
            isCollapsed = true
            ObjectAnimator.ofFloat(binding.mediaTitle, "translationX", 0f).setDuration(duration)
                .start()
            ObjectAnimator.ofFloat(binding.mediaAccessContainer, "translationX", screenWidth)
                .setDuration(duration).start()
            ObjectAnimator.ofFloat(binding.mediaCover, "translationX", screenWidth)
                .setDuration(duration).start()
            ObjectAnimator.ofFloat(binding.mediaCollapseContainer, "translationX", screenWidth)
                .setDuration(duration).start()
            binding.mediaBanner.pause()
        }
        if (percentage <= percent && isCollapsed) {
            isCollapsed = false
            ObjectAnimator.ofFloat(binding.mediaTitle, "translationX", -screenWidth)
                .setDuration(duration).start()
            ObjectAnimator.ofFloat(binding.mediaAccessContainer, "translationX", 0f)
                .setDuration(duration).start()
            ObjectAnimator.ofFloat(binding.mediaCover, "translationX", 0f).setDuration(duration)
                .start()
            ObjectAnimator.ofFloat(binding.mediaCollapseContainer, "translationX", 0f)
                .setDuration(duration).start()
            if (PrefManager.getVal(PrefName.BannerAnimations)) binding.mediaBanner.resume()
        }
        if (percentage == 1 && model.scrolledToTop.value != false) model.scrolledToTop.postValue(
            false
        )
        if (percentage == 0 && model.scrolledToTop.value != true) model.scrolledToTop.postValue(true)
    }

    class PopImageButton(
        private val scope: CoroutineScope,
        private val image: ImageView,
        private val d1: Int,
        private val d2: Int,
        private val c1: Int,
        private val c2: Int,
        var clicked: Boolean,
        needsInitialClick: Boolean = false,
        callback: suspend (Boolean) -> (Unit)
    ) {
        private var disabled = false
        private val context = image.context
        private var pressable = true

        fun setState(state: Boolean) {
            if (clicked != state) {
                clicked = state
                scope.launch {
                    clicked()
                }
            }
        }

        init {
            enabled(true)
            if (needsInitialClick) {
                scope.launch {
                    clicked()
                }
            }
            image.setOnClickListener {
                if (pressable && !disabled) {
                    pressable = false
                    clicked = !clicked
                    scope.launch {
                        launch(Dispatchers.IO) {
                            callback.invoke(clicked)
                        }
                        clicked()
                        pressable = true
                    }
                }
            }
        }

        suspend fun clicked() {
            ObjectAnimator.ofFloat(image, "scaleX", 1f, 0f).setDuration(69).start()
            ObjectAnimator.ofFloat(image, "scaleY", 1f, 0f).setDuration(100).start()
            delay(100.milliseconds)

            if (clicked) {
                ObjectAnimator.ofArgb(
                    image,
                    "ColorFilter",
                    ContextCompat.getColor(context, c1),
                    ContextCompat.getColor(context, c2)
                ).setDuration(120).start()
                image.setImageDrawable(AppCompatResources.getDrawable(context, d1))
            } else image.setImageDrawable(AppCompatResources.getDrawable(context, d2))
            ObjectAnimator.ofFloat(image, "scaleX", 0f, 1.5f).setDuration(120).start()
            ObjectAnimator.ofFloat(image, "scaleY", 0f, 1.5f).setDuration(100).start()
            delay(120.milliseconds)
            ObjectAnimator.ofFloat(image, "scaleX", 1.5f, 1f).setDuration(100).start()
            ObjectAnimator.ofFloat(image, "scaleY", 1.5f, 1f).setDuration(100).start()
            delay(200.milliseconds)
            if (clicked) {
                ObjectAnimator.ofArgb(
                    image,
                    "ColorFilter",
                    ContextCompat.getColor(context, c2),
                    ContextCompat.getColor(context, c1)
                ).setDuration(200).start()
            }
        }

        fun enabled(enabled: Boolean) {
            disabled = !enabled
            image.alpha = if (disabled) 0.33f else 1f
        }
    }

    override fun onProvideAssistContent(outContent: android.app.assist.AssistContent?) {
        super.onProvideAssistContent(outContent)
        outContent?.intent = null
    }

    override fun onDestroy() {
        super.onDestroy()
        Refresh.activity.remove(this.hashCode())
        mediaSingleton = null
        MediaSingleton.bitmap = null
        MediaSingleton.media = null
        if (::binding.isInitialized) {
            binding.mediaViewPager.adapter = null
        }
    }

    companion object {
        var mediaSingleton: Media? = null
        private val mediaCache = android.util.LruCache<Int, Media>(10)
    }
}
