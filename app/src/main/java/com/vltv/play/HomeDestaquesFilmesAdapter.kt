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
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        
        // ✅ CHAVES DO SEU SERVIDOR (Baseado na sua API)
        val titulo = item.optString("name").ifEmpty { item.optString("title") }
        val streamId = item.optInt("stream_id") // ID REAL DO VÍDEO NO SERVIDOR
        val iconUrl = item.optString("stream_icon").ifEmpty { 
            "https://image.tmdb.org/t/p/w500${item.optString("poster_path")}" 
        }

        holder.tvName.text = titulo
        holder.tvName.visibility = View.GONE

        Glide.with(context)
            .load(iconUrl)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .placeholder(R.drawable.bg_logo_placeholder)
            .centerCrop() 
            .into(holder.imgPoster)

        holder.itemView.setOnClickListener {
            val intent = Intent(context, DetailsActivity::class.java)
            
            // ✅ AQUI O PLAY VAI FUNCIONAR:
            // Passamos o "stream_id" porque seu servidor e sua PlayerActivity
            // usam exatamente essa chave para localizar o arquivo .mp4/.ts
            intent.putExtra("stream_id", streamId) 
            intent.putExtra("name", titulo)
            intent.putExtra("icon", iconUrl)
            intent.putExtra("rating", item.optString("rating", "0.0"))
            intent.putExtra("is_series", false) 
            
            context.startActivity(intent)
        }

        holder.itemView.setOnFocusChangeListener { view, hasFocus ->
            view.animate().scaleX(if (hasFocus) 1.1f else 1.0f)
                .scaleY(if (hasFocus) 1.1f else 1.0f).setDuration(150).start()
            
            holder.tvName.visibility = if (hasFocus) View.VISIBLE else View.GONE

            if (hasFocus) {
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
