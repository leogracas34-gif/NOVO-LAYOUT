package com.vltv.play

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
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
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager

// --- IMPORTS FIREBASE ATUALIZADOS PARA FIREBASE 34 ---
import com.google.firebase.Firebase
import com.google.firebase.remoteconfig.remoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private val TMDB_API_KEY = "9b73f5dd15b8165b1b57419be2f29128"
    
    private var ALGUEM_FOCADO_NA_LISTA = false // Controle para pausar banner quando usuário navega

    private val bannerHandler = Handler(Looper.getMainLooper())
    private val bannerRunnable = object : Runnable {
        override fun run() {
            if (!MODO_FUTEBOL_ATIVO && !ALGUEM_FOCADO_NA_LISTA) {
                // Sorteia entre Anúncio (se configurado no Firebase) e Filme
                val deveMostrarAnuncio = Random.nextBoolean() // 50% chance
                if (deveMostrarAnuncio && Firebase.remoteConfig.getBoolean("tem_anuncio")) {
                    carregarAnuncioFirebase()
                } else {
                    carregarBannerAlternado()
                }
            }
            bannerHandler.postDelayed(this, 30000)
        }
    }

    private var MODO_FUTEBOL_ATIVO = false 

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // --- INICIALIZAÇÃO FIREBASE REMOTE CONFIG ---
        val remoteConfig = Firebase.remoteConfig
        val configSettings = remoteConfigSettings {
            minimumFetchIntervalInSeconds = 60 
        }
        remoteConfig.setConfigSettingsAsync(configSettings)
        
        // 1. IMPLEMENTAÇÃO DO FUTEBOL VIA FIREBASE
        remoteConfig.fetchAndActivate().addOnCompleteListener(this) { task ->
            if (task.isSuccessful) {
                MODO_FUTEBOL_ATIVO = remoteConfig.getBoolean("modo_futebol")
                if (MODO_FUTEBOL_ATIVO) {
                    val titulo = remoteConfig.getString("futebol_titulo")
                    val desc = remoteConfig.getString("futebol_descricao")
                    val imagem = remoteConfig.getString("futebol_imagem")
                    
                    binding.tvBannerTitle.text = titulo
                    binding.tvBannerOverview.text = desc
                    binding.tvBannerTitle.visibility = View.VISIBLE
                    binding.imgBannerLogo.visibility = View.GONE
                    
                    Glide.with(this).load(imagem).centerCrop().into(binding.imgBanner)
                }
            }
        }

        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController?.hide(WindowInsetsCompat.Type.systemBars())

        DownloadHelper.registerReceiver(this)
        setupClicks()
        
        // 2. IMPLEMENTAÇÃO DA SEÇÃO DE DESTAQUES E LANÇAMENTOS
        carregarDestaquesAbaixo("movie") 

        bannerHandler.post(bannerRunnable)
        
        // --- FOCO ROBUSTO INICIAL (CONTROLE REMOTO) ---
        binding.cardBanner.requestFocus() 
    }

    // FUNÇÃO ADICIONADA PARA LANÇAMENTOS E DESTAQUES
    private fun carregarDestaquesAbaixo(tipo: String) {
        val urlDestaques = "https://api.themoviedb.org/3/trending/$tipo/week?api_key=$TMDB_API_KEY&language=pt-BR"
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val jsonTxt = URL(urlDestaques).readText()
                val results = JSONObject(jsonTxt).getJSONArray("results")
                
                // ✅ Converte o JSONArray em uma lista de objetos para o Adapter
                val listaItens = mutableListOf<JSONObject>()
                for (i in 0 until results.length()) {
                    listaItens.add(results.getJSONObject(i))
                }

                withContext(Dispatchers.Main) {
                    binding.rvRecentAdditions.layoutManager = LinearLayoutManager(this@HomeActivity, LinearLayoutManager.HORIZONTAL, false)
                    
                    // ✅ Conecta o novo Adapter Único que criamos
                    val adapter = HomeDestaquesFilmesAdapter(this@HomeActivity, listaItens) { item ->
                        // 📺 Ao focar no item, atualiza o banner com a sinopse discretamente
                        ALGUEM_FOCADO_NA_LISTA = true
                        exibirPreviewNoBanner(item)
                    }
                    binding.rvRecentAdditions.adapter = adapter
                    
                    // ✅ CORREÇÃO BANNER CINZA: Carrega o primeiro item imediatamente se não houver futebol
                    if (listaItens.isNotEmpty() && !MODO_FUTEBOL_ATIVO) {
                         exibirPreviewNoBanner(listaItens[0])
                    }

                    binding.rvRecentAdditions.isFocusable = true
                    binding.rvRecentAdditions.nextFocusUpId = binding.cardBanner.id
                    
                    // Detecta quando perde o foco para voltar a rodar o banner aleatório
                    binding.rvRecentAdditions.setOnFocusChangeListener { _, hasFocus ->
                        if (!hasFocus) ALGUEM_FOCADO_NA_LISTA = false
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // ✅ NOVA FUNÇÃO: Atualiza o banner quando o foco para no lançamento
    private fun exibirPreviewNoBanner(item: JSONObject) {
        val titulo = if (item.has("title")) item.getString("title") else item.getString("name")
        val sinopse = item.optString("overview", "")
        val backdrop = item.optString("backdrop_path", "")
        val id = item.optString("id")
        val tipo = if (item.has("title")) "movie" else "tv"

        binding.tvBannerTitle.text = titulo
        binding.tvBannerOverview.text = sinopse
        
        Glide.with(this)
            .load("https://image.tmdb.org/t/p/original$backdrop")
            .placeholder(R.drawable.bg_logo_placeholder) // Placeholder para evitar cinza
            .centerCrop()
            .into(binding.imgBanner)

        // Busca a logo discretamente para o banner
        buscarLogoOverlayHome(id, tipo)
    }

    // ✅ NOVA FUNÇÃO: Carrega Anúncio do Firebase
    private fun carregarAnuncioFirebase() {
        val remoteConfig = Firebase.remoteConfig
        val imgUrl = remoteConfig.getString("anuncio_imagem") // Use a chave correta do seu Firebase
        val titulo = remoteConfig.getString("anuncio_titulo")
        val desc = remoteConfig.getString("anuncio_descricao")

        if (imgUrl.isNotEmpty()) {
            binding.tvBannerTitle.text = titulo
            binding.tvBannerOverview.text = desc
            binding.tvBannerTitle.visibility = View.VISIBLE
            binding.imgBannerLogo.visibility = View.GONE
            
            Glide.with(this).load(imgUrl).centerCrop().into(binding.imgBanner)
        } else {
            // Se não tiver anuncio configurado, volta pro banner normal
            carregarBannerAlternado()
        }
    }

    override fun onResume() {
        super.onResume()
        if (!MODO_FUTEBOL_ATIVO) carregarBannerAlternado()
        
        try {
            binding.etSearch.clearFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(binding.etSearch.windowToken, 0)
            
            if (isTelevisionDevice()) {
                binding.cardBanner.requestFocus()
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun isTelevisionDevice(): Boolean {
        return packageManager.hasSystemFeature("android.software.leanback") || 
               packageManager.hasSystemFeature("android.hardware.type.television")
    }

    override fun onDestroy() {
        super.onDestroy()
        bannerHandler.removeCallbacks(bannerRunnable)
    }

    private fun setupClicks() {
        binding.etSearch.setOnClickListener {
            startActivity(Intent(this, SearchActivity::class.java).putExtra("initial_query", ""))
        }

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
        binding.cardBanner.nextFocusLeftId = binding.cardLiveTv.id
        
        binding.rvRecentAdditions.nextFocusLeftId = binding.cardMovies.id

        binding.cardLiveTv.setOnClickListener { startActivity(Intent(this, LiveTvActivity::class.java).putExtra("SHOW_PREVIEW", true)) }
        binding.cardMovies.setOnClickListener { startActivity(Intent(this, VodActivity::class.java).putExtra("SHOW_PREVIEW", false)) }
        binding.cardSeries.setOnClickListener { startActivity(Intent(this, SeriesActivity::class.java).putExtra("SHOW_PREVIEW", false)) }
        binding.cardKids.setOnClickListener { startActivity(Intent(this, KidsActivity::class.java).putExtra("SHOW_PREVIEW", false)) }
        
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

        binding.cardBanner.setOnClickListener {
            if (MODO_FUTEBOL_ATIVO) {
                startActivity(Intent(this, LiveTvActivity::class.java))
            }
        }
    }

    private fun carregarBannerAlternado() {
        if (MODO_FUTEBOL_ATIVO) return

        val prefs = getSharedPreferences("vltv_home_prefs", Context.MODE_PRIVATE)
        val ultimoTipo = prefs.getString("ultimo_tipo_banner", "tv") ?: "tv"
        val tipoAtual = if (ultimoTipo == "tv") "movie" else "tv"
        prefs.edit().putString("ultimo_tipo_banner", tipoAtual).apply()

        val urlString = "https://api.themoviedb.org/3/trending/$tipoAtual/day?api_key=$TMDB_API_KEY&language=pt-BR"

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val jsonTxt = URL(urlString).readText()
                val json = JSONObject(jsonTxt)
                val results = json.getJSONArray("results")

                if (results.length() > 0) {
                    val randomIndex = Random.nextInt(results.length())
                    val item = results.getJSONObject(randomIndex)
                    val titulo = if (item.has("title")) item.getString("title") else item.getString("name")
                    val overview = item.optString("overview", "")
                    val backdropPath = item.optString("backdrop_path", "")
                    val tmdbId = item.getString("id")

                    withContext(Dispatchers.Main) {
                        binding.tvBannerTitle.text = titulo
                        binding.tvBannerOverview.text = overview
                        Glide.with(this@HomeActivity)
                            .load("https://image.tmdb.org/t/p/original$backdropPath")
                            .centerCrop()
                            .into(binding.imgBanner)
                        
                        buscarLogoOverlayHome(tmdbId, tipoAtual)
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
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
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.tvBannerTitle.visibility = View.VISIBLE
                    binding.imgBannerLogo.visibility = View.GONE
                }
            }
        }
    }

    private fun mostrarDialogoSair() {
        AlertDialog.Builder(this).setTitle("Sair").setMessage("Deseja sair?")
            .setPositiveButton("Sim") { _, _ -> 
                getSharedPreferences("vltv_prefs", Context.MODE_PRIVATE).edit().clear().apply()
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }.setNegativeButton("Não", null).show()
    }
}
