package com.arkhins.arkhime.settings.paging

import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingDataAdapter
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.cachedIn
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.arkhins.arkhime.R
import com.arkhins.arkhime.databinding.ItemExtensionAllBinding
import com.arkhins.arkhime.others.LanguageMapper
import com.arkhins.arkhime.settings.saving.PrefManager
import com.arkhins.arkhime.settings.saving.PrefName
import com.bumptech.glide.Glide
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
 * The extension store list, shared by the anime and manga "Available" tabs. It pages through the
 * store's extensions, leaving out installed ones and filtering by search, language and the NSFW
 * setting. Each tab says how to read its extension type through [StoreItem].
 */

/** What the store list shows for one available extension. */
class StoreItem(
    val name: String,
    val pkgName: String,
    val lang: String,
    val versionName: String,
    val isNsfw: Boolean,
    val iconUrl: String,
)

@Suppress("UNCHECKED_CAST")
class ExtensionStoreViewModelFactory<A : Any>(
    private val available: Flow<List<A>>,
    private val installedPackages: Flow<List<String>>,
    private val item: (A) -> StoreItem,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ExtensionStoreViewModel(available, installedPackages, item) as T
    }
}

class ExtensionStoreViewModel<A : Any>(
    available: Flow<List<A>>,
    installedPackages: Flow<List<String>>,
    private val item: (A) -> StoreItem,
) : ViewModel() {
    private val searchQuery = MutableStateFlow("")
    private var currentPagingSource: ExtensionStorePagingSource<A>? = null

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun invalidatePager() {
        currentPagingSource?.invalidate()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val pagerFlow: Flow<PagingData<A>> = combine(
        available,
        installedPackages,
        searchQuery
    ) { availableList, installed, query ->
        Triple(availableList, installed, query)
    }.flatMapLatest { (availableList, installed, query) ->
        Pager(
            PagingConfig(
                pageSize = 15,
                initialLoadSize = 15,
                prefetchDistance = 15
            )
        ) {
            ExtensionStorePagingSource(availableList, installed.toSet(), query, item)
                .also { currentPagingSource = it }
        }.flow
    }.cachedIn(viewModelScope)
}

class ExtensionStorePagingSource<A : Any>(
    private val available: List<A>,
    private val installedPackages: Set<String>,
    private val searchQuery: String,
    private val item: (A) -> StoreItem,
) : PagingSource<Int, A>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, A> {
        val position = params.key ?: 0
        val availableExtensions = available.filterNot { item(it).pkgName in installedPackages }
        val isNsfwEnabled: Boolean = PrefManager.getVal(PrefName.NSFWExtension)

        val filteredExtensions = if (searchQuery.isEmpty()) {
            availableExtensions
        } else {
            availableExtensions.filter { item(it).name.contains(searchQuery, ignoreCase = true) }
        }
        val lang: String = PrefManager.getVal(PrefName.LangSort)
        val langFilter =
            if (lang != "all") filteredExtensions.filter { item(it).lang == lang } else filteredExtensions
        val filternfsw = if (isNsfwEnabled) langFilter else langFilter.filterNot { item(it).isNsfw }
        return try {
            val sublist = filternfsw.subList(
                fromIndex = position,
                toIndex = (position + params.loadSize).coerceAtMost(filternfsw.size)
            )
            LoadResult.Page(
                data = sublist,
                prevKey = if (position == 0) null else position - params.loadSize,
                nextKey = if (position + params.loadSize >= filternfsw.size) null else position + params.loadSize
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, A>): Int? {
        return null
    }
}

class ExtensionStoreAdapter<A : Any>(
    private val item: (A) -> StoreItem,
    private val onInstallClick: (A) -> Unit,
) : PagingDataAdapter<A, ExtensionStoreAdapter<A>.ViewHolder>(diffCallback(item)) {

    private val skipIcons: Boolean = PrefManager.getVal(PrefName.SkipExtensionIcons)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding =
            ItemExtensionAllBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val extension = getItem(position)
        if (extension != null) {
            if (!skipIcons) {
                Glide.with(holder.itemView.context)
                    .load(item(extension).iconUrl)
                    .into(holder.extensionIconImageView)
            }
            holder.bind(extension)
        }
    }

    inner class ViewHolder(private val binding: ItemExtensionAllBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private val job = Job()
        private val scope = CoroutineScope(Dispatchers.Main + job)

        init {
            binding.closeTextView.setOnClickListener {
                if (bindingAdapterPosition == RecyclerView.NO_POSITION) return@setOnClickListener
                val extension = getItem(bindingAdapterPosition)
                if (extension != null) {
                    onInstallClick(extension)
                    binding.closeTextView.setImageResource(R.drawable.ic_sync)
                    scope.launch {
                        while (isActive) {
                            withContext(Dispatchers.Main) {
                                binding.closeTextView.animate()
                                    .rotationBy(360f)
                                    .setDuration(1000)
                                    .setInterpolator(LinearInterpolator())
                                    .start()
                            }
                            delay(1000)
                        }
                    }
                }
            }
        }

        val extensionIconImageView: ImageView = binding.extensionIconImageView

        fun bind(extension: A) {
            val info = item(extension)
            val nsfw = if (info.isNsfw) "(18+)" else ""
            val lang = LanguageMapper.getLanguageName(info.lang)
            binding.extensionNameTextView.text = info.name
            val versionText = "$lang ${info.versionName} $nsfw"
            binding.extensionVersionTextView.text = versionText
        }

        fun clear() {
            job.cancel() // Cancel the coroutine when the view is recycled
        }
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        holder.clear()
    }

    companion object {
        fun <A : Any> diffCallback(item: (A) -> StoreItem) = object : DiffUtil.ItemCallback<A>() {
            override fun areItemsTheSame(oldItem: A, newItem: A): Boolean =
                item(oldItem).pkgName == item(newItem).pkgName

            // The extension models are data classes, so this compares their contents.
            override fun areContentsTheSame(oldItem: A, newItem: A): Boolean = oldItem == newItem
        }
    }
}
