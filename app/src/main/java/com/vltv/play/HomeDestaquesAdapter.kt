package com.vltv.play

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.Priority
import com.bumptech.glide.load.engine.DiskCacheStrategy
import org.json.JSONObject
import kotlinx.coroutines.* // Necessário para o Job e Coroutines

class HomeDestaquesAdapter(
    private val context: Context,
    private val items: List<JSONObject>,
    private val onItemSelected: (JSONObject) -> Unit
) : RecyclerView.Adapter<HomeDestaquesAdapter.VH>() {

    private val handler = Handler(Looper.getMainLooper())
    private var runnable: Runnable? = null
    
    // Preferências de Cache (Logos e Textos)
    private val logoCache = context.getSharedPreferences("vltv_logos_cache", Context.MODE_PRIVATE)

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val imgPoster: ImageView = v.findViewById(R.id.imgPoster)
        val tvName: TextView = v.findViewById(R.id.tvName)
        val imgLogo: ImageView = v.findViewById(R.id.imgLogo) // Sua logo/estrela
        var job: Job? = null // ✅ PROTEÇÃO: Para o "pisca-pisca" (Anti-flicker)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(context).inflate(R.layout.item_vod, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        
        // ✅ 1. IDENTIFICAÇÃO DUPLA (FILME OU SÉRIE)
        // Verificamos se o seu servidor mandou 'stream_id' (filme) ou 'series_id' (série)
        val isMovie = item.has("stream_id") || item.optString("stream_type") == "movie"
        val titulo = item.optString("name").ifEmpty { item.optString("title") }
        val idReal = if (isMovie) item.optInt("stream_id") else item.optInt("series_id")

        // Reset de estado para evitar fantasmas ao reciclar a view
        holder.job?.cancel() 
        holder.tvName.text = titulo
        holder.tvName.visibility = View.GONE
        holder.imgLogo.setImageDrawable(null)
        holder.imgLogo.visibility = View.GONE

        // ✅ 2. LOGO FIXA (CACHE IMEDIATO)
        // Mesma lógica que você tem nas Activities de detalhes
        val cacheKey = if (isMovie) "movie_logo_$idReal" else "series_logo_$idReal"
        val cachedLogoUrl = logoCache.getString(cacheKey, null)

        if (cachedLogoUrl != null) {
            holder.tvName.visibility = View.GONE
            holder.imgLogo.visibility = View.VISIBLE
            Glide.with(context)
                .load(cachedLogoUrl)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .priority(Priority.HIGH)
                .into(holder.imgLogo)
        }

        // ✅ 3. LÓGICA DE FAVORITOS (ENVIANDO PARA A ABA CORRETA)
        // Aqui usamos os dois caminhos diferentes que seus arquivos exigem
        val isFav = if (isMovie) {
            val favs = context.getSharedPreferences("vltv_favoritos", Context.MODE_PRIVATE)
            favs.getStringSet("favoritos", emptySet())?.contains(idReal.toString()) == true
        } else {
            val favs = context.getSharedPreferences("vltv_prefs", Context.MODE_PRIVATE)
            favs.getStringSet("fav_series", emptySet())?.contains(idReal.toString()) == true
        }

        // Se for favorito, mostramos a estrela em cima do poster na Home
        if (isFav) {
            holder.imgLogo.visibility = View.VISIBLE
            holder.imgLogo.setImageResource(android.R.drawable.btn_star_big_on)
            holder.imgLogo.setColorFilter(Color.parseColor("#FFD700"))
        }

        // ✅ 4. CARREGAMENTO DO POSTER (ALTA PERFORMANCE)
        val posterUrl = item.optString("stream_icon").ifEmpty { 
            "https://image.tmdb.org/t/p/w500${item.optString("poster_path")}" 
        }

        Glide.with(context)
            .load(posterUrl)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .priority(Priority.IMMEDIATE)
            .placeholder(R.drawable.bg_logo_placeholder)
            .centerCrop()
            .into(holder.imgPoster)

        // ✅ 5. MONTAGEM DO CLIQUE (PLAY FUNCIONANDO)
        holder.itemView.setOnClickListener {
            val intent = if (isMovie) {
                Intent(context, DetailsActivity::class.java).apply {
                    putExtra("stream_id", idReal) // Int para o motor de Filmes
                    putExtra("is_series", false)
                }
            } else {
                Intent(context, SeriesDetailsActivity::class.java).apply {
                    putExtra("series_id", idReal) // Int para o motor de Séries
                    putExtra("is_series", true)
                }
            }

            // Flag de limpeza para matar o delay do nome anterior
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            
            intent.putExtra("name", titulo)
            intent.putExtra("icon", posterUrl)
            intent.putExtra("rating", item.optString("rating", "0.0"))
            
            context.startActivity(intent)
        }

        // ✅ 6. CONTROLE REMOTO + PREVIEW 1.5s
        holder.itemView.setOnFocusChangeListener { view, hasFocus ->
            // Efeito visual de foco para TV e Celular
            view.animate().scaleX(if (hasFocus) 1.1f else 1.0f)
                .scaleY(if (hasFocus) 1.1f else 1.0f).setDuration(150).start()
            
            // O nome só aparece se não houver logo fixa carregada
            if (cachedLogoUrl == null) {
                holder.tvName.visibility = if (hasFocus) View.VISIBLE else View.GONE
            }

            if (hasFocus) {
                runnable?.let { handler.removeCallbacks(it) }
                runnable = Runnable { onItemSelected(item) }
                handler.postDelayed(runnable!!, 1500) // 1.5 segundo para o PREVIEW
            } else {
                handler.removeCallbacksAndMessages(null)
            }
        }
    }

    override fun getItemCount() = items.size
}
