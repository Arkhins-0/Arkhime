package ani.arkhime.com.themes

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowManager
import ani.arkhime.com.R
import ani.arkhime.com.settings.saving.PrefManager
import ani.arkhime.com.settings.saving.PrefName
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions


class ThemeManager(private val context: Activity) {
    fun applyTheme(fromImage: Bitmap? = null) {
        val useOLED = PrefManager.getVal(PrefName.UseOLED) && isDarkThemeActive(context)
        val useCustomTheme: Boolean = PrefManager.getVal(PrefName.UseCustomTheme)
        val customTheme: Int = PrefManager.getVal(PrefName.CustomThemeInt)
        val useSource: Boolean = PrefManager.getVal(PrefName.UseSourceTheme)
        val useMaterial: Boolean = PrefManager.getVal(PrefName.UseMaterialYou)
        if (useSource) {
            val returnedEarly = applyDynamicColors(
                useMaterial,
                context,
                useOLED,
                fromImage,
                useCustom = if (useCustomTheme) customTheme else null
            )
            if (!returnedEarly) return
        } else if (useCustomTheme) {
            val returnedEarly =
                applyDynamicColors(useMaterial, context, useOLED, useCustom = customTheme)
            if (!returnedEarly) return
        } else {
            val returnedEarly = applyDynamicColors(useMaterial, context, useOLED, useCustom = null)
            if (!returnedEarly) return
        }
        val theme: String = PrefManager.getVal(PrefName.Theme)

        val themeToApply = when (theme) {
            "ARKHIME" -> if (useOLED) R.style.Theme_Arkhime_ArkhimeOLED else R.style.Theme_Arkhime_Arkhime
            "BLUE" -> if (useOLED) R.style.Theme_Arkhime_BlueOLED else R.style.Theme_Arkhime_Blue
            "GREEN" -> if (useOLED) R.style.Theme_Arkhime_GreenOLED else R.style.Theme_Arkhime_Green
            "PURPLE" -> if (useOLED) R.style.Theme_Arkhime_PurpleOLED else R.style.Theme_Arkhime_Purple
            "PINK" -> if (useOLED) R.style.Theme_Arkhime_PinkOLED else R.style.Theme_Arkhime_Pink
            "ORANGE" -> if (useOLED) R.style.Theme_Arkhime_OrangeOLED else R.style.Theme_Arkhime_Orange
            "MAGENTA" -> if (useOLED) R.style.Theme_Arkhime_MagentaOLED else R.style.Theme_Arkhime_Magenta
            "RED" -> if (useOLED) R.style.Theme_Arkhime_RedOLED else R.style.Theme_Arkhime_Red
            "LAVENDER" -> if (useOLED) R.style.Theme_Arkhime_LavenderOLED else R.style.Theme_Arkhime_Lavender
            "OCEAN" -> if (useOLED) R.style.Theme_Arkhime_OceanOLED else R.style.Theme_Arkhime_Ocean
            "MONOCHROME (BETA)" -> if (useOLED) R.style.Theme_Arkhime_MonochromeOLED else R.style.Theme_Arkhime_Monochrome
            else -> if (useOLED) R.style.Theme_Arkhime_ArkhimeOLED else R.style.Theme_Arkhime_Arkhime
        }

        val window = context.window
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            @Suppress("DEPRECATION")
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = 0x00000000
        context.setTheme(themeToApply)
        val useSystemFont = PrefManager.getVal<Boolean>(PrefName.UseSystemFont)
        if (useSystemFont) {
            context.theme.applyStyle(R.style.ThemeOverlay_Arkhime_SystemFont, true)
        }
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_LTR
    }

    fun setWindowFlag(activity: Activity, bits: Int, on: Boolean) {
        val win: Window = activity.window
        val winParams: WindowManager.LayoutParams = win.attributes
        if (on) {
            winParams.flags = winParams.flags or bits
        } else {
            winParams.flags = winParams.flags and bits.inv()
        }
        win.attributes = winParams
    }

    private fun applyDynamicColors(
        useMaterialYou: Boolean,
        context: Context,
        useOLED: Boolean,
        bitmap: Bitmap? = null,
        useCustom: Int? = null
    ): Boolean {
        val builder = DynamicColorsOptions.Builder()
        var needMaterial = true

        // Set content-based source if a bitmap is provided
        if (bitmap != null) {
            builder.setContentBasedSource(bitmap)
            needMaterial = false
        } else if (useCustom != null) {
            builder.setContentBasedSource(useCustom)
            needMaterial = false
        }

        if (useOLED) {
            builder.setThemeOverlay(R.style.AppTheme_Amoled)
        }
        val useSystemFont = PrefManager.getVal<Boolean>(PrefName.UseSystemFont)
        if (useSystemFont) {
            builder.setThemeOverlay(R.style.ThemeOverlay_Arkhime_SystemFont)
        }
        if (needMaterial && !useMaterialYou) return true

        // Build the options
        val options = builder.build()

        // Apply the dynamic colors to the activity
        val activity = context as Activity
        DynamicColors.applyToActivityIfAvailable(activity, options)

        if (useOLED) {
            val options2 = DynamicColorsOptions.Builder()
                .setThemeOverlay(R.style.AppTheme_Amoled)
                .build()
            DynamicColors.applyToActivityIfAvailable(activity, options2)
        }
        if (useSystemFont) {
            val optionsFont = DynamicColorsOptions.Builder()
                .setThemeOverlay(R.style.ThemeOverlay_Arkhime_SystemFont)
                .build()
            DynamicColors.applyToActivityIfAvailable(activity, optionsFont)
        }

        return false
    }

    private fun isDarkThemeActive(context: Context): Boolean {
        return when (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
            Configuration.UI_MODE_NIGHT_YES -> true
            Configuration.UI_MODE_NIGHT_NO -> false
            Configuration.UI_MODE_NIGHT_UNDEFINED -> false
            else -> false
        }
    }


    companion object {
        enum class Theme(val theme: String) {
            ARKHIME("ARKHIME"),
            BLUE("BLUE"),
            GREEN("GREEN"),
            PURPLE("PURPLE"),
            PINK("PINK"),
            ORANGE("ORANGE"),
            MAGENTA("MAGENTA"),
            RED("RED"),
            LAVENDER("LAVENDER"),
            OCEAN("OCEAN"),
            MONOCHROME("MONOCHROME (BETA)");

            companion object {
                fun fromString(value: String): Theme {
                    return entries.find { it.theme == value } ?: ARKHIME
                }
            }
        }
    }
}
