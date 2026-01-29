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
import org.json.JSONObject

class HomeDestaquesAdapter(
    private val context: Context,
    private val items: List<JSONObject>,
    private val onItemSelected: (JSONObject) -> Unit // Aciona o Preview/Sinopse na Home
) : RecyclerView.Adapter<HomeDestaquesAdapter.VH>() {

    private val handler = Handler(Looper.getMainLooper())
    private var runnable: Runnable? = null

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val imgPoster: ImageView = v.findViewById(R.id.imgPoster)
        val tvName: TextView = v.findViewById(R.id.tvName)
        val imgLogo: ImageView = v.findViewById(R.id.imgLogo) // Usa o layout que você já tem
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        // Usamos o seu layout item_vod que já está pronto e configurado
        val v = LayoutInflater.from(context).inflate(R.layout.item_vod, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        
        // Diferencia Filme (title) de Série (name) conforme a API do TMDB
        val titulo = if (item.has("title")) item.getString("title") else item.getString("name")
        val posterPath = item.optString("poster_path", "")
        val fullPosterUrl = "https://image.tmdb.org/t/p/w500$posterPath"

        holder.tvName.text = titulo
        holder.tvName.visibility = View.GONE // Mantém limpo, só aparece no foco
        holder.imgLogo.visibility = View.GONE // Inicialmente oculto

        Glide.with(context)
            .load(fullPosterUrl)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .placeholder(R.drawable.bg_logo_placeholder)
            .centerCrop()
            .into(holder.imgPoster)

        // ✅ LOGICA DE CLIQUE: Encaminha para a tela de Detalhes correta
        holder.itemView.setOnClickListener {
            val isMovie = item.has("title")
            val intent = if (isMovie) {
                Intent(context, DetailsActivity::class.java) // Activity de Filmes
            } else {
                Intent(context, SeriesDetailsActivity::class.java) // Activity de Séries
            }
            
            // Passando os dados exatamente como seu app espera
            intent.putExtra("stream_id", item.optString("id"))
            intent.putExtra("name", titulo)
            intent.putExtra("icon", fullPosterUrl)
            intent.putExtra("rating", item.optString("vote_average", "0.0"))
            context.startActivity(intent)
        }

        // ✅ LOGICA DE FOCO: Animação + Gatilho para Preview/Sinopse
        holder.itemView.setOnFocusChangeListener { view, hasFocus ->
            // Efeito visual de crescimento
            view.animate().scaleX(if (hasFocus) 1.1f else 1.0f)
                .scaleY(if (hasFocus) 1.1f else 1.0f).setDuration(150).start()
            
            holder.tvName.visibility = if (hasFocus) View.VISIBLE else View.GONE

            if (hasFocus) {
                // Se o foco parar por 1.5 segundos, avisa a Home para mostrar a sinopse/vídeo
                runnable?.let { handler.removeCallbacks(it) }
                runnable = Runnable { onItemSelected(item) }
                handler.postDelayed(runnable!!, 1500)
            } else {
                runnable?.let { handler.removeCallbacks(it) }
            }
        }
    }

    override fun getItemCount() = items.size
}
