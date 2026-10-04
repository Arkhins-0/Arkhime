package ani.arkhime.com.settings

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.LinearLayoutManager
import ani.arkhime.com.R
import ani.arkhime.com.databinding.ActivitySettingsAnimeBinding
import ani.arkhime.com.initActivity
import ani.arkhime.com.media.MediaType
import ani.arkhime.com.navBarHeight
import ani.arkhime.com.restartApp
import ani.arkhime.com.settings.saving.PrefManager
import ani.arkhime.com.settings.saving.PrefName
import ani.arkhime.com.statusBarHeight
import ani.arkhime.com.themes.ThemeManager
import ani.arkhime.com.util.customAlertDialog
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class SettingsAnimeActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsAnimeBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager(this).applyTheme()
        initActivity(this)
        val context = this
        binding = ActivitySettingsAnimeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.apply {

            settingsAnimeLayout.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = statusBarHeight
                bottomMargin = navBarHeight
            }
            animeSettingsBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
            val highlightKey = intent.getStringExtra(ani.arkhime.com.settings.search.SettingsSearchAdapter.EXTRA_HIGHLIGHT_KEY)
            settingsRecyclerView.adapter = SettingsAdapter(
                arrayListOf(
                    Settings(
                        type = 2,
                        name = getString(R.string.show_yt),
                        desc = getString(R.string.show_yt_desc),
                        icon = R.drawable.ic_round_play_circle_24,
                        isChecked = PrefManager.getVal(PrefName.ShowYtButton),
                        switch = { isChecked, _ ->
                            PrefManager.setVal(PrefName.ShowYtButton, isChecked)
                        }
                    ),
                    Settings(
                        type = 2,
                        name = getString(R.string.include_list),
                        desc = getString(R.string.include_list_anime_desc),
                        icon = R.drawable.view_list_24,
                        isChecked = PrefManager.getVal(PrefName.IncludeAnimeList),
                        switch = { isChecked, _ ->
                            PrefManager.setVal(PrefName.IncludeAnimeList, isChecked)
                            restartApp()
                        }
                    ),
                ),
                highlightKey = highlightKey
            )
            settingsRecyclerView.apply {
                layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
                setHasFixedSize(true)
            }

            var previousEp: View = when (PrefManager.getVal<Int>(PrefName.AnimeDefaultView)) {
                0 -> settingsEpList
                1 -> settingsEpGrid
                2 -> settingsEpCompact
                else -> settingsEpList
            }
            previousEp.alpha = 1f
            fun uiEp(mode: Int, current: View) {
                previousEp.alpha = 0.33f
                previousEp = current
                current.alpha = 1f
                PrefManager.setVal(PrefName.AnimeDefaultView, mode)
            }

            settingsEpList.setOnClickListener {
                uiEp(0, it)
            }

            settingsEpGrid.setOnClickListener {
                uiEp(1, it)
            }

            settingsEpCompact.setOnClickListener {
                uiEp(2, it)
            }

        }
    }
}

object ResolutionPriorityDialog {
    val ALL_RESOLUTIONS = listOf("1080p", "720p", "480p", "360p", "240p", "144p")

    fun show(
        context: android.content.Context,
        extensionName: String? = null,
        onUpdated: ((List<String>) -> Unit)? = null
    ) {
        val currentResolutions = PrefManager.getPreferredDownloadResolutions(extensionName).toMutableList()
        val fullList = currentResolutions + ALL_RESOLUTIONS.filter { it !in currentResolutions }
        val checkedItems = fullList.map { it in currentResolutions }.toBooleanArray()

        context.customAlertDialog().apply {
            setTitle(R.string.preferred_download_resolutions)
            multiChoiceItems(fullList.toTypedArray(), checkedItems) { updatedSelection ->
                currentResolutions.clear()
                fullList.forEachIndexed { index, res ->
                    if (updatedSelection[index]) {
                        currentResolutions.add(res)
                    }
                }
            }
            setPosButton(R.string.ok) {
                val toSave = if (currentResolutions.isEmpty()) ALL_RESOLUTIONS else currentResolutions
                PrefManager.setPreferredDownloadResolutions(extensionName, toSave)
                onUpdated?.invoke(toSave)
            }
            setNeutralButton(R.string.reset) {
                PrefManager.setPreferredDownloadResolutions(extensionName, ALL_RESOLUTIONS)
                onUpdated?.invoke(ALL_RESOLUTIONS)
            }
            setNegButton(R.string.cancel)
            show()
        }
    }
}
