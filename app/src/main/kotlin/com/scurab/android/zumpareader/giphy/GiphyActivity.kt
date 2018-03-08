package com.scurab.android.zumpareader.giphy

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.StaggeredGridLayoutManager
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ProgressBar
import com.giphy.sdk.core.models.Media
import com.giphy.sdk.core.network.api.GPHApiClient
import com.scurab.android.zumpareader.R
import com.scurab.android.zumpareader.ZumpaReaderApp
import com.scurab.android.zumpareader.extension.hideKeyboard
import com.scurab.android.zumpareader.extension.toViewVisibility
import org.jetbrains.anko.find
import org.jetbrains.anko.onEditorAction

/**
 * Created by jbruchanov on 08/03/2018.
 */
class GiphyActivity : AppCompatActivity() {

    private val progressBar: ProgressBar by lazy { find<ProgressBar>(R.id.progress_bar) }
    private val giphySearch: EditText by lazy { find<EditText>(R.id.giphy_search) }
    private val recyclerView: RecyclerView by lazy { find<RecyclerView>(R.id.recycler_view) }
    private val recyclerViewHorizontal: RecyclerView by lazy { find<RecyclerView>(R.id.recycler_view_horizontal) }

    private val giphyAPI: GPHApiClient by lazy { (application as ZumpaReaderApp).giphyAPI }
    private val adapter: GiphyAdapter by lazy {
        GiphyAdapter(giphyAPI).apply {
            onItemClickListener = { dispatchItemClicked(it) }
            onLoadingListener = { progressBar.visibility = it.toViewVisibility(View.INVISIBLE) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_giphy)

        recyclerViewHorizontal.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        recyclerViewHorizontal.adapter = WordsAdapter(resources.getStringArray(R.array.giphy)).apply {
            onItemClickListener = {
                adapter.search(it)
                giphySearch.hideKeyboard()
            }
        }
        recyclerViewHorizontal.addItemDecoration(DividerItemDecoration(this, LinearLayoutManager.HORIZONTAL))

        recyclerView.layoutManager = StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
        recyclerView.adapter = adapter

        giphySearch.onEditorAction { textView, actionId, keyEvent ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH
                    || actionId == EditorInfo.IME_ACTION_UNSPECIFIED) {
                adapter.search(giphySearch.text.toString().trim())
                giphySearch.hideKeyboard()
            }
            actionId == EditorInfo.IME_ACTION_SEARCH
        }
    }

    fun dispatchItemClicked(media: Media) {
        setResult(Activity.RESULT_OK, Intent().setData(Uri.parse(media.images.original.gifUrl)))
        finish()
    }
}