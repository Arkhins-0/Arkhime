package com.arkhins.arkhime.media

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.arkhins.arkhime.connections.anilist.Anilist
import com.arkhins.arkhime.connections.anilist.GenresViewModel
import com.arkhins.arkhime.databinding.ActivityGenreBinding
import com.arkhins.arkhime.initActivity
import com.arkhins.arkhime.navBarHeight
import com.arkhins.arkhime.settings.saving.PrefManager
import com.arkhins.arkhime.settings.saving.PrefName
import com.arkhins.arkhime.statusBarHeight
import com.arkhins.arkhime.themes.ThemeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

class GenreActivity : AppCompatActivity() {
    private lateinit var binding: ActivityGenreBinding
    val model: GenresViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ThemeManager(this).applyTheme()
        binding = ActivityGenreBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initActivity(this)

        binding.genreContainer.updateLayoutParams<ViewGroup.MarginLayoutParams> { topMargin += statusBarHeight;bottomMargin += navBarHeight }
        val screenWidth = resources.displayMetrics.run { widthPixels / density }
        val type = intent.getStringExtra("type")
        if (type != null) {
            val adapter = GenreAdapter(type, true)
            model.doneListener = {
                MainScope().launch {
                    binding.mediaInfoGenresProgressBar.visibility = View.GONE
                }
            }
            if (model.genres != null) {
                adapter.genres = model.genres!!
                adapter.pos = ArrayList(model.genres!!.keys)
                if (model.done)
                    model.doneListener?.invoke()
            }
            binding.mediaInfoGenresRecyclerView.adapter = adapter
            binding.mediaInfoGenresRecyclerView.layoutManager =
                GridLayoutManager(this, (screenWidth / 156f).toInt())

            lifecycleScope.launch(Dispatchers.IO) {
                model.loadGenres(
                    Anilist.genres ?: loadLocalGenres() ?: arrayListOf()
                ) {
                    MainScope().launch {
                        adapter.addGenre(it)
                    }
                }
            }
        }
    }

    private fun loadLocalGenres(): ArrayList<String>? {
        val genres = PrefManager.getVal<Set<String>>(PrefName.GenresList)
            .toMutableList()
        return if (genres.isEmpty()) {
            null
        } else {
            //sort alphabetically
            genres.sort().let { genres as ArrayList<String> }
        }
    }
}