package com.vltv.play

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import org.json.JSONObject

class HomeDestaquesFilmesAdapter(
    private val context: Context,
    private val items: List<JSONObject>
) : RecyclerView.Adapter<HomeDestaquesFilmesAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val imgPoster: ImageView = v.findViewById(R.id.imgPoster)
        val tvName: TextView = v.findViewById(R.id.tvName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(context).inflate(R.layout.item_vod, parent, false)
        
        // ✅ CORREÇÃO CRÍTICA PARA HÍBRIDO (TV + CELULAR):
        v.isFocusable = true            // Necessário para o Controle Remoto da TV navegar
        v.isFocusableInTouchMode = false // ✅ ISSO GARANTE QUE NO CELULAR SEJA SÓ 1 CLIQUE
        
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        
        // Detecção Automática: Filme ou Série
        val isSeries = item.has("name") 
        val titulo = if (isSeries) item.getString("name") else item.optString("title")
        val poster = item.optString("poster_path", "")
        val fullPosterUrl = "https://image.tmdb.org/t/p/w500$poster"

        holder.tvName.text = titulo
        holder.tvName.visibility = View.GONE

        Glide.with(context)
            .load(fullPosterUrl)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .placeholder(R.drawable.bg_logo_placeholder)
            .centerCrop() 
            .into(holder.imgPoster)

        // ✅ CLIQUE (Funciona com Toque no Celular e "Enter" na TV)
        holder.itemView.setOnClickListener {
            val intent = Intent(context, DetailsActivity::class.java)
            intent.putExtra("stream_id", item.optInt("id")) 
            intent.putExtra("name", titulo)
            intent.putExtra("icon", fullPosterUrl)
            intent.putExtra("is_series", isSeries) 
            intent.putExtra("from_highlights", true) 
            context.startActivity(intent)
        }

        // ✅ VISUAL (Apenas muda o tamanho/zoom, não interfere no clique)
        holder.itemView.setOnFocusChangeListener { view, hasFocus ->
            view.animate().scaleX(if (hasFocus) 1.1f else 1.0f)
                .scaleY(if (hasFocus) 1.1f else 1.0f).setDuration(150).start()
            
            holder.tvName.visibility = if (hasFocus) View.VISIBLE else View.GONE
        }
    }

    override fun getItemCount() = items.size
}
