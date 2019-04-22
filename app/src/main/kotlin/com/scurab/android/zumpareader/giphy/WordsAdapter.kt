package com.scurab.android.zumpareader.giphy

import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import com.scurab.android.zumpareader.R

/**
 * Created by Scurab on 08/03/2018.
 */
class WordsAdapter(private val words: Array<String>) : RecyclerView.Adapter<WordViewHolder>() {

    var onItemClickListener: ((word: String) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WordViewHolder {
        val textView: TextView = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_giphy_word, parent, false)
                .let { it as TextView }
                .apply {
                    setOnClickListener({ onItemClickListener?.invoke(text.toString()) })
                }
        return WordViewHolder(textView)
    }

    override fun getItemCount(): Int = words.size

    override fun onBindViewHolder(holder: WordViewHolder, position: Int) {
        holder.textView.text = words[position]
    }
}

class WordViewHolder(val textView: TextView) : RecyclerView.ViewHolder(textView) {

}