package com.arkhins.arkhime.settings

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.arkhins.arkhime.BottomSheetDialogFragment
import com.arkhins.arkhime.MainActivity
import com.arkhins.arkhime.R
import com.arkhins.arkhime.Refresh
import com.arkhins.arkhime.connections.anilist.Anilist
import com.arkhins.arkhime.connections.mal.MAL
import com.arkhins.arkhime.databinding.BottomSheetSettingsBinding
import com.arkhins.arkhime.getThemeColor
import com.arkhins.arkhime.home.AnimeFragment
import com.arkhins.arkhime.home.HomeFragment
import com.arkhins.arkhime.home.LoginFragment
import com.arkhins.arkhime.home.MangaFragment
import com.arkhins.arkhime.home.NoInternet
import com.arkhins.arkhime.incognitoNotification
import com.arkhins.arkhime.loadImage
import com.arkhins.arkhime.snackString
import com.arkhins.arkhime.profile.ProfileActivity
import com.arkhins.arkhime.profile.activity.FeedActivity
import com.arkhins.arkhime.profile.notification.NotificationActivity
import com.arkhins.arkhime.setSafeOnClickListener
import com.arkhins.arkhime.settings.saving.PrefManager
import com.arkhins.arkhime.settings.saving.PrefName
import com.arkhins.arkhime.startMainActivity
import com.arkhins.arkhime.openLinkInCustomTab
import com.arkhins.arkhime.util.customAlertDialog
import eu.kanade.tachiyomi.util.system.getSerializableCompat
import com.arkhins.arkhime.download.anime.OfflineAnimeFragment
import com.arkhins.arkhime.download.manga.OfflineMangaFragment
import com.arkhins.arkhime.offline.OfflineFragment
import java.util.Timer
import kotlin.concurrent.schedule

class SettingsDialogFragment : BottomSheetDialogFragment() {
    private var _binding: BottomSheetSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var pageType: PageType
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pageType = arguments?.getSerializableCompat("pageType") as? PageType ?: PageType.HOME
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val window = dialog?.window
        window?.statusBarColor = Color.CYAN
        window?.navigationBarColor =
            requireContext().getThemeColor(com.google.android.material.R.attr.colorSurface)
        val isRescueModeEarly: Boolean = PrefManager.getVal(PrefName.RescueMode)
        val notificationIcon = if (!isRescueModeEarly && Anilist.unreadNotificationCount > 0) {
            R.drawable.ic_round_notifications_active_24
        } else {
            R.drawable.ic_round_notifications_none_24
        }
        binding.settingsNotification.setImageResource(notificationIcon)
        if (isRescueModeEarly) binding.settingsNotification.visibility = View.GONE

        if (Anilist.token != null) {
            binding.settingsLogin.setText(R.string.logout)
            binding.settingsLogin.setOnClickListener {
                requireContext().customAlertDialog().apply {
                    setTitle(R.string.logout)
                    setMessage(R.string.logout_confirm)
                    setPosButton(R.string.yes) {
                        Anilist.removeSavedToken()
                        startMainActivity(requireActivity())
                    }
                    setNegButton(R.string.no)
                    show()
                }
            }
            val isRescueMode: Boolean = PrefManager.getVal(PrefName.RescueMode)
            binding.settingsUsername.text = if (isRescueMode) MAL.username ?: "MAL User" else Anilist.username
            binding.settingsUserAvatar.loadImage(if (isRescueMode) MAL.avatar else Anilist.avatar)
        } else {
            binding.settingsUsername.visibility = View.GONE
            binding.settingsLogin.setText(R.string.login)
            binding.settingsLogin.setOnClickListener {
                dismiss()
                Anilist.loginIntent(requireActivity())
            }
        }
        val isRescueMode: Boolean = PrefManager.getVal(PrefName.RescueMode)
        binding.settingsNotificationCount.isVisible = !isRescueMode && Anilist.unreadNotificationCount > 0
        binding.settingsNotificationCount.text = Anilist.unreadNotificationCount.toString()
        if (isRescueMode) {
            binding.settingsActivity.visibility = View.GONE
        }
        binding.settingsUserAvatar.setOnClickListener {
            if (isRescueMode) {
                val malUsername = MAL.username
                if (!malUsername.isNullOrBlank()) {
                    openLinkInCustomTab("https://myanimelist.net/profile/$malUsername")
                } else {
                    snackString(getString(R.string.rescue_mode_active))
                }
                return@setOnClickListener
            }
            ContextCompat.startActivity(
                requireContext(), Intent(requireContext(), ProfileActivity::class.java)
                    .putExtra("userId", Anilist.userid), null
            )
        }

        binding.settingsIncognito.isChecked = PrefManager.getVal(PrefName.Incognito)
        binding.settingsIncognito.setOnCheckedChangeListener { _, isChecked ->
            // Added check to ensure fragment is still active before updating
            if (isAdded) {
                PrefManager.setVal(PrefName.Incognito, isChecked)
                incognitoNotification(requireContext())
            }
        }

        binding.settingsRescueMode.isChecked = PrefManager.getVal(PrefName.RescueMode)
        binding.settingsRescueMode.setOnCheckedChangeListener { _, isChecked ->
            PrefManager.setVal(PrefName.RescueMode, isChecked)
            activity?.let { act ->
                dismiss()
                val intent = Intent(act, MainActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                act.startActivity(intent)
                act.overridePendingTransition(0, 0)
                act.finish()
                act.overridePendingTransition(0, 0)
            }
        }

        // Restored from Dantotsu: Offline mode switch and Extensions button
        binding.settingsDownloads.isChecked = PrefManager.getVal(PrefName.OfflineMode)
        binding.settingsDownloads.setOnCheckedChangeListener { _, isChecked ->
            Timer().schedule(300) {
                when (pageType) {
                    PageType.MANGA -> {
                        val intent = Intent(activity, NoInternet::class.java)
                        intent.putExtra(
                            "FRAGMENT_CLASS_NAME",
                            OfflineMangaFragment::class.java.name
                        )
                        startActivity(intent)
                    }

                    PageType.ANIME -> {
                        val intent = Intent(activity, NoInternet::class.java)
                        intent.putExtra(
                            "FRAGMENT_CLASS_NAME",
                            OfflineAnimeFragment::class.java.name
                        )
                        startActivity(intent)
                    }

                    PageType.HOME -> {
                        val intent = Intent(activity, NoInternet::class.java)
                        intent.putExtra("FRAGMENT_CLASS_NAME", OfflineFragment::class.java.name)
                        startActivity(intent)
                    }

                    PageType.OfflineMANGA -> {
                        val intent = Intent(activity, MainActivity::class.java)
                        intent.putExtra("FRAGMENT_CLASS_NAME", MangaFragment::class.java.name)
                        startActivity(intent)
                    }

                    PageType.OfflineHOME -> {
                        val intent = Intent(activity, MainActivity::class.java)
                        intent.putExtra(
                            "FRAGMENT_CLASS_NAME",
                            if (Anilist.token != null) HomeFragment::class.java.name else LoginFragment::class.java.name
                        )
                        startActivity(intent)
                    }

                    PageType.OfflineANIME -> {
                        val intent = Intent(activity, MainActivity::class.java)
                        intent.putExtra("FRAGMENT_CLASS_NAME", AnimeFragment::class.java.name)
                        startActivity(intent)
                    }
                }

                dismiss()
                PrefManager.setVal(PrefName.OfflineMode, isChecked)
            }
        }

        binding.settingsExtensionSettings.setSafeOnClickListener {
            startActivity(Intent(activity, ExtensionsActivity::class.java))
            dismiss()
        }

        binding.settingsNyaa.setSafeOnClickListener {
            startActivity(
                com.arkhins.arkhime.feeds.FeedHostActivity.intent(
                    requireContext(), com.arkhins.arkhime.feeds.FeedSource.NYAA,
                    category = if (pageType == PageType.MANGA) com.arkhins.arkhime.feeds.nyaa.NyaaCategory.MANGA.key else null
                )
            )
            dismiss()
        }

        // 18+ site: only offered when AniList's "Display adult content" is on, like the rest of the app
        binding.settingsSukebei.isVisible = Anilist.adult
        binding.settingsSukebei.setSafeOnClickListener {
            startActivity(
                com.arkhins.arkhime.feeds.FeedHostActivity.intent(requireContext(), com.arkhins.arkhime.feeds.FeedSource.SUKEBEI)
            )
            dismiss()
        }

        binding.settingsCrunchyroll.setSafeOnClickListener {
            startActivity(
                com.arkhins.arkhime.feeds.FeedHostActivity.intent(requireContext(), com.arkhins.arkhime.feeds.FeedSource.CRUNCHYROLL)
            )
            dismiss()
        }

        binding.settingsForum.setSafeOnClickListener {
            startActivity(Intent(activity, com.arkhins.arkhime.forum.ForumActivity::class.java))
            dismiss()
        }

        binding.settingsSettings.setSafeOnClickListener {
            startActivity(Intent(activity, SettingsActivity::class.java))
            dismiss()
        }

        binding.settingsActivity.setSafeOnClickListener {
            if (PrefManager.getVal<Boolean>(PrefName.RescueMode)) {
                snackString(getString(R.string.rescue_mode_active))
                return@setSafeOnClickListener
            }
            startActivity(Intent(activity, FeedActivity::class.java))
            dismiss()
        }

        binding.settingsNotification.setOnClickListener {
            if (PrefManager.getVal<Boolean>(PrefName.RescueMode)) {
                snackString(getString(R.string.rescue_mode_active))
                return@setOnClickListener
            }
            startActivity(Intent(activity, NotificationActivity::class.java))
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        enum class PageType {
            MANGA, ANIME, HOME, OfflineMANGA, OfflineANIME, OfflineHOME
        }

        fun newInstance(pageType: PageType): SettingsDialogFragment {
            val fragment = SettingsDialogFragment()
            val args = Bundle()
            args.putSerializable("pageType", pageType)
            fragment.arguments = args
            return fragment
        }
    }
}
