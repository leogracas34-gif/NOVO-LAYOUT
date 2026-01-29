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
import android.os.Handler
import android.os.Looper

class HomeDestaquesFilmesAdapter(
    private val context: Context,
    private val items: List<JSONObject>,
    private val onItemSelected: (JSONObject) -> Unit // Aciona o Preview no Banner
) : RecyclerView.Adapter<HomeDestaquesFilmesAdapter.VH>() {

    private val handler = Handler(Looper.getMainLooper())
    private var runnable: Runnable? = null

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val imgPoster: ImageView = v.findViewById(R.id.imgPoster)
        val tvName: TextView = v.findViewById(R.id.tvName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(context).inflate(R.layout.item_vod, parent, false)
        // Garante que o item seja focável pelo controle remoto
        v.isFocusable = true
        v.isFocusableInTouchMode = true
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        
        // 1. Extração de dados (Compatível com Espelhamento do Servidor)
        val titulo = when {
            item.has("name") -> item.getString("name")
            item.has("title") -> item.getString("title")
            else -> "Sem Título"
        }

        // Pega o ID Real do seu servidor para o Player não travar
        val serverId = when {
            item.has("stream_id") -> item.optInt("stream_id")
            item.has("id") -> item.optInt("id")
            else -> 0
        }

        val iconUrl = when {
            item.has("stream_icon") -> item.getString("stream_icon")
            item.has("cover") -> item.getString("cover")
            item.has("poster_path") -> "https://image.tmdb.org/t/p/w500${item.getString("poster_path")}"
            else -> ""
        }

        holder.tvName.text = titulo
        holder.tvName.visibility = View.GONE

        // Imagem com CenterCrop para não esticar/ficar gigante
        Glide.with(context)
            .load(iconUrl)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .placeholder(R.drawable.bg_logo_placeholder)
            .centerCrop() 
            .into(holder.imgPoster)

        // ✅ CLIQUE (Toque no Celular ou 'OK' no Controle)
        holder.itemView.setOnClickListener {
            val intent = Intent(context, DetailsActivity::class.java)
            
            // Enviamos o ID para as duas chaves para garantir que a DetailsActivity receba
            intent.putExtra("stream_id", serverId)
            intent.putExtra("id", serverId) 
            
            intent.putExtra("name", titulo)
            intent.putExtra("icon", iconUrl)
            intent.putExtra("is_series", false) // Força modo Filme
            
            context.startActivity(intent)
        }

        // ✅ LÓGICA DE FOCO (Controle Remoto) e SELEÇÃO (Preview)
        holder.itemView.setOnFocusChangeListener { view, hasFocus ->
            // Efeito visual de aumento (Feedback do controle remoto)
            view.animate().scaleX(if (hasFocus) 1.12f else 1.0f)
                .scaleY(if (hasFocus) 1.12f else 1.0f).setDuration(150).start()
            
            if (hasFocus) {
                holder.tvName.visibility = View.VISIBLE
                view.elevation = 10f
                
                // DISPARA O PREVIEW: Se o usuário parar o foco por 1.5s, roda o vídeo no banner
                runnable?.let { handler.removeCallbacks(it) }
                runnable = Runnable { 
                    onItemSelected(item) 
                }
                handler.postDelayed(runnable!!, 1500)
            } else {
                holder.tvName.visibility = View.GONE
                view.elevation = 0f
                runnable?.let { handler.removeCallbacks(it) }
            }
        }
    }

    override fun getItemCount() = items.size
}
