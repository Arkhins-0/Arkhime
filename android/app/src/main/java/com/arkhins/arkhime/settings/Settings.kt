package com.arkhins.arkhime.settings

import com.arkhins.arkhime.databinding.ItemSettingsBinding
import com.arkhins.arkhime.databinding.ItemSettingsSwitchBinding

data class Settings(
    val type: Int,
    val name: String,
    val desc: String,
    val icon: Int,
    val onClick: ((ItemSettingsBinding) -> Unit)? = null,
    val onLongClick: (() -> Unit)? = null,
    val switch: ((isChecked: Boolean, view: ItemSettingsSwitchBinding) -> Unit)? = null,
    val attach: ((ItemSettingsBinding) -> Unit)? = null,
    val attachToSwitch: ((ItemSettingsSwitchBinding) -> Unit)? = null,
    var isVisible: Boolean = true,
    val isActivity: Boolean = false,
    var isChecked: Boolean = false,
)
