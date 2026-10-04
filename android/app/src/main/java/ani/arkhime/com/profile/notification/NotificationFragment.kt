package ani.arkhime.com.profile.notification

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ani.arkhime.com.R
import ani.arkhime.com.connections.anilist.Anilist
import ani.arkhime.com.connections.anilist.api.Notification
import ani.arkhime.com.databinding.FragmentNotificationsBinding
import ani.arkhime.com.media.MediaDetailsActivity
import ani.arkhime.com.profile.ProfileActivity
import ani.arkhime.com.profile.activity.FeedActivity
import ani.arkhime.com.profile.notification.NotificationFragment.Companion.NotificationType.MEDIA
import ani.arkhime.com.profile.notification.NotificationFragment.Companion.NotificationType.ONE
import ani.arkhime.com.profile.notification.NotificationFragment.Companion.NotificationType.USER
import ani.arkhime.com.settings.saving.PrefManager
import ani.arkhime.com.settings.saving.PrefName
import com.xwray.groupie.GroupieAdapter
import eu.kanade.tachiyomi.util.system.getSerializableCompat
import kotlinx.coroutines.launch


class NotificationFragment : Fragment() {
    private lateinit var type: NotificationType
    private var getID: Int = -1
    private var _binding: FragmentNotificationsBinding? = null
    private val binding get() = _binding!!
    private var adapter: GroupieAdapter = GroupieAdapter()
    private var currentPage = 1
    private var hasNextPage = false
    private var countResetCallback: ((NotificationType, Boolean) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNotificationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding?.notificationRecyclerView?.adapter = null
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        arguments?.let {
            getID = it.getInt("id")
            type = it.getSerializableCompat<NotificationType>("type") as NotificationType
        }
        binding.notificationRecyclerView.adapter = adapter
        binding.notificationRecyclerView.layoutManager = LinearLayoutManager(context)
        binding.notificationProgressBar.isVisible = true
        binding.emptyTextView.text = getString(R.string.nothing_here)
        viewLifecycleOwner.lifecycleScope.launch {
            getList()
            resetCountIfNeeded()

            _binding?.notificationProgressBar?.isVisible = false
        }
        binding.notificationSwipeRefresh.setOnRefreshListener {
            viewLifecycleOwner.lifecycleScope.launch {
                adapter.clear()
                currentPage = 1
                resetCountIfNeeded()
                getList()
                _binding?.notificationSwipeRefresh?.isRefreshing = false
            }
        }
        binding.notificationRecyclerView.addOnScrollListener(object :
            RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (shouldLoadMore()) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        _binding?.notificationRefresh?.isVisible = true
                        getList()
                        _binding?.notificationRefresh?.isVisible = false
                    }
                }
            }
        })

    }
    private fun resetCountIfNeeded() {
        if (type == ONE) return

        when (type) {
            USER -> {
                PrefManager.setVal(PrefName.UnreadUserNotifications, 0)
            }
            MEDIA -> {
                PrefManager.setVal(PrefName.UnreadMediaNotifications, 0)
            }
            ONE -> {}
        }

        countResetCallback?.invoke(type, true)
    }
    private suspend fun getList() {
        val list = when (type) {
            ONE -> getNotificationsFiltered(false) { it.id == getID }
            MEDIA -> getNotificationsFiltered(type = true) { it.media != null || it.notificationType == ani.arkhime.com.connections.anilist.api.NotificationType.MEDIA_DELETION.value }
            USER -> getNotificationsFiltered { it.media == null && it.notificationType != ani.arkhime.com.connections.anilist.api.NotificationType.RELATED_MEDIA_ADDITION.value }
        }

        
        adapter.addAll(list.map { NotificationItem(it, type, adapter, ::onClick) })
        if (adapter.itemCount == 0) {
            binding.emptyTextView.isVisible = true
        }
    }

    private suspend fun getNotificationsFiltered(
        reset: Boolean = true,
        type: Boolean? = null,
        filter: (Notification) -> Boolean
    ): List<Notification> {
        val userId =
            Anilist.userid ?: PrefManager.getVal<String>(PrefName.AnilistUserId).toIntOrNull() ?: 0
        val res = Anilist.query.getNotifications(userId, currentPage, reset, type)?.data?.page
        currentPage = res?.pageInfo?.currentPage?.plus(1) ?: 1
        hasNextPage = res?.pageInfo?.hasNextPage ?: false
        return res?.notifications?.filter(filter) ?: listOf()
    }

    private fun shouldLoadMore(): Boolean {
        val layoutManager =
            (binding.notificationRecyclerView.layoutManager as LinearLayoutManager).findLastVisibleItemPosition()
        val adapter = binding.notificationRecyclerView.adapter

        return hasNextPage && !binding.notificationRefresh.isVisible && adapter?.itemCount != 0 &&
                layoutManager == (adapter!!.itemCount - 1) &&
                !binding.notificationRecyclerView.canScrollVertically(1)
    }

    fun onClick(id: Int, optional: Int?, type: NotificationClickType) {
        val intent = when (type) {
            NotificationClickType.USER -> Intent(
                requireContext(),
                ProfileActivity::class.java
            ).apply {
                putExtra("userId", id)
            }

            NotificationClickType.MEDIA -> Intent(
                requireContext(),
                MediaDetailsActivity::class.java
            ).apply {
                putExtra("mediaId", id)
            }

            NotificationClickType.ACTIVITY -> Intent(
                requireContext(),
                FeedActivity::class.java
            ).apply {
                putExtra("activityId", id)
            }

            NotificationClickType.COMMENT -> Intent(
                requireContext(),
                MediaDetailsActivity::class.java
            ).apply {
                putExtra("FRAGMENT_TO_LOAD", "COMMENTS")
                putExtra("mediaId", id)
                putExtra("commentId", optional ?: -1)
            }

            NotificationClickType.THREAD -> Intent(
                requireContext(),
                ani.arkhime.com.forum.ThreadViewActivity::class.java
            ).apply {
                putExtra("threadId", id)
            }

            NotificationClickType.UNDEFINED -> null
        }

        intent?.let {
            ContextCompat.startActivity(requireContext(), it, null)
        }
    }
    override fun onResume() {
        super.onResume()
        if (_binding != null) {
            binding.root.requestLayout()
        }
    }
    fun onVisible() {
        resetCountIfNeeded()
    }
    companion object {
        enum class NotificationClickType { USER, MEDIA, ACTIVITY, COMMENT, THREAD, UNDEFINED }
        enum class NotificationType { MEDIA, USER, ONE }

        fun newInstance(
            type: NotificationType, 
            id: Int = -1,
            countResetCallback: ((NotificationType, Boolean) -> Unit)? = null
        ): NotificationFragment {
            return NotificationFragment().apply {
                this.countResetCallback = countResetCallback
                arguments = Bundle().apply {
                    putSerializable("type", type)
                    putInt("id", id)
                }
            }
        }
    }

}
