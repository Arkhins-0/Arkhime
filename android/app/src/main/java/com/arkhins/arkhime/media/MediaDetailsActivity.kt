package com.arkhins.arkhime.media

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
import com.arkhins.arkhime.GesturesListener
import com.arkhins.arkhime.MainActivity
import com.arkhins.arkhime.R
import com.arkhins.arkhime.Refresh
import com.arkhins.arkhime.ZoomOutPageTransformer
import com.arkhins.arkhime.blurImage
import com.arkhins.arkhime.connections.anilist.Anilist
import com.arkhins.arkhime.connections.mal.MAL
import com.arkhins.arkhime.copyToClipboard
import com.arkhins.arkhime.databinding.ActivityMediaBinding
import com.arkhins.arkhime.getThemeColor
import com.arkhins.arkhime.initActivity
import com.arkhins.arkhime.loadImage
import com.arkhins.arkhime.navBarHeight
import com.arkhins.arkhime.openLinkInBrowser
import com.arkhins.arkhime.others.AndroidBug5497Workaround
import com.arkhins.arkhime.others.ImageViewDialog
import com.arkhins.arkhime.others.getSerialized
import com.arkhins.arkhime.settings.saving.PrefManager
import com.arkhins.arkhime.settings.saving.PrefName
import com.arkhins.arkhime.snackString
import com.arkhins.arkhime.statusBarHeight
import com.arkhins.arkhime.themes.ThemeManager
import com.arkhins.arkhime.util.LauncherWrapper
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
import com.arkhins.arkhime.connections.comments.CommentsAPI
import com.arkhins.arkhime.media.anime.AnimeWatchFragment
import com.arkhins.arkhime.media.comments.CommentsFragment
import com.arkhins.arkhime.media.manga.MangaReadFragment
import com.arkhins.arkhime.media.novel.NovelReadFragment


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


        val isDownload = intent.getBooleanExtra("download", false)
        media.selected = model.loadSelected(media, isDownload)

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
                        com.arkhins.arkhime.connections.anilist.AnilistMutations.FavType.ANIME
                    } else {
                        com.arkhins.arkhime.connections.anilist.AnilistMutations.FavType.MANGA
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
        // Tabs restored from Dantotsu: Info / Watch (or Read) / Comments
        val mediaType = when {
            media.anime != null -> SupportedMedia.ANIME
            media.format == "NOVEL" -> SupportedMedia.NOVEL
            else -> SupportedMedia.MANGA
        }
        if (media.manga != null) anime = false
        // Comments only show once Arkhime's own comments server is configured
        val showComments = CommentsAPI.isConfigured &&
            PrefManager.getVal<Int>(PrefName.CommentsEnabled) == 1
        tabs = buildList {
            add(MediaTab.INFO)
            add(MediaTab.WATCH)
            if (mediaType != SupportedMedia.NOVEL) add(MediaTab.RELEASES)
            add(MediaTab.DISCUSS)
            if (showComments) add(MediaTab.COMMENTS)
        }
        viewPager.adapter = ViewPagerAdapter(
            supportFragmentManager,
            lifecycle,
            mediaType,
            media,
            intent.getIntExtra("commentId", -1),
            tabs
        )
        selected = media.selected!!.window.coerceIn(0, tabs.lastIndex)
        binding.mediaTitle.translationX = -screenWidth

        val infoTab = navBar.createTab(R.drawable.ic_round_info_24, R.string.info, R.id.info)
        val watchTab = when (mediaType) {
            SupportedMedia.ANIME ->
                navBar.createTab(R.drawable.ic_round_movie_filter_24, R.string.watch, R.id.watch)
            SupportedMedia.NOVEL ->
                navBar.createTab(R.drawable.ic_round_book_24, R.string.read, R.id.read)
            SupportedMedia.MANGA ->
                navBar.createTab(R.drawable.ic_round_import_contacts_24, R.string.read, R.id.read)
        }
        tabs.forEach { tab ->
            navBar.addTab(
                when (tab) {
                    MediaTab.INFO -> infoTab
                    MediaTab.WATCH -> watchTab
                    MediaTab.RELEASES ->
                        navBar.createTab(R.drawable.ic_round_magnet_24, R.string.releases, R.id.releases)
                    MediaTab.DISCUSS ->
                        navBar.createTab(R.drawable.ic_round_forum_24, R.string.forum, R.id.discuss)
                    MediaTab.COMMENTS ->
                        navBar.createTab(R.drawable.ic_round_comment_24, R.string.comments, R.id.comment)
                }
            )
        }
        val commentsIndex = tabs.indexOf(MediaTab.COMMENTS)
        if (model.continueMedia == null && media.cameFromContinue) {
            model.continueMedia = PrefManager.getVal(PrefName.ContinueMedia)
            selected = 1
        }
        if (commentsIndex != -1 && intent.getStringExtra("FRAGMENT_TO_LOAD") != null) selected = commentsIndex
        if (viewPager.currentItem != selected) viewPager.post {
            viewPager.setCurrentItem(selected, false)
        }
        binding.commentInputLayout.isVisible = selected == commentsIndex
        navBar.selectTabAt(selected)
        navBar.setOnTabSelectListener(object : AnimatedBottomBar.OnTabSelectListener {
            override fun onTabSelected(
                lastIndex: Int,
                lastTab: AnimatedBottomBar.Tab?,
                newIndex: Int,
                newTab: AnimatedBottomBar.Tab
            ) {
                selected = newIndex
                binding.commentInputLayout.isVisible = selected == commentsIndex
                viewPager.setCurrentItem(selected, true)
                val sel = model.loadSelected(media, isDownload)
                sel.window = selected
                model.saveSelected(media.id, sel)
            }
        })

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
        if (::navBar.isInitialized) navBar.selectTabAt(selected)
        if (!::binding.isInitialized) return
        binding.root.requestLayout()
    }

    private enum class SupportedMedia {
        ANIME, MANGA, NOVEL
    }

    private enum class MediaTab { INFO, WATCH, RELEASES, DISCUSS, COMMENTS }

    private var tabs: List<MediaTab> = emptyList()

    // ViewPager: Info / Watch or Read / Comments (restored from Dantotsu)
    private class ViewPagerAdapter(
        fragmentManager: FragmentManager,
        lifecycle: Lifecycle,
        private val mediaType: SupportedMedia,
        private val media: Media,
        private val commentId: Int,
        private val tabs: List<MediaTab>
    ) : FragmentStateAdapter(fragmentManager, lifecycle) {

        override fun getItemCount(): Int = tabs.size

        override fun createFragment(position: Int): Fragment = when (tabs.getOrNull(position)) {
            MediaTab.INFO -> MediaInfoFragment()
            MediaTab.WATCH -> when (mediaType) {
                SupportedMedia.ANIME -> AnimeWatchFragment()
                SupportedMedia.MANGA -> MangaReadFragment()
                SupportedMedia.NOVEL -> NovelReadFragment()
            }
            MediaTab.RELEASES -> MediaReleasesFragment()
            MediaTab.DISCUSS -> MediaDiscussFragment()
            MediaTab.COMMENTS -> CommentsFragment().apply {
                arguments = Bundle().apply {
                    putInt("mediaId", media.id)
                    putString("mediaName", media.mainName())
                    if (commentId != -1) putInt("commentId", commentId)
                }
            }
            null -> MediaInfoFragment()
        }
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
