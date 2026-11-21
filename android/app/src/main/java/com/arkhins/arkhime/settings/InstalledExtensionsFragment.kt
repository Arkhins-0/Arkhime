package com.arkhins.arkhime.settings

import android.app.NotificationManager
import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.arkhins.arkhime.R
import com.arkhins.arkhime.connections.crashlytics.CrashlyticsInterface
import com.arkhins.arkhime.databinding.FragmentExtensionsBinding
import com.arkhins.arkhime.others.LanguageMapper.Companion.getLanguageName
import com.arkhins.arkhime.settings.saving.PrefManager
import com.arkhins.arkhime.settings.saving.PrefName
import com.arkhins.arkhime.snackString
import com.arkhins.arkhime.util.Logger
import com.arkhins.arkhime.util.customAlertDialog
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputLayout
import eu.kanade.tachiyomi.data.notification.Notifications
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Locale

/**
 * The installed-extensions tab, shared by anime and manga. A subclass supplies its extensions,
 * how to read one, and its settings screen and saved order; the list, drag-to-reorder, search,
 * update and uninstall work the same for both.
 *
 * @param E the installed extension type, like `AnimeExtension.Installed`.
 */
abstract class InstalledExtensionsFragment<E : Any> : Fragment(), SearchQueryHandler {

    /** What the list shows for one extension. */
    protected class Info(
        val name: String,
        val pkgName: String,
        val versionName: String,
        val lang: String,
        val isNsfw: Boolean,
        val hasUpdate: Boolean,
        val icon: Drawable?,
    )

    /** A source with settings: its id and language, shown when an extension has several. */
    protected class Configurable(val id: Long, val lang: String)

    protected abstract val installedExtensions: StateFlow<List<E>>
    protected abstract fun info(extension: E): Info
    protected abstract fun configurableSources(extension: E): List<Configurable>
    protected abstract fun settingsFragment(sourceId: Long, onClose: () -> Unit): Fragment
    protected abstract fun uninstall(extension: E)
    protected abstract fun update(extension: E): Observable<*>

    /** Source names pinned to the top, in the user's order. */
    protected abstract val pinnedSources: List<String>

    /** Saves the order the user dragged the extensions into. */
    protected abstract fun saveOrder(names: List<String>)

    private var _binding: FragmentExtensionsBinding? = null
    private val binding get() = _binding!!
    private lateinit var extensionsRecyclerView: RecyclerView
    private val skipIcons: Boolean = PrefManager.getVal(PrefName.SkipExtensionIcons)

    private val extensionsAdapter by lazy {
        ExtensionsAdapter(
            info = ::info,
            onSettingsClicked = ::openSettings,
            onUninstallClicked = { extension ->
                if (isAdded) {
                    uninstall(extension)
                    snackString("Extension uninstalled")
                }
            },
            onUpdateClicked = ::updateExtension,
            saveOrder = { saveOrder(it) },
            skipIcons = skipIcons,
        )
    }

    private fun openSettings(extension: E) {
        val name = info(extension).name
        val changeUIVisibility: (Boolean) -> Unit = { show ->
            val activity = requireActivity() as ExtensionsActivity
            activity.findViewById<ViewPager2>(R.id.viewPager).isVisible = show
            activity.findViewById<TabLayout>(R.id.tabLayout).isVisible = show
            activity.findViewById<TextInputLayout>(R.id.searchView).isVisible = show
            activity.findViewById<ImageView>(R.id.languageselect).isVisible = show
            activity.findViewById<TextView>(R.id.extensions).text =
                if (show) getString(R.string.extensions) else name
            activity.findViewById<FrameLayout>(R.id.fragmentExtensionsContainer).isGone = show
        }
        val showSettings: (Configurable) -> Unit = { setting ->
            val fragment = settingsFragment(setting.id) { changeUIVisibility(true) }
            parentFragmentManager.beginTransaction()
                .setCustomAnimations(R.anim.slide_up, R.anim.slide_down)
                .replace(R.id.fragmentExtensionsContainer, fragment)
                .addToBackStack(null)
                .commit()
        }

        val allSettings = configurableSources(extension)
        if (allSettings.isEmpty()) {
            Toast.makeText(requireContext(), "Source is not configurable", Toast.LENGTH_SHORT)
                .show()
            return
        }
        if (allSettings.size > 1) {
            var itemSelected = false
            val names = allSettings.map { getLanguageName(it.lang) }.toTypedArray()
            requireContext().customAlertDialog().apply {
                setTitle("Select a Source")
                singleChoiceItems(names, 0) { which ->
                    itemSelected = true
                    showSettings(allSettings[which])
                }
                onDismiss {
                    if (!itemSelected) {
                        changeUIVisibility(true)
                    }
                }
                show()
            }
        } else {
            showSettings(allSettings[0])
        }

        // Hide ViewPager2 and TabLayout
        changeUIVisibility(false)
    }

    private fun updateExtension(extension: E) {
        if (!isAdded) return
        val context = requireContext()
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (!info(extension).hasUpdate) {
            snackString("No update available")
            return
        }
        update(extension)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { installStep ->
                    val builder = NotificationCompat.Builder(
                        context,
                        Notifications.CHANNEL_DOWNLOADER_PROGRESS
                    )
                        .setSmallIcon(R.drawable.ic_round_sync_24)
                        .setContentTitle("Updating extension")
                        .setContentText("Step: $installStep")
                        .setPriority(NotificationCompat.PRIORITY_LOW)
                    notificationManager.notify(1, builder.build())
                },
                { error ->
                    Injekt.get<CrashlyticsInterface>().logException(error)
                    Logger.log(error)
                    val builder = NotificationCompat.Builder(
                        context,
                        Notifications.CHANNEL_DOWNLOADER_ERROR
                    )
                        .setSmallIcon(R.drawable.ic_round_info_24)
                        .setContentTitle("Update failed: ${error.message}")
                        .setContentText("Error: ${error.message}")
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                    notificationManager.notify(1, builder.build())
                    snackString("Update failed: ${error.message}")
                },
                {
                    val builder = NotificationCompat.Builder(
                        context,
                        Notifications.CHANNEL_DOWNLOADER_PROGRESS
                    )
                        .setSmallIcon(R.drawable.ic_circle_check)
                        .setContentTitle("Update complete")
                        .setContentText("The extension has been successfully updated.")
                        .setPriority(NotificationCompat.PRIORITY_LOW)
                    notificationManager.notify(1, builder.build())
                    snackString("Extension updated")
                }
            )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentExtensionsBinding.inflate(inflater, container, false)

        extensionsRecyclerView = binding.allExtensionsRecyclerView
        extensionsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        extensionsRecyclerView.adapter = extensionsAdapter

        val itemTouchHelperCallback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPosition = viewHolder.absoluteAdapterPosition
                val toPosition = target.absoluteAdapterPosition
                val newList = extensionsAdapter.currentList.toMutableList().apply {
                    add(toPosition, removeAt(fromPosition))
                }
                extensionsAdapter.submitList(newList)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    viewHolder?.itemView?.elevation = 8f
                    viewHolder?.itemView?.translationZ = 8f
                }
            }

            override fun clearView(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ) {
                super.clearView(recyclerView, viewHolder)
                extensionsAdapter.updatePref()
                viewHolder.itemView.elevation = 0f
                viewHolder.itemView.translationZ = 0f
            }
        }
        ItemTouchHelper(itemTouchHelperCallback).attachToRecyclerView(extensionsRecyclerView)

        lifecycleScope.launch {
            installedExtensions.collect { extensions ->
                extensionsAdapter.updateData(sortToSourcesList(extensions))
            }
        }
        return binding.root
    }

    /** Pinned sources first, in the saved order, then the rest. */
    private fun sortToSourcesList(input: List<E>): List<E> {
        val pinned = pinnedSources
        val sourcesMap = input.associateBy { info(it).name }
        val orderedSources = pinned.mapNotNull { name -> sourcesMap[name] }
        return orderedSources + input.filter { !pinned.contains(info(it).name) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun updateContentBasedOnQuery(query: String?) {
        extensionsAdapter.filter(query ?: "", sortToSourcesList(installedExtensions.value))
    }

    override fun notifyDataChanged() { // Do nothing
    }

    private class ExtensionsAdapter<E : Any>(
        private val info: (E) -> Info,
        private val onSettingsClicked: (E) -> Unit,
        private val onUninstallClicked: (E) -> Unit,
        private val onUpdateClicked: (E) -> Unit,
        private val saveOrder: (List<String>) -> Unit,
        val skipIcons: Boolean
    ) : ListAdapter<E, ExtensionsAdapter.ViewHolder>(diffCallback(info)) {

        fun updateData(newExtensions: List<E>) {
            submitList(newExtensions)
        }

        fun updatePref() {
            saveOrder(currentList.map { info(it).name })
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_extension, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val extension = getItem(position)
            val info = info(extension)
            val nsfw = if (info.isNsfw) "(18+)" else ""
            val lang = getLanguageName(info.lang)
            holder.extensionNameTextView.text = info.name
            val versionText = "$lang ${info.versionName} $nsfw"
            holder.extensionVersionTextView.text = versionText
            if (!skipIcons) {
                holder.extensionIconImageView.setImageDrawable(info.icon)
            }
            holder.updateView.isVisible = info.hasUpdate
            holder.deleteView.setOnClickListener {
                onUninstallClicked(extension)
            }
            holder.updateView.setOnClickListener {
                onUpdateClicked(extension)
            }
            holder.settingsImageView.setOnClickListener {
                onSettingsClicked(extension)
            }
        }

        fun filter(query: String, currentList: List<E>) {
            val filteredList = currentList.filter {
                info(it).name.lowercase(Locale.ROOT).contains(query.lowercase(Locale.ROOT))
            }
            if (filteredList != currentList)
                submitList(filteredList)
        }

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val extensionNameTextView: TextView = view.findViewById(R.id.extensionNameTextView)
            val extensionVersionTextView: TextView =
                view.findViewById(R.id.extensionVersionTextView)
            val settingsImageView: ImageView = view.findViewById(R.id.settingsImageView)
            val extensionIconImageView: ImageView = view.findViewById(R.id.extensionIconImageView)
            val deleteView: ImageView = view.findViewById(R.id.deleteTextView)
            val updateView: ImageView = view.findViewById(R.id.updateTextView)
        }

        companion object {
            fun <E : Any> diffCallback(info: (E) -> Info) = object : DiffUtil.ItemCallback<E>() {
                override fun areItemsTheSame(oldItem: E, newItem: E): Boolean =
                    info(oldItem).pkgName == info(newItem).pkgName

                // The extension models are data classes, so this compares their contents.
                override fun areContentsTheSame(oldItem: E, newItem: E): Boolean =
                    oldItem == newItem
            }
        }
    }
}
