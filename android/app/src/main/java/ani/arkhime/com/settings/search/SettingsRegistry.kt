package ani.arkhime.com.settings.search

import android.content.Context
import android.os.Build
import ani.arkhime.com.R
import ani.arkhime.com.connections.anilist.Anilist
import ani.arkhime.com.settings.AnilistSettingsActivity
import ani.arkhime.com.settings.FAQActivity
import ani.arkhime.com.settings.SettingsAboutActivity
import ani.arkhime.com.settings.SettingsAccountActivity
import ani.arkhime.com.settings.SettingsAnimeActivity
import ani.arkhime.com.settings.SettingsCommonActivity
import ani.arkhime.com.settings.SettingsMangaActivity
import ani.arkhime.com.settings.SettingsNotificationActivity
import ani.arkhime.com.settings.SettingsThemeActivity
import ani.arkhime.com.settings.UserInterfaceSettingsActivity

object SettingsRegistry {

    fun getAllSettings(context: Context): List<SearchableSetting> {
        val list = mutableListOf<SearchableSetting>()

        // 1. Theme Settings
        list.add(
            SearchableSetting(
                title = context.getString(R.string.oled_theme_variant),
                desc = context.getString(R.string.oled_theme_variant_desc),
                icon = R.drawable.ic_round_brightness_4_24,
                category = context.getString(R.string.theme),
                breadcrumbs = "${context.getString(R.string.settings)} > ${context.getString(R.string.theme)}",
                targetActivity = SettingsThemeActivity::class.java,
                highlightKey = context.getString(R.string.oled_theme_variant)
            )
        )
        list.add(
            SearchableSetting(
                title = context.getString(R.string.use_system_font),
                desc = context.getString(R.string.use_system_font_desc),
                icon = R.drawable.ic_round_font_size_24,
                category = context.getString(R.string.theme),
                breadcrumbs = "${context.getString(R.string.settings)} > ${context.getString(R.string.theme)}",
                targetActivity = SettingsThemeActivity::class.java,
                highlightKey = context.getString(R.string.use_system_font)
            )
        )
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.R) {
            list.add(
                SearchableSetting(
                    title = context.getString(R.string.use_material_you),
                    desc = context.getString(R.string.use_material_you_desc),
                    icon = R.drawable.ic_round_new_releases_24,
                    category = context.getString(R.string.theme),
                    breadcrumbs = "${context.getString(R.string.settings)} > ${context.getString(R.string.theme)}",
                    targetActivity = SettingsThemeActivity::class.java,
                    highlightKey = context.getString(R.string.use_material_you)
                )
            )
            list.add(
                SearchableSetting(
                    title = context.getString(R.string.use_unique_theme_for_each_item),
                    desc = context.getString(R.string.use_unique_theme_for_each_item_desc),
                    icon = R.drawable.ic_palette,
                    category = context.getString(R.string.theme),
                    breadcrumbs = "${context.getString(R.string.settings)} > ${context.getString(R.string.theme)}",
                    targetActivity = SettingsThemeActivity::class.java,
                    highlightKey = context.getString(R.string.use_unique_theme_for_each_item)
                )
            )
            list.add(
                SearchableSetting(
                    title = context.getString(R.string.use_custom_theme),
                    desc = context.getString(R.string.use_custom_theme_desc),
                    icon = R.drawable.ic_palette,
                    category = context.getString(R.string.theme),
                    breadcrumbs = "${context.getString(R.string.settings)} > ${context.getString(R.string.theme)}",
                    targetActivity = SettingsThemeActivity::class.java,
                    highlightKey = context.getString(R.string.use_custom_theme)
                )
            )
            list.add(
                SearchableSetting(
                    title = context.getString(R.string.color_picker),
                    desc = context.getString(R.string.color_picker_desc),
                    icon = R.drawable.ic_palette,
                    category = context.getString(R.string.theme),
                    breadcrumbs = "${context.getString(R.string.settings)} > ${context.getString(R.string.theme)}",
                    targetActivity = SettingsThemeActivity::class.java,
                    highlightKey = context.getString(R.string.color_picker)
                )
            )
        }

        // 2. Common Settings
        list.add(
            SearchableSetting(
                title = context.getString(R.string.ui_settings),
                desc = context.getString(R.string.ui_settings_desc),
                icon = R.drawable.ic_round_auto_awesome_24,
                category = context.getString(R.string.common),
                breadcrumbs = "${context.getString(R.string.settings)} > ${context.getString(R.string.common)}",
                targetActivity = UserInterfaceSettingsActivity::class.java,
                highlightKey = context.getString(R.string.ui_settings)
            )
        )
        list.add(
            SearchableSetting(
                title = context.getString(R.string.app_lock),
                desc = context.getString(R.string.app_lock_desc),
                icon = R.drawable.ic_round_lock_open_24,
                category = context.getString(R.string.common),
                breadcrumbs = "${context.getString(R.string.settings)} > ${context.getString(R.string.common)}",
                targetActivity = SettingsCommonActivity::class.java,
                highlightKey = context.getString(R.string.app_lock)
            )
        )
        list.add(
            SearchableSetting(
                title = context.getString(R.string.backup_restore),
                desc = context.getString(R.string.backup_restore_desc),
                icon = R.drawable.backup_restore,
                category = context.getString(R.string.common),
                breadcrumbs = "${context.getString(R.string.settings)} > ${context.getString(R.string.common)}",
                targetActivity = SettingsCommonActivity::class.java,
                highlightKey = context.getString(R.string.backup_restore)
            )
        )
        list.add(
            SearchableSetting(
                title = context.getString(R.string.hide_private),
                desc = context.getString(R.string.hide_private_desc),
                icon = R.drawable.ic_round_remove_red_eye_24,
                category = context.getString(R.string.common),
                breadcrumbs = "${context.getString(R.string.settings)} > ${context.getString(R.string.common)}",
                targetActivity = SettingsCommonActivity::class.java,
                highlightKey = context.getString(R.string.hide_private)
            )
        )
        list.add(
            SearchableSetting(
                title = context.getString(R.string.recentlyListOnly),
                desc = context.getString(R.string.recentlyListOnly_desc),
                icon = R.drawable.ic_round_new_releases_24,
                category = context.getString(R.string.common),
                breadcrumbs = "${context.getString(R.string.settings)} > ${context.getString(R.string.common)}",
                targetActivity = SettingsCommonActivity::class.java,
                highlightKey = context.getString(R.string.recentlyListOnly)
            )
        )
        list.add(
            SearchableSetting(
                title = context.getString(R.string.adult_only_content),
                desc = context.getString(R.string.adult_only_content_desc),
                icon = R.drawable.ic_round_nsfw_24,
                category = context.getString(R.string.common),
                breadcrumbs = "${context.getString(R.string.settings)} > ${context.getString(R.string.common)}",
                targetActivity = SettingsCommonActivity::class.java,
                highlightKey = context.getString(R.string.adult_only_content),
                isVisible = Anilist.adult
            )
        )

        // 3. User Interface Settings
        list.add(
            SearchableSetting(
                title = context.getString(R.string.immersive_mode),
                desc = context.getString(R.string.immersive_mode_info),
                icon = R.drawable.ic_round_fullscreen_24,
                category = context.getString(R.string.ui_settings),
                breadcrumbs = "${context.getString(R.string.settings)} > ${context.getString(R.string.common)} > ${context.getString(R.string.ui_settings)}",
                targetActivity = UserInterfaceSettingsActivity::class.java,
                highlightKey = context.getString(R.string.immersive_mode)
            )
        )
        list.add(
            SearchableSetting(
                title = context.getString(R.string.hide_notification_dot),
                desc = context.getString(R.string.hide_notification_dot),
                icon = R.drawable.ic_round_notifications_active_24,
                category = context.getString(R.string.ui_settings),
                breadcrumbs = "${context.getString(R.string.settings)} > ${context.getString(R.string.common)} > ${context.getString(R.string.ui_settings)}",
                targetActivity = UserInterfaceSettingsActivity::class.java,
                highlightKey = context.getString(R.string.hide_notification_dot)
            )
        )
        list.add(
            SearchableSetting(
                title = context.getString(R.string.home_layout_show),
                desc = context.getString(R.string.home_layout_show),
                icon = R.drawable.ic_round_playlist_add_24,
                category = context.getString(R.string.ui_settings),
                breadcrumbs = "${context.getString(R.string.settings)} > ${context.getString(R.string.common)} > ${context.getString(R.string.ui_settings)}",
                targetActivity = UserInterfaceSettingsActivity::class.java,
                highlightKey = context.getString(R.string.home_layout_show)
            )
        )
        list.add(
            SearchableSetting(
                title = context.getString(R.string.small_view),
                desc = context.getString(R.string.small_view),
                icon = R.drawable.ic_round_art_track_24,
                category = context.getString(R.string.ui_settings),
                breadcrumbs = "${context.getString(R.string.settings)} > ${context.getString(R.string.common)} > ${context.getString(R.string.ui_settings)}",
                targetActivity = UserInterfaceSettingsActivity::class.java,
                highlightKey = context.getString(R.string.small_view)
            )
        )
        list.add(
            SearchableSetting(
                title = context.getString(R.string.carousel_clearlogo),
                desc = context.getString(R.string.carousel_clearlogo_desc),
                icon = R.drawable.ic_round_auto_awesome_24,
                category = context.getString(R.string.ui_settings),
                breadcrumbs = "${context.getString(R.string.settings)} > ${context.getString(R.string.common)} > ${context.getString(R.string.ui_settings)}",
                targetActivity = UserInterfaceSettingsActivity::class.java,
                highlightKey = context.getString(R.string.carousel_clearlogo)
            )
        )
        list.add(
            SearchableSetting(
                title = context.getString(R.string.banner_animations),
                desc = context.getString(R.string.banner_animations),
                icon = R.drawable.ic_round_animation_24,
                category = context.getString(R.string.ui_settings),
                breadcrumbs = "${context.getString(R.string.settings)} > ${context.getString(R.string.common)} > ${context.getString(R.string.ui_settings)}",
                targetActivity = UserInterfaceSettingsActivity::class.java,
                highlightKey = context.getString(R.string.banner_animations)
            )
        )
        list.add(
            SearchableSetting(
                title = context.getString(R.string.layout_animations),
                desc = context.getString(R.string.layout_animations),
                icon = R.drawable.ic_round_animation_24,
                category = context.getString(R.string.ui_settings),
                breadcrumbs = "${context.getString(R.string.settings)} > ${context.getString(R.string.common)} > ${context.getString(R.string.ui_settings)}",
                targetActivity = UserInterfaceSettingsActivity::class.java,
                highlightKey = context.getString(R.string.layout_animations)
            )
        )

        // 4. Anime Settings
        list.add(
            SearchableSetting(
                title = context.getString(R.string.show_yt),
                desc = context.getString(R.string.show_yt_desc),
                icon = R.drawable.ic_round_play_circle_24,
                category = context.getString(R.string.anime),
                breadcrumbs = "${context.getString(R.string.settings)} > ${context.getString(R.string.anime)}",
                targetActivity = SettingsAnimeActivity::class.java,
                highlightKey = context.getString(R.string.show_yt)
            )
        )
        list.add(
            SearchableSetting(
                title = context.getString(R.string.include_list),
                desc = context.getString(R.string.include_list_anime_desc),
                icon = R.drawable.view_list_24,
                category = context.getString(R.string.anime),
                breadcrumbs = "${context.getString(R.string.settings)} > ${context.getString(R.string.anime)}",
                targetActivity = SettingsAnimeActivity::class.java,
                highlightKey = context.getString(R.string.include_list)
            )
        )

        // 5. Player Settings

        // 6. Manga & Reader Settings

        // 7. Extensions Settings

        // 8. Addons & Torrent Settings

        // 9. Notifications Settings

        // 10. Accounts & Anilist Settings
        list.add(
            SearchableSetting(
                title = context.getString(R.string.anilist_settings),
                desc = context.getString(R.string.alsettings_desc),
                icon = R.drawable.ic_anilist,
                category = context.getString(R.string.accounts),
                breadcrumbs = "${context.getString(R.string.settings)} > ${context.getString(R.string.accounts)}",
                targetActivity = AnilistSettingsActivity::class.java,
                highlightKey = context.getString(R.string.anilist_settings)
            )
        )
        list.add(
            SearchableSetting(
                title = context.getString(R.string.comments_button),
                desc = context.getString(R.string.comments_button_desc),
                icon = R.drawable.ic_round_comment_24,
                category = context.getString(R.string.accounts),
                breadcrumbs = "${context.getString(R.string.settings)} > ${context.getString(R.string.accounts)}",
                targetActivity = SettingsAccountActivity::class.java,
                highlightKey = context.getString(R.string.comments_button),
                isVisible = (Anilist.token != null)
            )
        )

        // 11. About Settings
        list.add(
            SearchableSetting(
                title = context.getString(R.string.faq),
                desc = context.getString(R.string.faq_desc),
                icon = R.drawable.ic_round_help_24,
                category = context.getString(R.string.about),
                breadcrumbs = "${context.getString(R.string.settings)} > ${context.getString(R.string.about)}",
                targetActivity = FAQActivity::class.java,
                highlightKey = context.getString(R.string.faq)
            )
        )
        list.add(
            SearchableSetting(
                title = context.getString(R.string.devs),
                desc = context.getString(R.string.devs_desc),
                icon = R.drawable.ic_round_accessible_forward_24,
                category = context.getString(R.string.about),
                breadcrumbs = "${context.getString(R.string.settings)} > ${context.getString(R.string.about)}",
                targetActivity = SettingsAboutActivity::class.java,
                highlightKey = context.getString(R.string.devs)
            )
        )
        list.add(
            SearchableSetting(
                title = context.getString(R.string.forks),
                desc = context.getString(R.string.forks_desc),
                icon = R.drawable.ic_round_restaurant_24,
                category = context.getString(R.string.about),
                breadcrumbs = "${context.getString(R.string.settings)} > ${context.getString(R.string.about)}",
                targetActivity = SettingsAboutActivity::class.java,
                highlightKey = context.getString(R.string.forks)
            )
        )
        list.add(
            SearchableSetting(
                title = context.getString(R.string.privacy_policy),
                desc = context.getString(R.string.privacy_policy_desc),
                icon = R.drawable.ic_incognito_24,
                category = context.getString(R.string.about),
                breadcrumbs = "${context.getString(R.string.settings)} > ${context.getString(R.string.about)}",
                targetActivity = SettingsAboutActivity::class.java,
                highlightKey = context.getString(R.string.privacy_policy)
            )
        )

        return list
    }

    fun search(context: Context, query: String): List<SearchableSetting> {
        val trimmed = query.trim().lowercase()
        if (trimmed.isEmpty()) return emptyList()

        val all = getAllSettings(context).filter { it.isVisible }

        val scored = all.mapNotNull { setting ->
            val title = setting.title.lowercase()
            val desc = setting.desc?.lowercase() ?: ""
            val category = setting.category.lowercase()

            var score = 0
            when {
                title == trimmed -> score += 100
                title.startsWith(trimmed) -> score += 75
                title.contains(trimmed) -> score += 50
                desc.contains(trimmed) -> score += 25
                category.contains(trimmed) -> score += 15
                else -> {
                    val queryWords = trimmed.split(" ").filter { it.isNotBlank() }
                    var wordMatches = 0
                    for (word in queryWords) {
                        if (title.contains(word) || desc.contains(word) || category.contains(word)) {
                            wordMatches++
                        }
                    }
                    if (wordMatches > 0) {
                        score += wordMatches * 10
                    }
                }
            }

            if (score > 0) setting to score else null
        }

        return scored.sortedByDescending { it.second }.map { it.first }
    }
}
