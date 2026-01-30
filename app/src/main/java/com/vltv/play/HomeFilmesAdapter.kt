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

class HomeFilmesAdapter(
    private val context: Context,
    private val items: List<JSONObject>
) : RecyclerView.Adapter<HomeFilmesAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val imgPoster: ImageView = v.findViewById(R.id.imgPoster)
        val tvName: TextView = v.findViewById(R.id.tvName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(context).inflate(R.layout.item_vod, parent, false)
        v.isFocusable = true
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val titulo = item.optString("name")
        val imgUrl = item.optString("poster_path")

        holder.tvName.text = titulo
        holder.tvName.visibility = View.GONE

        Glide.with(context)
            .load(imgUrl)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .into(holder.imgPoster)

        holder.itemView.setOnClickListener {
            val intent = Intent(context, DetailsActivity::class.java)
            intent.putExtra("stream_id", item.optInt("id"))
            intent.putExtra("name", titulo)
            intent.putExtra("icon", imgUrl)
            intent.putExtra("is_series", false) // ✅ Garante que é Filme
            context.startActivity(intent)
        }

        holder.itemView.setOnFocusChangeListener { v, hasFocus ->
            v.animate().scaleX(if (hasFocus) 1.1f else 1.0f).scaleY(if (hasFocus) 1.1f else 1.0f).duration = 150
            holder.tvName.visibility = if (hasFocus) View.VISIBLE else View.GONE
        }
    }

    override fun getItemCount() = items.size
}
