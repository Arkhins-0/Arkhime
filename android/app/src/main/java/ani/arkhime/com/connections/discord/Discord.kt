package ani.arkhime.com.connections.discord

import android.content.Context
import android.content.Intent
import android.widget.TextView
import ani.arkhime.com.R
import ani.arkhime.com.others.CustomBottomDialog
import ani.arkhime.com.settings.saving.PrefManager
import ani.arkhime.com.settings.saving.PrefName
import ani.arkhime.com.toast
import ani.arkhime.com.tryWith
import io.noties.markwon.Markwon
import io.noties.markwon.SoftBreakAddsNewLinePlugin
import java.io.File

object Discord {

    var token: String? = null
    var userid: String? = null
    var avatar: String? = null


    fun getSavedToken(): Boolean {
        token = PrefManager.getVal(
            PrefName.DiscordToken, null as String?
        )
        return token != null
    }

    fun saveToken(token: String) {
        PrefManager.setVal(PrefName.DiscordToken, token)
    }

    fun removeSavedToken(context: Context) {
        PrefManager.removeVal(PrefName.DiscordToken)

        tryWith(true) {
            val dir = File(context.filesDir?.parentFile, "app_webview")
            if (dir.deleteRecursively())
                toast(context.getString(R.string.discord_logout_success))
        }
    }

    private var rpc: RPC? = null


    fun warning(context: Context) = CustomBottomDialog().apply {
        title = context.getString(R.string.warning)
        val md = context.getString(R.string.discord_warning)
        addView(TextView(context).apply {
            val markWon =
                Markwon.builder(context).usePlugin(SoftBreakAddsNewLinePlugin.create()).build()
            markWon.setMarkdown(this, md)
        })

        setNegativeButton(context.getString(R.string.cancel)) {
            dismiss()
        }

        setPositiveButton(context.getString(R.string.login)) {
            dismiss()
            loginIntent(context)
        }
    }

    private fun loginIntent(context: Context) {
        val intent = Intent(context, Login::class.java)
        context.startActivity(intent)
    }

    const val application_Id = "1163925779692912771"
    const val small_Image: String =
        "https://arkhime.arkhins.com/web/arkhime-192.png"
    const val small_Image_AniList: String =
        "https://anilist.co/img/icons/android-chrome-512x512.png"
}