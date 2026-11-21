package com.arkhins.arkhime.settings

import android.app.NotificationManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.arkhins.arkhime.databinding.FragmentExtensionsBinding
import com.arkhins.arkhime.settings.paging.ExtensionStoreAdapter
import com.arkhins.arkhime.settings.paging.ExtensionStoreViewModel
import com.arkhins.arkhime.settings.paging.ExtensionStoreViewModelFactory
import com.arkhins.arkhime.settings.paging.StoreItem
import eu.kanade.tachiyomi.extension.InstallStep
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import rx.Observable
import rx.android.schedulers.AndroidSchedulers

/**
 * The "Available" tab, shared by anime and manga: the store's extensions that aren't installed
 * yet, with search and install. A subclass says where its extensions come from and how to read
 * and install one.
 *
 * @param A the available extension type, like `AnimeExtension.Available`.
 */
abstract class AvailableExtensionsFragment<A : Any> : Fragment(), SearchQueryHandler {

    protected abstract val availableExtensions: Flow<List<A>>
    protected abstract val installedPackageNames: Flow<List<String>>
    protected abstract fun item(extension: A): StoreItem
    protected abstract fun install(extension: A): Observable<InstallStep>

    private var _binding: FragmentExtensionsBinding? = null
    private val binding get() = _binding!!

    @Suppress("UNCHECKED_CAST")
    private val viewModel: ExtensionStoreViewModel<A> by lazy {
        ViewModelProvider(
            this,
            ExtensionStoreViewModelFactory(availableExtensions, installedPackageNames, ::item)
        )[ExtensionStoreViewModel::class.java] as ExtensionStoreViewModel<A>
    }

    private val adapter by lazy {
        ExtensionStoreAdapter(::item, ::onInstallClick)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentExtensionsBinding.inflate(inflater, container, false)

        binding.allExtensionsRecyclerView.isNestedScrollingEnabled = false
        binding.allExtensionsRecyclerView.adapter = adapter
        binding.allExtensionsRecyclerView.layoutManager = LinearLayoutManager(context)
        (binding.allExtensionsRecyclerView.layoutManager as LinearLayoutManager).isItemPrefetchEnabled =
            true

        lifecycleScope.launch {
            viewModel.pagerFlow.collectLatest { pagingData ->
                adapter.submitData(pagingData)
            }
        }

        viewModel.invalidatePager() // Force a refresh of the pager

        return binding.root
    }

    override fun updateContentBasedOnQuery(query: String?) {
        viewModel.setSearchQuery(query ?: "")
    }

    override fun notifyDataChanged() {
        viewModel.invalidatePager()
    }

    private fun onInstallClick(extension: A) {
        if (isAdded) {
            val context = requireContext()
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val installerSteps = InstallerSteps(notificationManager, context)
            // Start the installation process
            install(extension)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { installStep -> installerSteps.onInstallStep(installStep) {} },
                    { error -> installerSteps.onError(error) {} },
                    { installerSteps.onComplete { viewModel.invalidatePager() } }
                )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
