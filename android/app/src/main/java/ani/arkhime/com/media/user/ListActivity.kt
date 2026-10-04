package ani.arkhime.com.media.user

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.inputmethod.InputMethodManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import ani.arkhime.com.R
import ani.arkhime.com.Refresh
import ani.arkhime.com.connections.anilist.Anilist
import ani.arkhime.com.databinding.ActivityListBinding
import ani.arkhime.com.getThemeColor
import ani.arkhime.com.hideSystemBarsExtendView
import ani.arkhime.com.settings.saving.PrefManager
import ani.arkhime.com.settings.saving.PrefName
import ani.arkhime.com.snackString
import ani.arkhime.com.statusBarHeight
import ani.arkhime.com.util.customAlertDialog
import ani.arkhime.com.themes.ThemeManager
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class ListActivity : AppCompatActivity() {
    private lateinit var binding: ActivityListBinding
    private val scope = lifecycleScope
    private var selectedTabIdx = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ThemeManager(this).applyTheme()
        binding = ActivityListBinding.inflate(layoutInflater)

        val primaryColor = getThemeColor(com.google.android.material.R.attr.colorSurface)
        val primaryTextColor = getThemeColor(androidx.appcompat.R.attr.colorPrimary)
        val secondaryTextColor = getThemeColor(com.google.android.material.R.attr.colorOutline)

        window.statusBarColor = primaryColor
        window.navigationBarColor = primaryColor
        binding.listed.visibility = View.GONE
        binding.listTabLayout.setBackgroundColor(primaryColor)
        binding.listAppBar.setBackgroundColor(primaryColor)
        binding.listTitle.setTextColor(primaryTextColor)
        binding.listTabLayout.setTabTextColors(secondaryTextColor, primaryTextColor)
        binding.listTabLayout.setSelectedTabIndicatorColor(primaryTextColor)
        if (!PrefManager.getVal<Boolean>(PrefName.ImmersiveMode)) {
            this.window.statusBarColor =
                ContextCompat.getColor(this, R.color.nav_bg_inv)
            binding.root.fitsSystemWindows = true

        } else {
            binding.root.fitsSystemWindows = false
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            hideSystemBarsExtendView()
            binding.settingsContainer.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = statusBarHeight
            }
        }
        setContentView(binding.root)

        val anime = intent.getBooleanExtra("anime", true)
        binding.listTitle.text = getString(
            R.string.user_list, intent.getStringExtra("username"),
            if (anime) getString(R.string.anime) else getString(R.string.manga)
        )
        binding.listTabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                this@ListActivity.selectedTabIdx = tab?.position ?: 0
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        val model: ListViewModel by viewModels()
        model.getLists().observe(this) {
            val defaultKeys = listOf(
                "Reading",
                "Watching",
                "Completed",
                "Paused",
                "Dropped",
                "Planning",
                "Favourites",
                "Rewatching",
                "Rereading",
                "All"
            )
            val userKeys: Array<String> = resources.getStringArray(R.array.keys)

            if (it != null) {
                binding.listProgressBar.visibility = View.GONE
                binding.listViewPager.adapter = ListViewPagerAdapter(it.size, false, this)
                val keys = it.keys.toList()
                    .map { key -> userKeys.getOrNull(defaultKeys.indexOf(key)) ?: key }
                val values = it.values.toList()
                val savedTab = this.selectedTabIdx
                TabLayoutMediator(binding.listTabLayout, binding.listViewPager) { tab, position ->
                    tab.text = "${keys[position]} (${values[position].size})"
                }.attach()
                binding.listViewPager.setCurrentItem(savedTab, false)
            }
        }

        val live = Refresh.activity.getOrPut(this.hashCode()) { MutableLiveData(true) }
        live.observe(this) {
            if (it) {
                scope.launch {
                    withContext(Dispatchers.IO) {
                        model.loadLists(
                            anime,
                            intent.getIntExtra("userId", 0)
                        )
                    }
                    live.postValue(false)
                }
            }
        }

        if (PrefManager.getVal<Boolean>(PrefName.RescueMode)) {
            binding.listSort.visibility = View.GONE
        }
        binding.listSort.setOnClickListener {
            val popup = PopupMenu(this, it)
            popup.setOnMenuItemClickListener { item ->
                val sort = when (item.itemId) {
                    R.id.score -> "score"
                    R.id.title -> "title"
                    R.id.updated -> "updatedAt"
                    R.id.release -> "release"
                    else -> null
                }
                PrefManager.setVal(
                    if (anime) PrefName.AnimeListSortOrder else PrefName.MangaListSortOrder,
                    sort ?: ""
                )
                binding.listProgressBar.visibility = View.VISIBLE
                binding.listViewPager.adapter = null
                scope.launch {
                    withContext(Dispatchers.IO) {
                        model.loadLists(
                            anime,
                            intent.getIntExtra("userId", 0),
                            sort
                        )
                    }
                }
                true
            }
            popup.inflate(R.menu.list_sort_menu)
            popup.show()
        }

        binding.filter.setOnClickListener {
            val popup = PopupMenu(this, it)
            popup.menu.add(Menu.NONE, 0, Menu.NONE, "All")

            val genres = model.getAllGenres()
            if (genres.isNotEmpty()) {
                val genreSubMenu = popup.menu.addSubMenu("Filter by Genre")
                genres.forEachIndexed { index, genre ->
                    genreSubMenu.add(1, index + 1, Menu.NONE, genre)
                }
            }

            val tags = model.getAllTags()
            if (tags.isNotEmpty()) {
                val tagSubMenu = popup.menu.addSubMenu("Filter by Tag")
                tags.forEachIndexed { index, tag ->
                    tagSubMenu.add(2, index + 10000, Menu.NONE, tag)
                }
            }

            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.groupId) {
                    0 -> model.unfilterLists()
                    1 -> model.filterLists(menuItem.title.toString())
                    2 -> model.filterListsByTag(menuItem.title.toString())
                    else -> {
                        if (menuItem.title == "All") model.unfilterLists()
                    }
                }
                true
            }
            popup.show()
        }

        binding.random.setOnClickListener {
            //get the current tab
            val currentTab =
                binding.listTabLayout.getTabAt(binding.listTabLayout.selectedTabPosition)
            val currentFragment =
                supportFragmentManager.findFragmentByTag("f" + currentTab?.position.toString()) as? ListFragment
            currentFragment?.randomOptionClick()
        }

        setupSeriesAndEditing(model, anime, primaryTextColor)

        binding.search.setOnClickListener {
            toggleSearchView(binding.searchView.isVisible)
            if (!binding.searchView.isVisible) {
                model.unfilterLists()
            }
        }

        binding.searchViewText.addTextChangedListener {
            model.searchLists(binding.searchViewText.text.toString())
        }
    }

    /** Group-by-series toggle, batch edit mode and its save bar */
    private fun setupSeriesAndEditing(model: ListViewModel, anime: Boolean, activeColor: Int) {
        val idleColor = getThemeColor(com.google.android.material.R.attr.colorOnBackground)
        fun tint(button: android.widget.ImageButton, active: Boolean) {
            button.imageTintList = ColorStateList.valueOf(if (active) activeColor else idleColor)
        }
        fun reload() {
            Refresh.activity[this.hashCode()]?.postValue(true)
        }

        val rescueMode: Boolean = PrefManager.getVal(PrefName.RescueMode)
        val ownList = intent.getIntExtra("userId", 0).let { it != 0 && it == Anilist.userid }
        binding.listGroup.isVisible = !rescueMode
        binding.listEdit.isVisible = !rescueMode && ownList

        model.groupBySeries.observe(this) { tint(binding.listGroup, it) }
        binding.listGroup.setOnClickListener {
            val grouped = model.groupBySeries.value != true
            PrefManager.setVal(PrefName.ListGroupBySeries, grouped)
            model.groupBySeries.value = grouped
            snackString(getString(if (grouped) R.string.series_grouped else R.string.series_ungrouped))
            binding.listProgressBar.visibility = View.VISIBLE
            reload()
        }

        model.editMode.observe(this) { tint(binding.listEdit, it) }
        binding.listEdit.setOnClickListener {
            if (model.editMode.value == true) {
                confirmDiscard(model) { model.editMode.value = false }
            } else {
                model.editMode.value = true
                snackString(getString(R.string.series_edit_on))
            }
        }

        val backGuard = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                confirmDiscard(model) { finish() }
            }
        }
        onBackPressedDispatcher.addCallback(this, backGuard)

        fun renderBar() {
            val count = model.edits.value.orEmpty().size
            val progress = model.saveProgress.value
            backGuard.isEnabled = count > 0
            binding.listSaveBar.isVisible = count > 0 || progress != null
            binding.listSaveProgress.isVisible = progress != null
            binding.listSave.isEnabled = progress == null
            binding.listDiscard.isEnabled = progress == null
            if (progress != null) {
                binding.listSaveProgress.max = progress.second.coerceAtLeast(1)
                binding.listSaveProgress.setProgressCompat(progress.first, true)
                binding.listSaveText.text = getString(R.string.series_saving, progress.first, progress.second)
            } else {
                binding.listSaveText.text = resources.getQuantityString(R.plurals.series_changes, count, count)
            }
        }
        model.edits.observe(this) { renderBar() }
        model.saveProgress.observe(this) { renderBar() }

        binding.listDiscard.setOnClickListener { model.discardEdits() }
        binding.listSave.setOnClickListener {
            scope.launch {
                val (saved, failed) = withContext(Dispatchers.IO) { model.saveEdits() }
                snackString(
                    if (failed == 0) getString(R.string.series_saved, saved)
                    else getString(R.string.series_saved_failed, saved, failed)
                )
                if (saved > 0) reload()
            }
        }
    }

    private fun confirmDiscard(model: ListViewModel, then: () -> Unit) {
        val count = model.edits.value.orEmpty().size
        if (count == 0) {
            then()
            return
        }
        customAlertDialog().apply {
            setTitle(getString(R.string.series_discard_title, count))
            setPosButton(getString(R.string.series_discard)) {
                model.discardEdits()
                then()
            }
            setNegButton(getString(R.string.series_keep_editing))
        }.show()
    }

    private fun toggleSearchView(isVisible: Boolean) {
        if (isVisible) {
            binding.searchView.visibility = View.GONE
            binding.searchViewText.text.clear()
        } else {
            binding.searchView.visibility = View.VISIBLE
            binding.searchViewText.requestFocus()
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(binding.searchViewText, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Refresh.activity.remove(this.hashCode())
        if (::binding.isInitialized) {
            binding.listViewPager.adapter = null
        }
    }
}
