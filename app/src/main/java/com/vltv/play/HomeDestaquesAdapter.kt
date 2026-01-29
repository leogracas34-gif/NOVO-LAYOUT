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

class HomeDestaquesAdapter(
    private val context: Context,
    private val items: List<JSONObject>,
    private val onItemSelected: (JSONObject) -> Unit
) : RecyclerView.Adapter<HomeDestaquesAdapter.VH>() {

    private val handler = Handler(Looper.getMainLooper())
    private var runnable: Runnable? = null
    private val logoCache = context.getSharedPreferences("vltv_logos_cache", Context.MODE_PRIVATE)

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val imgPoster: ImageView = v.findViewById(R.id.imgPoster)
        val tvName: TextView = v.findViewById(R.id.tvName)
        val imgStar: ImageView = v.findViewById(R.id.imgLogo) // Usado como indicador de favorito
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(context).inflate(R.layout.item_vod, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val isMovie = item.has("title")
        val titulo = if (isMovie) item.optString("title") else item.optString("name")
        val streamId = item.optInt("id", 0)

        // 1. LIMPEZA E RESET (Evita delay e fantasmas)
        holder.tvName.text = titulo
        holder.tvName.visibility = View.GONE 
        holder.imgStar.visibility = View.GONE

        // 2. LOGO CACHE (O nome fixo que você pediu)
        val cacheKey = if (isMovie) "movie_logo_$streamId" else "series_logo_$streamId"
        val cachedLogo = logoCache.getString(cacheKey, null)
        
        if (cachedLogo != null) {
            holder.tvName.visibility = View.GONE
            // Se você tiver um ImageView para a logo no item_vod, use-o aqui. 
            // Caso contrário, o Glide carrega no lugar do texto se necessário.
        }

        // 3. ESTRELA INDIVIDUAL (Favoritos)
        val isFav = if (isMovie) {
            val favs = context.getSharedPreferences("vltv_favoritos", Context.MODE_PRIVATE)
                .getStringSet("favoritos", emptySet())
            favs?.contains(streamId.toString()) == true
        } else {
            val favs = context.getSharedPreferences("vltv_prefs", Context.MODE_PRIVATE)
                .getStringSet("fav_series", emptySet())
            favs?.contains(streamId.toString()) == true
        }

        if (isFav) {
            holder.imgStar.visibility = View.VISIBLE
            holder.imgStar.setImageResource(android.R.drawable.btn_star_big_on)
            holder.imgStar.setColorFilter(Color.parseColor("#FFD700"))
        }

        // 4. CARREGAMENTO DO POSTER (Alta Prioridade)
        val posterPath = item.optString("poster_path", "")
        val fullUrl = "https://image.tmdb.org/t/p/w500$posterPath"

        Glide.with(context)
            .load(fullUrl)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .priority(Priority.IMMEDIATE)
            .into(holder.imgPoster)

        // 5. CLIQUE CONFIGURADO PARA O PLAY FUNCIONAR
        holder.itemView.setOnClickListener {
            val intent = if (isMovie) Intent(context, DetailsActivity::class.java)
                         else Intent(context, SeriesDetailsActivity::class.java)

            // Flags para limpar a memória da tela anterior (Resolve o delay do nome)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)

            // Passagem de IDs como Inteiro (Crucial para o seu Player)
            if (isMovie) {
                intent.putExtra("stream_id", streamId)
            } else {
                intent.putExtra("series_id", streamId)
            }

            intent.putExtra("name", titulo)
            intent.putExtra("icon", fullUrl)
            intent.putExtra("rating", item.optString("vote_average", "0.0"))
            intent.putExtra("is_series", !isMovie)
            
            context.startActivity(intent)
        }

        // 6. CONTROLE REMOTO + PREVIEW 1.5s
        holder.itemView.setOnFocusChangeListener { view, hasFocus ->
            // Efeito visual de foco
            view.animate().scaleX(if (hasFocus) 1.1f else 1.0f)
                .scaleY(if (hasFocus) 1.1f else 1.0f).setDuration(150).start()
            
            holder.tvName.visibility = if (hasFocus) View.VISIBLE else View.GONE

            if (hasFocus) {
                runnable?.let { handler.removeCallbacks(it) }
                runnable = Runnable { onItemSelected(item) }
                handler.postDelayed(runnable!!, 1500) // 1.5 segundo para o preview
            } else {
                handler.removeCallbacksAndMessages(null)
            }
        }
    }

    override fun getItemCount() = items.size
}
