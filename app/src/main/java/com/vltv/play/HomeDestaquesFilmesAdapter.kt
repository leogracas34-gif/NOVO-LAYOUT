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
import com.bumptech.glide.load.engine.DiskCacheStrategy
import org.json.JSONObject

class HomeDestaquesFilmesAdapter(
    private val context: Context,
    private val items: List<JSONObject>,
    private val onItemSelected: (JSONObject) -> Unit
) : RecyclerView.Adapter<HomeDestaquesFilmesAdapter.VH>() {

    private val handler = Handler(Looper.getMainLooper())
    private var runnable: Runnable? = null

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val imgPoster: ImageView = v.findViewById(R.id.imgPoster)
        val tvName: TextView = v.findViewById(R.id.tvName)
        val imgStar: ImageView = v.findViewById(R.id.imgLogo) // Indicador de Favorito
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(context).inflate(R.layout.item_vod, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]

        // ✅ CHAVES DA SUA API (VodStream)
        val streamId = item.optInt("stream_id")
        val titulo = item.optString("name").ifEmpty { item.optString("title") }
        val iconUrl = item.optString("stream_icon")

        holder.tvName.text = titulo
        holder.tvName.visibility = View.GONE
        holder.imgStar.visibility = View.GONE

        // ✅ LÓGICA DE FAVORITOS (Pasta vltv_favoritos como na sua DetailsActivity)
        val favs = context.getSharedPreferences("vltv_favoritos", Context.MODE_PRIVATE)
            .getStringSet("favoritos", emptySet())
        
        if (favs?.contains(streamId.toString()) == true) {
            holder.imgStar.visibility = View.VISIBLE
            holder.imgStar.setImageResource(android.R.drawable.btn_star_big_on)
            holder.imgStar.setColorFilter(Color.parseColor("#FFD700"))
        }

        // ✅ CARREGAMENTO DA CAPA
        Glide.with(context)
            .load(iconUrl)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .placeholder(android.R.color.darker_gray)
            .into(holder.imgPoster)

        // ✅ CLIQUE - ABRE DETALHES DE FILME
        holder.itemView.setOnClickListener {
            val intent = Intent(context, DetailsActivity::class.java)
            
            // Enviando as chaves exatas que a sua DetailsActivity.kt espera
            intent.putExtra("stream_id", streamId)
            intent.putExtra("name", titulo)
            intent.putExtra("icon", iconUrl)
            intent.putExtra("rating", item.optString("rating", "0.0"))
            
            // ✅ CRUCIAL: is_series = false para NÃO pedir episódio
            intent.putExtra("is_series", false) 
            
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }

        // ✅ FOCO - GATILHO PARA O PREVIEW NO BANNER
        holder.itemView.setOnFocusChangeListener { view, hasFocus ->
            // Efeito visual de foco
            view.animate().scaleX(if (hasFocus) 1.1f else 1.0f)
                .scaleY(if (hasFocus) 1.1f else 1.0f).setDuration(150).start()
            
            if (hasFocus) {
                holder.tvName.visibility = View.VISIBLE
                
                // Se ficar 2 segundos focado, avisa a HomeActivity para rodar o Preview
                runnable?.let { handler.removeCallbacks(it) }
                runnable = Runnable { 
                    onItemSelected(item) 
                }
                handler.postDelayed(runnable!!, 2000)
            } else {
                holder.tvName.visibility = View.GONE
                handler.removeCallbacksAndMessages(null)
            }
        }
    }

    override fun getItemCount() = items.size
}
