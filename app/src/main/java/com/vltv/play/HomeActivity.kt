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

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private val TMDB_API_KEY = "9b73f5dd15b8165b1b57419be2f29128"
    
    // Timer para o Banner Rotativo
    private val bannerHandler = Handler(Looper.getMainLooper())
    private val bannerRunnable = object : Runnable {
        override fun run() {
            carregarBannerAlternado()
            bannerHandler.postDelayed(this, 30000) // Troca a cada 30 segundos
        }
    }

    // Altere para TRUE quando quiser fixar o banner de Futebol amanhã
    private val MODO_FUTEBOL_ATIVO = false 

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController?.hide(WindowInsetsCompat.Type.systemBars())

        DownloadHelper.registerReceiver(this)
        setupClicks()
        
        // Inicia o carrossel automático
        bannerHandler.post(bannerRunnable)
    }

    override fun onResume() {
        super.onResume()
        if (!MODO_FUTEBOL_ATIVO) carregarBannerAlternado()
        
        try {
            // Ajustado para não dar erro com o novo botão de busca
            binding.etSearch.clearFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(binding.etSearch.windowToken, 0)
            binding.cardBanner.requestFocus()
        } catch (e: Exception) { e.printStackTrace() }
    }

    override fun onDestroy() {
        super.onDestroy()
        bannerHandler.removeCallbacks(bannerRunnable) // Para o timer ao fechar o app
    }

    private fun setupClicks() {
        // Ação do novo botão de Lupa na lateral
        binding.etSearch.setOnClickListener {
            startActivity(Intent(this, SearchActivity::class.java).putExtra("initial_query", ""))
        }

        // Estilo de foco para os botões da lateral (Cores Vibrantes)
        val menuButtons = listOf(binding.etSearch, binding.cardLiveTv, binding.cardMovies, binding.cardSeries, binding.cardKids, binding.btnSettings)
        
        menuButtons.forEach { btn ->
            btn.isFocusable = true
            btn.setOnFocusChangeListener { _, hasFocus ->
                btn.scaleX = if (hasFocus) 1.10f else 1f
                btn.scaleY = if (hasFocus) 1.10f else 1f
                // Efeito de brilho ao focar (opcional)
                btn.alpha = if (hasFocus) 1f else 0.8f
            }
        }

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
                // AMANHÃ: Coloque aqui o Intent para abrir o canal do jogo direto
                Toast.makeText(this, "Abrindo Jogo Ao Vivo...", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun carregarBannerAlternado() {
        if (MODO_FUTEBOL_ATIVO) {
            exibirBannerFutebol()
            return
        }

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

    private fun exibirBannerFutebol() {
        binding.tvBannerTitle.text = "BRASILEIRÃO: FLAMENGO x ATLÉTICO"
        binding.tvBannerOverview.text = "Acompanhe o clássico ao vivo agora no Premiere!"
        binding.imgBannerLogo.visibility = View.GONE
        
        // Coloque aqui o link da imagem do estádio ou arte do jogo
        Glide.with(this).load("URL_DA_IMAGEM_DO_JOGO").centerCrop().into(binding.imgBanner)
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
