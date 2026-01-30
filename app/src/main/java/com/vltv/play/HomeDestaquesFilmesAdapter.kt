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

// ✅ REMOVIDO: Callback "onItemSelected". O adapter não precisa mais falar com a Home.
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
        v.isFocusable = true
        v.isFocusableInTouchMode = true
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        
        val titulo = if (item.has("title")) item.getString("title") else item.optString("name")
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

        // ✅ CLIQUE: Mantém a lógica de enviar para a DetailsActivity buscar o ID
        holder.itemView.setOnClickListener {
            val intent = Intent(context, DetailsActivity::class.java)
            intent.putExtra("stream_id", item.optInt("id")) 
            intent.putExtra("name", titulo)
            intent.putExtra("icon", fullPosterUrl)
            intent.putExtra("from_highlights", true) // Continua avisando que veio do site
            context.startActivity(intent)
        }

        // ✅ FOCO: Apenas efeito visual (Zoom). Não altera mais o banner.
        holder.itemView.setOnFocusChangeListener { view, hasFocus ->
            view.animate().scaleX(if (hasFocus) 1.1f else 1.0f)
                .scaleY(if (hasFocus) 1.1f else 1.0f).setDuration(150).start()
            
            holder.tvName.visibility = if (hasFocus) View.VISIBLE else View.GONE
        }
    }

    override fun getItemCount() = items.size
}
