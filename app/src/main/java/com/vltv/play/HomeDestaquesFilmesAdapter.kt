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
    private val onItemSelected: (JSONObject) -> Unit
) : RecyclerView.Adapter<HomeDestaquesFilmesAdapter.VH>() {

    private val handler = Handler(Looper.getMainLooper())
    private var runnable: Runnable? = null

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
        
        val titulo = if (item.has("title")) item.getString("title") else item.optString("name", "")
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

        holder.itemView.setOnClickListener {
            val intent = Intent(context, DetailsActivity::class.java)
            // Enviamos o ID do TMDB para a sinopse e logo
            intent.putExtra("stream_id", item.optInt("id")) 
            intent.putExtra("name", titulo)
            intent.putExtra("icon", fullPosterUrl)
            intent.putExtra("is_series", false)
            // Tag para a DetailsActivity saber que precisa buscar o ID de vídeo no servidor
            intent.putExtra("from_highlights", true) 
            context.startActivity(intent)
        }

        holder.itemView.setOnFocusChangeListener { view, hasFocus ->
            view.animate().scaleX(if (hasFocus) 1.1f else 1.0f)
                .scaleY(if (hasFocus) 1.1f else 1.0f).setDuration(150).start()
            
            if (hasFocus) {
                holder.tvName.visibility = View.VISIBLE
                runnable?.let { handler.removeCallbacks(it) }
                runnable = Runnable { onItemSelected(item) }
                handler.postDelayed(runnable!!, 1500)
            } else {
                holder.tvName.visibility = View.GONE
                runnable?.let { handler.removeCallbacks(it) }
            }
        }
    }

    override fun getItemCount() = items.size
}
