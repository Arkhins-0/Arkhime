package com.arkhins.arkhime.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.arkhins.arkhime.BottomSheetDialogFragment
import com.arkhins.arkhime.R
import com.arkhins.arkhime.databinding.BottomSheetDiscordRpcBinding
import com.arkhins.arkhime.settings.saving.PrefManager
import com.arkhins.arkhime.settings.saving.PrefName

class DiscordDialogFragment : BottomSheetDialogFragment() {
    private var _binding: BottomSheetDiscordRpcBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetDiscordRpcBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        when (PrefManager.getCustomVal("discord_mode", "arkhime")) {
            "nothing" -> binding.radioNothing.isChecked = true
            "arkhime" -> binding.radioArkhime.isChecked = true
            "anilist" -> binding.radioAnilist.isChecked = true
            else -> binding.radioAnilist.isChecked = true
        }
        binding.showIcon.isChecked = PrefManager.getVal(PrefName.ShowAniListIcon)
        binding.showIcon.setOnCheckedChangeListener { _, isChecked ->
            PrefManager.setVal(PrefName.ShowAniListIcon, isChecked)
        }
        binding.anilistLinkPreview.text =
            getString(R.string.anilist_link, PrefManager.getVal<String>(PrefName.AnilistUserName))

        binding.radioGroup.setOnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                binding.radioNothing.id -> "nothing"
                binding.radioArkhime.id -> "arkhime"
                binding.radioAnilist.id -> "anilist"
                else -> "arkhime"
            }
            PrefManager.setCustomVal("discord_mode", mode)
        }
    }

    override fun onDestroy() {
        _binding = null
        super.onDestroy()
    }
}