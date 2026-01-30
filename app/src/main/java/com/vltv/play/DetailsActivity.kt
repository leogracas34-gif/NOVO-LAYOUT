package com.vltv.play

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Normalizer // Necessário para a busca inteligente

// ✅ MANTIDO: EpisodeData
data class EpisodeData(
    val streamId: Int,
    val season: Int,
    val episode: Int,
    val title: String,
    val thumb: String
)

class DetailsActivity : AppCompatActivity() {

    private var streamId: Int = 0
    private var name: String = ""
    private var icon: String? = null
    private var rating: String = "0.0"
    private var isSeries: Boolean = false
    private var episodes: List<EpisodeData> = emptyList()
    
    private lateinit var imgPoster: ImageView
    private lateinit var tvTitle: TextView
    private lateinit var imgTitleLogo: ImageView 
    private lateinit var tvRating: TextView
    private lateinit var tvGenre: TextView
    private lateinit var tvCast: TextView
    private lateinit var tvPlot: TextView
    private lateinit var btnPlay: Button
    private lateinit var btnResume: Button
    private lateinit var btnFavorite: ImageButton
    private lateinit var btnDownloadArea: LinearLayout
    private lateinit var imgDownloadState: ImageView
    private lateinit var tvDownloadState: TextView
    private lateinit var imgBackground: ImageView
    private lateinit var tvEpisodesTitle: TextView
    private lateinit var recyclerEpisodes: RecyclerView
    private var tvYear: TextView? = null
    private var btnSettings: Button? = null

    private lateinit var episodesAdapter: EpisodesAdapter
    private enum class DownloadState { BAIXAR, BAIXANDO, BAIXADO }
    private var downloadState: DownloadState = DownloadState.BAIXAR
    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_details)

        configurarTelaTV()
        
        streamId = intent.getIntExtra("stream_id", 0)
        name = intent.getStringExtra("name") ?: ""
        icon = intent.getStringExtra("icon")
        rating = intent.getStringExtra("rating") ?: "0.0"
        isSeries = intent.getBooleanExtra("is_series", false)

        inicializarViews()
        carregarConteudo()
        setupEventos()
        setupEpisodesRecycler()

        tentarCarregarTextoCache()
        tentarCarregarLogoCache()
        
        // ==================================================================
        // 🛠️ A ÚNICA ALTERAÇÃO NO SEU CÓDIGO (PARA FUNCIONAR A HOME)
        // ==================================================================
        if (intent.getBooleanExtra("from_highlights", false)) {
            // Se veio da Home, o ID está "errado" (é do TMDB).
            // Precisamos achar o ID "certo" no servidor.
            btnPlay.text = "Sincronizando..."
            btnPlay.isEnabled = false
            buscarIdRealNoServidor(name)
        } else {
            // Se veio das categorias, o ID já está certo.
            sincronizarDadosTMDB() 
        }
    }

    // ✅ FUNÇÃO NOVA: Busca o ID real sem mudar sua estrutura
    private fun buscarIdRealNoServidor(nomeTMDB: String) {
        val prefs = getSharedPreferences("vltv_prefs", MODE_PRIVATE)
        val user = prefs.getString("username", "") ?: ""
        val pass = prefs.getString("password", "") ?: ""

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Decide se busca lista de Filmes ou Séries
                val call = if (isSeries) XtreamApi.service.getAllSeries(user, pass) 
                           else XtreamApi.service.getAllVodStreams(user, pass)

                val response = call.execute()
                
                if (response.isSuccessful) {
                    val lista = response.body() as List<*>
                    
                    // Limpa o nome para comparar (tira acentos e maiúsculas)
                    fun limpar(t: String) = Normalizer.normalize(t, Normalizer.Form.NFD)
                        .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "").lowercase()

                    val nomeAlvo = limpar(nomeTMDB)
                    var idEncontrado = 0

                    for (item in lista) {
                        val (n, id) = when (item) {
                            is VodStream -> Pair(item.name, item.stream_id)
                            is SeriesStream -> Pair(item.name, item.series_id)
                            else -> Pair("", 0)
                        }
                        
                        val nomeItemLimpo = limpar(n)
                        if (nomeItemLimpo.contains(nomeAlvo) || nomeAlvo.contains(nomeItemLimpo)) {
                            idEncontrado = id
                            break
                        }
                    }

                    withContext(Dispatchers.Main) {
                        if (idEncontrado != 0) {
                            streamId = idEncontrado // ✅ ATUALIZA O ID!
                            btnPlay.text = "Assistir"
                            btnPlay.isEnabled = true
                            Toast.makeText(this@DetailsActivity, "Sincronizado!", Toast.LENGTH_SHORT).show()
                            // Agora busca os dados visuais (logo/sinopse)
                            sincronizarDadosTMDB()
                        } else {
                            btnPlay.text = "Indisponível"
                            tvPlot.text = "Conteúdo ainda não disponível no servidor."
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { btnPlay.text = "Erro Conexão" }
            }
        }
    }

    private fun configurarTelaTV() {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController?.hide(WindowInsetsCompat.Type.systemBars())
        if (isTelevisionDevice()) {
            requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    private fun inicializarViews() {
        imgPoster = findViewById(R.id.imgPoster)
        tvTitle = findViewById(R.id.tvTitle)
        imgTitleLogo = findViewById(R.id.imgTitleLogo) 
        
        tvTitle.visibility = View.INVISIBLE 

        tvRating = findViewById(R.id.tvRating)
        tvGenre = findViewById(R.id.tvGenre)
        tvCast = findViewById(R.id.tvCast)
        tvPlot = findViewById(R.id.tvPlot)
        btnPlay = findViewById(R.id.btnPlay)
        btnResume = findViewById(R.id.btnResume)
        btnFavorite = findViewById(R.id.btnFavorite)
        btnDownloadArea = findViewById(R.id.btnDownloadArea)
        imgDownloadState = findViewById(R.id.imgDownloadState)
        tvDownloadState = findViewById(R.id.tvDownloadState)
        imgBackground = findViewById(R.id.imgBackground)
        tvEpisodesTitle = findViewById(R.id.tvEpisodesTitle)
        recyclerEpisodes = findViewById(R.id.recyclerEpisodes)
        tvYear = findViewById(R.id.tvYear)
        btnSettings = findViewById(R.id.btnSettings)

        if (isTelevisionDevice()) btnDownloadArea.visibility = View.GONE
        btnPlay.isFocusable = true
        btnResume.isFocusable = true
        btnFavorite.isFocusable = true
        btnPlay.requestFocus()
    }

    private fun carregarConteudo() {
        tvRating.text = "⭐ $rating"
        tvPlot.text = "Buscando detalhes..."
        tvGenre.text = "Gênero: ..."
        tvCast.text = "Elenco:"
        
        Glide.with(this).load(icon).diskCacheStrategy(DiskCacheStrategy.ALL).into(imgPoster)
        Glide.with(this).load(icon).centerCrop().diskCacheStrategy(DiskCacheStrategy.ALL).into(imgBackground)

        val isFavInicial = getFavMovies(this).contains(streamId)
        atualizarIconeFavorito(isFavInicial)

        if (isSeries) carregarEpisodios() else {
            tvEpisodesTitle.visibility = View.GONE
            recyclerEpisodes.visibility = View.GONE
        }
        verificarResume()
        restaurarEstadoDownload()
    }

    private fun tentarCarregarTextoCache() {
        val prefs = getSharedPreferences("vltv_text_cache", Context.MODE_PRIVATE)
        prefs.getString("title_$streamId", null)?.let { tvTitle.text = it }
        prefs.getString("plot_$streamId", null)?.let { tvPlot.text = it }
        prefs.getString("cast_$streamId", null)?.let { tvCast.text = it }
        prefs.getString("genre_$streamId", null)?.let { tvGenre.text = it }
        prefs.getString("year_$streamId", null)?.let { tvYear?.text = it }
    }

    private fun tentarCarregarLogoCache() {
        val prefs = getSharedPreferences("vltv_logos_cache", Context.MODE_PRIVATE)
        val cachedUrl = prefs.getString("movie_logo_$streamId", null)
        if (cachedUrl != null) {
            tvTitle.visibility = View.GONE
            imgTitleLogo.visibility = View.VISIBLE
            Glide.with(this).load(cachedUrl).diskCacheStrategy(DiskCacheStrategy.ALL).into(imgTitleLogo)
        } else {
            tvTitle.visibility = View.VISIBLE 
        }
    }

    private fun sincronizarDadosTMDB() {
        val apiKey = "9b73f5dd15b8165b1b57419be2f29128"
        val type = if (isSeries) "tv" else "movie"
        
        var clean = name.replace(Regex("[\\(\\[\\{].*?[\\)\\]\\}]"), "").replace(Regex("\\b\\d{4}\\b"), "").trim()
        val lixo = listOf("FHD", "HD", "4K", "H265", "LEG", "DUBLADO")
        lixo.forEach { clean = clean.replace(it, "", ignoreCase = true) }
        val encoded = URLEncoder.encode(clean.replace(Regex("\\s+"), " "), "UTF-8")
        
        val url = "https://api.themoviedb.org/3/search/$type?api_key=$apiKey&query=$encoded&language=pt-BR"

        client.newCall(Request.Builder().url(url).build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { runOnUiThread { tvTitle.visibility = View.VISIBLE } }
            override fun onResponse(call: Call, response: Response) {
                val body = response.body()?.string() ?: return
                try {
                    val results = JSONObject(body).optJSONArray("results")
                    if (results != null && results.length() > 0) {
                        val selected = results.getJSONObject(0)
                        val idTmdb = selected.getInt("id")
                        
                        buscarLogoTMDB(idTmdb, type, apiKey) 
                        buscarDetalhesCompletos(idTmdb, type, apiKey)

                        runOnUiThread {
                            val tOficial = if (type == "movie") selected.getString("title") else selected.getString("name")
                            val sinopse = selected.optString("overview")
                            val date = if (isSeries) selected.optString("first_air_date") else selected.optString("release_date")
                            
                            tvTitle.text = tOficial
                            if (sinopse.isNotEmpty()) tvPlot.text = sinopse
                            if (date.length >= 4) tvYear?.text = date.substring(0, 4)

                            getSharedPreferences("vltv_text_cache", Context.MODE_PRIVATE).edit()
                                .putString("title_$streamId", tOficial).putString("plot_$streamId", sinopse)
                                .putString("year_$streamId", if (date.length >= 4) date.substring(0, 4) else "").apply()
                        }
                    } else { runOnUiThread { tvTitle.visibility = View.VISIBLE } }
                } catch (e: Exception) { runOnUiThread { tvTitle.visibility = View.VISIBLE } }
            }
        })
    }

    private fun buscarLogoTMDB(id: Int, type: String, key: String) {
        val imagesUrl = "https://api.themoviedb.org/3/$type/$id/images?api_key=$key&include_image_language=pt,en,null"
        client.newCall(Request.Builder().url(imagesUrl).build()).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                val body = response.body()?.string() ?: return
                try {
                    val logos = JSONObject(body).optJSONArray("logos")
                    if (logos != null && logos.length() > 0) {
                        val path = logos.getJSONObject(0).getString("file_path")
                        val finalUrl = "https://image.tmdb.org/t/p/w500$path"
                        getSharedPreferences("vltv_logos_cache", Context.MODE_PRIVATE).edit().putString("movie_logo_$streamId", finalUrl).apply()
                        runOnUiThread {
                            tvTitle.visibility = View.GONE
                            imgTitleLogo.visibility = View.VISIBLE
                            Glide.with(this@DetailsActivity).load(finalUrl).into(imgTitleLogo)
                        }
                    }
                } catch (e: Exception) {}
            }
            override fun onFailure(call: Call, e: IOException) {}
        })
    }

    private fun buscarDetalhesCompletos(id: Int, type: String, key: String) {
        val url = "https://api.themoviedb.org/3/$type/$id?api_key=$key&append_to_response=credits&language=pt-BR"
        client.newCall(Request.Builder().url(url).build()).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                val body = response.body()?.string() ?: return
                try {
                    val d = JSONObject(body)
                    val gs = d.optJSONArray("genres")
                    val genresList = mutableListOf<String>()
                    if (gs != null) for (i in 0 until gs.length()) genresList.add(gs.getJSONObject(i).getString("name"))
                    
                    val castArray = d.optJSONObject("credits")?.optJSONArray("cast")
                    val castNamesList = mutableListOf<String>()
                    if (castArray != null) {
                        val limit = if (castArray.length() > 10) 10 else castArray.length()
                        for (i in 0 until limit) castNamesList.add(castArray.getJSONObject(i).getString("name"))
                    }
                    runOnUiThread {
                        val g = "Gênero: ${genresList.joinToString(", ")}"
                        val e = "Elenco: ${castNamesList.joinToString(", ")}" 
                        tvGenre.text = g
                        tvCast.text = e
                        getSharedPreferences("vltv_text_cache", Context.MODE_PRIVATE).edit()
                            .putString("genre_$streamId", g).putString("cast_$streamId", e).apply()
                    }
                } catch (e: Exception) {}
            }
            override fun onFailure(call: Call, e: IOException) {}
        })
    }

    private fun setupEpisodesRecycler() {
        episodesAdapter = EpisodesAdapter { episode ->
            val intent = Intent(this, PlayerActivity::class.java)
            intent.putExtra("stream_id", episode.streamId).putExtra("stream_type", "series")
            intent.putExtra("channel_name", "${name} - S${episode.season}:E${episode.episode}")
            startActivity(intent)
        }
        recyclerEpisodes.apply {
            layoutManager = if (isTelevisionDevice()) GridLayoutManager(this@DetailsActivity, 6) else LinearLayoutManager(this@DetailsActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = episodesAdapter
        }
    }

    private fun carregarEpisodios() {
        episodes = listOf(EpisodeData(101, 1, 1, "Episódio 1", icon ?: ""))
        episodesAdapter.submitList(episodes)
        tvEpisodesTitle.visibility = View.VISIBLE
        recyclerEpisodes.visibility = View.VISIBLE
    }

    private fun setupEventos() {
        val zoomFocus = View.OnFocusChangeListener { v, hasFocus ->
            v.scaleX = if (hasFocus) 1.1f else 1.0f
            v.scaleY = if (hasFocus) 1.1f else 1.0f
        }
        btnPlay.onFocusChangeListener = zoomFocus
        btnResume.onFocusChangeListener = zoomFocus
        btnFavorite.onFocusChangeListener = zoomFocus
        btnFavorite.setOnClickListener { toggleFavorite() }
        
        // ✅ BOTÃO ASSISTIR SEGURO
        btnPlay.setOnClickListener { 
            if (btnPlay.isEnabled) abrirPlayer(false) 
            else Toast.makeText(this, "Aguarde a sincronização...", Toast.LENGTH_SHORT).show()
        }
        
        btnResume.setOnClickListener { abrirPlayer(true) }
        btnDownloadArea.setOnClickListener { handleDownloadClick() }
        btnSettings?.setOnClickListener { mostrarConfiguracoes() }
    }

    private fun getFavMovies(context: Context): MutableList<Int> {
        val prefs = context.getSharedPreferences("vltv_favoritos", Context.MODE_PRIVATE)
        return prefs.getStringSet("favoritos", emptySet())?.mapNotNull { it.toIntOrNull() }?.toMutableList() ?: mutableListOf()
    }

    private fun saveFavMovies(context: Context, favs: List<Int>) {
        val prefs = context.getSharedPreferences("vltv_favoritos", Context.MODE_PRIVATE)
        prefs.edit().putStringSet("favoritos", favs.map { it.toString() }.toSet()).apply()
    }

    private fun atualizarIconeFavorito(isFavorite: Boolean) {
        if (isFavorite) {
            btnFavorite.setImageResource(android.R.drawable.btn_star_big_on)
            btnFavorite.setColorFilter(android.graphics.Color.parseColor("#FFD700"))
        } else {
            btnFavorite.setImageResource(android.R.drawable.btn_star_big_off)
            btnFavorite.clearColorFilter()
        }
    }
    
    private fun toggleFavorite() {
        val favs = getFavMovies(this)
        val isFav = favs.contains(streamId)
        if (isFav) favs.remove(streamId) else favs.add(streamId)
        saveFavMovies(this, favs)
        atualizarIconeFavorito(!isFav)
    }

    private fun verificarResume() {
        val pos = getSharedPreferences("vltv_prefs", Context.MODE_PRIVATE).getLong("movie_resume_${streamId}_pos", 0L)
        btnResume.visibility = if (pos > 30000L) View.VISIBLE else View.GONE
    }

    private fun abrirPlayer(usarResume: Boolean) {
        val intent = Intent(this, PlayerActivity::class.java)
        intent.putExtra("stream_id", streamId).putExtra("stream_type", if (isSeries) "series" else "movie").putExtra("channel_name", name)
        if (usarResume) {
            val pos = getSharedPreferences("vltv_prefs", Context.MODE_PRIVATE).getLong("movie_resume_${streamId}_pos", 0L)
            intent.putExtra("start_position_ms", pos)
        }
        startActivity(intent)
    }

    private fun restaurarEstadoDownload() {
        val estado = getSharedPreferences("vltv_downloads", Context.MODE_PRIVATE).getString("download_state_$streamId", "BAIXAR")
        try { downloadState = DownloadState.valueOf(estado ?: "BAIXAR") } catch(e: Exception) { downloadState = DownloadState.BAIXAR }
        atualizarUI_download()
    }

    private fun iniciarDownload() {
        downloadState = DownloadState.BAIXANDO; atualizarUI_download()
        Handler(Looper.getMainLooper()).postDelayed({
            downloadState = DownloadState.BAIXADO; atualizarUI_download()
            getSharedPreferences("vltv_downloads", Context.MODE_PRIVATE).edit().putString("download_state_$streamId", "BAIXADO").apply()
        }, 3000)
    }

    private fun atualizarUI_download() {
        when (downloadState) {
            DownloadState.BAIXAR -> { imgDownloadState.setImageResource(android.R.drawable.stat_sys_download); tvDownloadState.text = "Baixar" }
            DownloadState.BAIXANDO -> { imgDownloadState.setImageResource(android.R.drawable.ic_media_play); tvDownloadState.text = "Baixando..." }
            DownloadState.BAIXADO -> { imgDownloadState.setImageResource(android.R.drawable.stat_sys_download_done); tvDownloadState.text = "Baixado" }
        }
    }

    private fun handleDownloadClick() { if (downloadState == DownloadState.BAIXAR) iniciarDownload() }
    private fun mostrarConfiguracoes() {
        val p = arrayOf("ExoPlayer", "VLC", "MX Player")
        AlertDialog.Builder(this).setTitle("Player").setItems(p) { _, i -> getSharedPreferences("vltv_prefs", Context.MODE_PRIVATE).edit().putString("player_preferido", p[i]).apply() }.show()
    }

    private fun isTelevisionDevice() = packageManager.hasSystemFeature("android.software.leanback") || packageManager.hasSystemFeature("android.hardware.type.television")

    inner class EpisodesAdapter(private val onEpisodeClick: (EpisodeData) -> Unit) : ListAdapter<EpisodeData, EpisodesAdapter.ViewHolder>(DiffCallback) {
        override fun onCreateViewHolder(p: ViewGroup, t: Int) = ViewHolder(LayoutInflater.from(p.context).inflate(R.layout.item_episode, p, false))
        override fun onBindViewHolder(h: ViewHolder, p: Int) = h.bind(getItem(p))
        inner class ViewHolder(val v: View) : RecyclerView.ViewHolder(v) {
            fun bind(e: EpisodeData) {
                v.isFocusable = true
                v.findViewById<TextView>(R.id.tvEpisodeTitle).text = "S${e.season}E${e.episode}: ${e.title}"
                Glide.with(v.context).load(e.thumb).centerCrop().into(v.findViewById(R.id.imgEpisodeThumb))
                v.setOnClickListener { onEpisodeClick(e) }
            }
        }
    }

    companion object {
        private object DiffCallback : DiffUtil.ItemCallback<EpisodeData>() {
            override fun areItemsTheSame(o: EpisodeData, n: EpisodeData) = o.streamId == n.streamId
            override fun areContentsTheSame(o: EpisodeData, n: EpisodeData) = o == n
        }
    }

    override fun onResume() { super.onResume(); restaurarEstadoDownload(); verificarResume() }
}
