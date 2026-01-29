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

class HomeDestaquesAdapter(
    private val context: Context,
    private val items: List<JSONObject>,
    private val onItemSelected: (JSONObject) -> Unit
) : RecyclerView.Adapter<HomeDestaquesAdapter.VH>() {

    private val handler = Handler(Looper.getMainLooper())
    private var runnable: Runnable? = null

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val imgPoster: ImageView = v.findViewById(R.id.imgPoster)
        val tvName: TextView = v.findViewById(R.id.tvName)
        val imgStar: ImageView = v.findViewById(R.id.imgLogo)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(context).inflate(R.layout.item_vod, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]

        // ✅ LÓGICA DE IDENTIFICAÇÃO (EXTREMA IMPORTÂNCIA)
        // Verificamos se o objeto tem 'stream_id' (Filme) ou 'series_id' (Série)
        val isMovie = item.has("stream_id")
        val titulo = item.optString("name").ifEmpty { item.optString("title") }
        val iconUrl = item.optString("stream_icon").ifEmpty { item.optString("icon") }

        // Reset dos campos para não repetir info de outro filme ao rolar a lista
        holder.tvName.text = titulo
        holder.tvName.visibility = View.GONE
        holder.imgStar.visibility = View.GONE

        // ✅ CAMINHO DOS FAVORITOS (SEM MISTURAR)
        if (isMovie) {
            val streamId = item.optInt("stream_id")
            val favs = context.getSharedPreferences("vltv_favoritos", Context.MODE_PRIVATE)
                .getStringSet("favoritos", emptySet())
            if (favs?.contains(streamId.toString()) == true) {
                holder.imgStar.visibility = View.VISIBLE
                holder.imgStar.setImageResource(android.R.drawable.btn_star_big_on)
                holder.imgStar.setColorFilter(Color.parseColor("#FFD700"))
            }
        } else {
            val seriesId = item.optInt("series_id")
            val favs = context.getSharedPreferences("vltv_prefs", Context.MODE_PRIVATE)
                .getStringSet("fav_series", emptySet())
            if (favs?.contains(seriesId.toString()) == true) {
                holder.imgStar.visibility = View.VISIBLE
                holder.imgStar.setImageResource(android.R.drawable.btn_star_big_on)
                holder.imgStar.setColorFilter(Color.parseColor("#FFD700"))
            }
        }

        // CARREGAR A CAPA
        Glide.with(context)
            .load(iconUrl)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .into(holder.imgPoster)

        // ✅ O CLIQUE: AQUI É ONDE RESOLVEMOS A TELA PRETA E O ERRO DO EPISÓDIO
        holder.itemView.setOnClickListener {
            val intent: Intent
            if (isMovie) {
                // Se for filme, usa a Activity de Filmes e manda o ID correto
                intent = Intent(context, DetailsActivity::class.java)
                intent.putExtra("stream_id", item.optInt("stream_id"))
                intent.putExtra("is_series", false) // ✅ AQUI: Impede de pedir episódio
            } else {
                // Se for série, usa a Activity de Séries
                intent = Intent(context, SeriesDetailsActivity::class.java)
                intent.putExtra("series_id", item.optInt("series_id"))
                intent.putExtra("is_series", true) // ✅ AQUI: Ativa a aba de episódios
            }

            intent.putExtra("name", titulo)
            intent.putExtra("icon", iconUrl)
            intent.putExtra("rating", item.optString("rating", "0.0"))
            
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }

        // ✅ LÓGICA DO PREVIEW (BANNER DO TOPO)
        holder.itemView.setOnFocusChangeListener { view, hasFocus ->
            // Efeito de zoom na capa
            view.animate().scaleX(if (hasFocus) 1.1f else 1.0f).scaleY(if (hasFocus) 1.1f else 1.0f).setDuration(150).start()
            
            if (hasFocus) {
                holder.tvName.visibility = View.VISIBLE
                runnable?.let { handler.removeCallbacks(it) }
                runnable = Runnable { 
                    // Avisa a Home que o foco parou aqui. A Home vai decidir se mostra o Preview.
                    onItemSelected(item) 
                }
                handler.postDelayed(runnable!!, 2000) // 2 segundos para disparar o preview no banner
            } else {
                holder.tvName.visibility = View.GONE
                handler.removeCallbacksAndMessages(null)
            }
        }
    }

    override fun getItemCount() = items.size
}
