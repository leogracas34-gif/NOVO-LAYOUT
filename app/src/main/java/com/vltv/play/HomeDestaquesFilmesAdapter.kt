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
        // ✅ CONFIGURAÇÃO: 1 Clique no Celular, Foco na TV
        v.isFocusable = true
        v.isFocusableInTouchMode = false 
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        
        // Pega os dados que montamos na HomeActivity (DO SERVIDOR)
        val isSeries = item.optBoolean("is_series", false)
        val titulo = item.optString("name", "Sem Título")
        val posterPath = item.optString("poster_path", "")
        
        // ✅ Como vem do servidor, é um link completo (http...), então usamos direto.
        val imgUrl = posterPath

        holder.tvName.text = titulo
        holder.tvName.visibility = View.GONE

        Glide.with(context)
            .load(imgUrl)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .placeholder(R.drawable.bg_logo_placeholder)
            .centerCrop() 
            .into(holder.imgPoster)

        holder.itemView.setOnClickListener {
            // ✅ AQUI A MÁGICA ACONTECE:
            // Mandamos o ID do Servidor (ex: 14209) direto para a sua DetailsActivity.
            // Ela vai receber e dar play na hora.
            val intent = Intent(context, DetailsActivity::class.java)
            
            intent.putExtra("stream_id", item.optInt("id")) // ID Correto do Servidor
            intent.putExtra("name", titulo)
            intent.putExtra("icon", imgUrl)                 // Capa do Servidor
            intent.putExtra("is_series", isSeries)
            intent.putExtra("rating", "0.0")
            
            context.startActivity(intent)
        }

        holder.itemView.setOnFocusChangeListener { view, hasFocus ->
            view.animate().scaleX(if (hasFocus) 1.1f else 1.0f)
                .scaleY(if (hasFocus) 1.1f else 1.0f).setDuration(150).start()
            
            holder.tvName.visibility = if (hasFocus) View.VISIBLE else View.GONE
        }
    }

    override fun getItemCount() = items.size
}
