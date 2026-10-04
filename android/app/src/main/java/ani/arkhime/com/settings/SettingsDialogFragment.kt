package ani.arkhime.com.settings

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import ani.arkhime.com.BottomSheetDialogFragment
import ani.arkhime.com.MainActivity
import ani.arkhime.com.R
import ani.arkhime.com.Refresh
import ani.arkhime.com.connections.anilist.Anilist
import ani.arkhime.com.connections.mal.MAL
import ani.arkhime.com.databinding.BottomSheetSettingsBinding
import ani.arkhime.com.getThemeColor
import ani.arkhime.com.home.AnimeFragment
import ani.arkhime.com.home.HomeFragment
import ani.arkhime.com.home.LoginFragment
import ani.arkhime.com.home.MangaFragment
import ani.arkhime.com.home.NoInternet
import ani.arkhime.com.incognitoNotification
import ani.arkhime.com.loadImage
import ani.arkhime.com.snackString
import ani.arkhime.com.profile.ProfileActivity
import ani.arkhime.com.profile.activity.FeedActivity
import ani.arkhime.com.profile.notification.NotificationActivity
import ani.arkhime.com.setSafeOnClickListener
import ani.arkhime.com.settings.saving.PrefManager
import ani.arkhime.com.settings.saving.PrefName
import ani.arkhime.com.startMainActivity
import ani.arkhime.com.openLinkInCustomTab
import ani.arkhime.com.util.customAlertDialog
import eu.kanade.tachiyomi.util.system.getSerializableCompat

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
