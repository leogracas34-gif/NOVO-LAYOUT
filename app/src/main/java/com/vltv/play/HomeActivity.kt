package com.vltv.play

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.bumptech.glide.Glide
import com.vltv.play.databinding.ActivityHomeBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import kotlin.random.Random
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.Firebase
import com.google.firebase.remoteconfig.remoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private val TMDB_API_KEY = "9b73f5dd15b8165b1b57419be2f29128"
    
    // Banner rotativo (Cima) continua usando TMDB para ficar bonito (apenas visual)
    private val bannerHandler = Handler(Looper.getMainLooper())
    private val bannerRunnable = object : Runnable {
        override fun run() {
            if (!MODO_FUTEBOL_ATIVO) {
                val temAnuncio = Firebase.remoteConfig.getBoolean("tem_anuncio")
                val mostrarAnuncio = temAnuncio && Random.nextBoolean()
                if (mostrarAnuncio) carregarAnuncioFirebase() else carregarBannerAlternado()
            }
            bannerHandler.postDelayed(this, 30000) 
        }
    }

    private var MODO_FUTEBOL_ATIVO = false 

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Configuração Firebase
        val remoteConfig = Firebase.remoteConfig
        remoteConfig.setConfigSettingsAsync(remoteConfigSettings { minimumFetchIntervalInSeconds = 60 })
        remoteConfig.fetchAndActivate().addOnCompleteListener(this) { task ->
            if (task.isSuccessful) {
                MODO_FUTEBOL_ATIVO = remoteConfig.getBoolean("modo_futebol")
                if (MODO_FUTEBOL_ATIVO) {
                    binding.tvBannerTitle.text = remoteConfig.getString("futebol_titulo")
                    binding.tvBannerOverview.text = remoteConfig.getString("futebol_descricao")
                    binding.imgBannerLogo.visibility = View.GONE
                    binding.tvBannerTitle.visibility = View.VISIBLE
                    Glide.with(this).load(remoteConfig.getString("futebol_imagem")).centerCrop().into(binding.imgBanner)
                }
            }
        }

        WindowCompat.getInsetsController(window, window.decorView)?.apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }

        setupClicks()
        
        bannerHandler.post(bannerRunnable)
        if (!MODO_FUTEBOL_ATIVO) carregarBannerAlternado()

        binding.cardBanner.requestFocus() 
        
        // ✅ AQUI ESTÁ A CORREÇÃO: Carrega direto do XTREAM CODES
        carregarRecentesDoServidor()
    }

    private fun carregarRecentesDoServidor() {
        val prefs = getSharedPreferences("vltv_prefs", MODE_PRIVATE)
        val user = prefs.getString("username", "") ?: ""
        val pass = prefs.getString("password", "") ?: ""

        if (user.isEmpty()) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. Baixa listas completas do servidor (Isso garante o ID correto)
                val filmesRaw = XtreamApi.service.getAllVodStreams(user, pass).execute().body() ?: emptyList()
                val seriesRaw = XtreamApi.service.getAllSeries(user, pass).execute().body() ?: emptyList()

                // 2. Ordena pelo ID (Maior ID = Mais novo) e pega os top 20 de cada
                val filmesRecentes = filmesRaw.sortedByDescending { it.stream_id }.take(20)
                val seriesRecentes = seriesRaw.sortedByDescending { it.series_id }.take(20)

                val listaMista = mutableListOf<JSONObject>()
                val maxLen = maxOf(filmesRecentes.size, seriesRecentes.size)

                // 3. Mistura (Intercala) e cria JSON para o Adapter
                for (i in 0 until maxLen) {
                    // Adiciona Filme
                    if (i < filmesRecentes.size) {
                        val f = filmesRecentes[i]
                        val obj = JSONObject()
                        obj.put("id", f.stream_id)             // ✅ ID REAL DO SERVIDOR
                        obj.put("name", f.name)
                        obj.put("poster_path", f.stream_icon)  // ✅ URL DA CAPA DO SERVIDOR
                        obj.put("is_series", false)
                        listaMista.add(obj)
                    }
                    // Adiciona Série
                    if (i < seriesRecentes.size) {
                        val s = seriesRecentes[i]
                        val obj = JSONObject()
                        obj.put("id", s.series_id)             // ✅ ID REAL DO SERVIDOR
                        obj.put("name", s.name)
                        obj.put("poster_path", s.cover)        // ✅ URL DA CAPA DO SERVIDOR
                        obj.put("is_series", true)
                        listaMista.add(obj)
                    }
                }

                withContext(Dispatchers.Main) {
                    binding.rvRecentAdditions.layoutManager = LinearLayoutManager(this@HomeActivity, LinearLayoutManager.HORIZONTAL, false)
                    
                    // Chama o Adapter (veja código abaixo)
                    val adapter = HomeDestaquesFilmesAdapter(this@HomeActivity, listaMista)
                    binding.rvRecentAdditions.adapter = adapter
                    
                    binding.rvRecentAdditions.isFocusable = true
                    binding.rvRecentAdditions.nextFocusUpId = binding.cardBanner.id
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun carregarAnuncioFirebase() {
        val remoteConfig = Firebase.remoteConfig
        val imgUrl = remoteConfig.getString("anuncio_imagem") 
        val titulo = remoteConfig.getString("anuncio_titulo")
        val desc = remoteConfig.getString("anuncio_descricao")
        if (imgUrl.isNotEmpty()) {
            binding.tvBannerTitle.text = titulo
            binding.tvBannerOverview.text = desc
            binding.tvBannerTitle.visibility = View.VISIBLE
            binding.imgBannerLogo.visibility = View.GONE
            Glide.with(this).load(imgUrl).centerCrop().into(binding.imgBanner)
        } else { carregarBannerAlternado() }
    }

    private fun carregarBannerAlternado() {
        if (MODO_FUTEBOL_ATIVO) return
        val tipo = "movie"
        val urlString = "https://api.themoviedb.org/3/trending/$tipo/day?api_key=$TMDB_API_KEY&language=pt-BR"
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val results = JSONObject(URL(urlString).readText()).getJSONArray("results")
                if (results.length() > 0) {
                    val item = results.getJSONObject(Random.nextInt(results.length()))
                    val titulo = if (item.has("title")) item.getString("title") else item.getString("name")
                    val overview = item.optString("overview", "")
                    val backdropPath = item.optString("backdrop_path", "")
                    val tmdbId = item.getString("id")
                    withContext(Dispatchers.Main) {
                        binding.tvBannerTitle.text = titulo
                        binding.tvBannerOverview.text = overview
                        binding.tvBannerTitle.visibility = View.VISIBLE
                        Glide.with(this@HomeActivity)
                            .load("https://image.tmdb.org/t/p/original$backdropPath")
                            .placeholder(R.drawable.bg_logo_placeholder)
                            .centerCrop().into(binding.imgBanner)
                        buscarLogoOverlayHome(tmdbId, tipo)
                    }
                }
            } catch (e: Exception) { }
        }
    }

    private fun buscarLogoOverlayHome(tmdbId: String, tipo: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val imagesUrl = "https://api.themoviedb.org/3/$tipo/$tmdbId/images?api_key=$TMDB_API_KEY&include_image_language=pt,en,null"
                val imagesObj = JSONObject(URL(imagesUrl).readText())
                if (imagesObj.has("logos") && imagesObj.getJSONArray("logos").length() > 0) {
                    val logoPath = imagesObj.getJSONArray("logos").getJSONObject(0).getString("file_path")
                    withContext(Dispatchers.Main) {
                        binding.tvBannerTitle.visibility = View.GONE
                        binding.imgBannerLogo.visibility = View.VISIBLE
                        Glide.with(this@HomeActivity).load("https://image.tmdb.org/t/p/w500$logoPath").into(binding.imgBannerLogo)
                    }
                } else {
                     withContext(Dispatchers.Main) {
                        binding.tvBannerTitle.visibility = View.VISIBLE
                        binding.imgBannerLogo.visibility = View.GONE
                    }
                }
            } catch (e: Exception) { }
        }
    }

    private fun setupClicks() {
        binding.etSearch.setOnClickListener { startActivity(Intent(this, SearchActivity::class.java).putExtra("initial_query", "")) }
        val menuButtons = listOf(binding.etSearch, binding.cardLiveTv, binding.cardMovies, binding.cardSeries, binding.cardKids, binding.btnSettings)
        menuButtons.forEach { btn ->
            btn.isFocusable = true
            btn.setOnFocusChangeListener { _, hasFocus ->
                btn.scaleX = if (hasFocus) 1.10f else 1f
                btn.scaleY = if (hasFocus) 1.10f else 1f
                btn.alpha = if (hasFocus) 1f else 0.8f
            }
        }
        binding.cardBanner.nextFocusDownId = binding.rvRecentAdditions.id
        binding.cardLiveTv.setOnClickListener { startActivity(Intent(this, LiveTvActivity::class.java)) }
        binding.cardMovies.setOnClickListener { startActivity(Intent(this, VodActivity::class.java)) }
        binding.cardSeries.setOnClickListener { startActivity(Intent(this, SeriesActivity::class.java)) }
        binding.cardKids.setOnClickListener { startActivity(Intent(this, KidsActivity::class.java)) }
        binding.btnSettings.setOnClickListener {
            val itens = arrayOf("Meus downloads", "Configurações", "Sair")
            AlertDialog.Builder(this).setTitle("Opções").setItems(itens) { _, which ->
                when (which) {
                    0 -> startActivity(Intent(this, DownloadsActivity::class.java))
                    1 -> startActivity(Intent(this, SettingsActivity::class.java))
                    2 -> mostrarDialogoSair()
                }
            }.show()
        }
        binding.cardBanner.setOnClickListener { if (MODO_FUTEBOL_ATIVO) startActivity(Intent(this, LiveTvActivity::class.java)) }
    }

    override fun onResume() { super.onResume(); if (isTelevisionDevice()) binding.cardBanner.requestFocus() }

    private fun isTelevisionDevice(): Boolean {
        return packageManager.hasSystemFeature("android.software.leanback") || 
               packageManager.hasSystemFeature("android.hardware.type.television")
    }

    override fun onDestroy() { super.onDestroy(); bannerHandler.removeCallbacks(bannerRunnable) }

    private fun mostrarDialogoSair() {
        AlertDialog.Builder(this).setTitle("Sair").setMessage("Deseja sair?")
            .setPositiveButton("Sim") { _, _ -> 
                getSharedPreferences("vltv_prefs", Context.MODE_PRIVATE).edit().clear().apply()
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }.setNegativeButton("Não", null).show()
    }
}
