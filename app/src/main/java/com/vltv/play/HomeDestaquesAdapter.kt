package com.vltv.play

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.Priority
import org.json.JSONObject
import kotlinx.coroutines.*

class HomeDestaquesAdapter(
    private val context: Context,
    private val items: List<JSONObject>,
    private val onItemSelected: (JSONObject) -> Unit
) : RecyclerView.Adapter<HomeDestaquesAdapter.VH>() {

    private val handler = Handler(Looper.getMainLooper())
    private var runnable: Runnable? = null
    
    // Cache de Logos (Mesmo arquivo que as Activities de detalhes usam)
    private val logoCachePrefs = context.getSharedPreferences("vltv_logos_cache", Context.MODE_PRIVATE)

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val imgPoster: ImageView = v.findViewById(R.id.imgPoster)
        val tvName: TextView = v.findViewById(R.id.tvName)
        val imgLogo: ImageView = v.findViewById(R.id.imgLogo)
        var job: Job? = null // ✅ Proteção contra o "Pisca-Pisca"
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(context).inflate(R.layout.item_vod, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val isMovie = item.has("title")
        val titulo = if (isMovie) item.getString("title") else item.getString("name")
        val streamId = item.optInt("id")
        
        holder.job?.cancel() // ✅ Cancela busca anterior se você rodar a lista rápido
        
        holder.tvName.text = titulo
        holder.tvName.visibility = View.VISIBLE
        holder.imgLogo.visibility = View.GONE
        holder.imgLogo.setImageDrawable(null)

        val posterUrl = "https://image.tmdb.org/t/p/w500${item.optString("poster_path")}"

        // ✅ CARREGAMENTO IMEDIATO DO POSTER
        Glide.with(context)
            .load(posterUrl)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .priority(Priority.IMMEDIATE) // Prioridade máxima
            .placeholder(R.drawable.bg_logo_placeholder)
            .into(holder.imgPoster)

        // ✅ LÓGICA DE LOGO FIXA (Igual à tela de detalhes)
        val key = if (isMovie) "movie_logo_$streamId" else "series_logo_$streamId"
        val cachedLogoUrl = logoCachePrefs.getString(key, null)

        if (cachedLogoUrl != null) {
            holder.tvName.visibility = View.GONE
            holder.imgLogo.visibility = View.VISIBLE
            Glide.with(context).load(cachedLogoUrl).diskCacheStrategy(DiskCacheStrategy.ALL).into(holder.imgLogo)
        }

        holder.itemView.setOnClickListener {
            val intent = if (isMovie) Intent(context, DetailsActivity::class.java) 
                         else Intent(context, SeriesDetailsActivity::class.java)
            
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP) // Limpa o "fantasma" do filme anterior
            
            if (isMovie) intent.putExtra("stream_id", streamId)
            else intent.putExtra("series_id", streamId)
            
            intent.putExtra("name", titulo)
            intent.putExtra("icon", posterUrl)
            intent.putExtra("rating", item.optString("vote_average", "0.0"))
            context.startActivity(intent)
        }

        // ✅ PREVIEW E FOCO
        holder.itemView.setOnFocusChangeListener { view, hasFocus ->
            view.animate().scaleX(if (hasFocus) 1.1f else 1.0f).scaleY(if (hasFocus) 1.1f else 1.0f).setDuration(150).start()
            
            if (hasFocus) {
                runnable?.let { handler.removeCallbacks(it) }
                runnable = Runnable { onItemSelected(item) }
                handler.postDelayed(runnable!!, 1500)
            } else {
                handler.removeCallbacksAndMessages(null)
            }
        }
    }

    override fun getItemCount() = items.size
}
