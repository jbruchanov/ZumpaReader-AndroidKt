package com.scurab.android.zumpareader.content

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.pawegio.kandroid.find
import com.pawegio.kandroid.toast
import com.scurab.android.zumpareader.R
import com.scurab.android.zumpareader.app.BaseFragment
import com.scurab.android.zumpareader.model.ZumpaThreadResult
import com.scurab.android.zumpareader.util.exec
import retrofit.Callback
import retrofit.Response
import retrofit.Retrofit

/**
 * Created by JBruchanov on 27/11/2015.
 */
public class SubListFragment : BaseFragment() {

    companion object {
        private val THREAD_ID: String = "THREAD_ID"

        public fun newInstance(threadId: String): SubListFragment {
            return SubListFragment().apply {
                var args = Bundle()
                args.putString(THREAD_ID, threadId)
                arguments = args
            }
        }
    }

    override val title: CharSequence get() = zumpaData?.get(threadId)?.subject ?: ""
    protected val threadId: String by lazy { arguments!!.getString(THREAD_ID) }

    private var recyclerView : RecyclerView? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle?): View? {
        var content = inflater.inflate(R.layout.view_recycler, container, false)
        content.setBackgroundColor(Color.BLACK)
        recyclerView = content.find(R.id.recycler_view)
        recyclerView?.layoutManager = LinearLayoutManager(inflater.context, LinearLayoutManager.VERTICAL, false)
        return content
    }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val call = zumpaApp?.zumpaAPI?.getThreadPage(threadId, threadId)
        call?.enqueue(object: Callback<ZumpaThreadResult?> {
            override fun onResponse(response: Response<ZumpaThreadResult?>?, retrofit: Retrofit?) {
                response?.body()?.items.exec {
                    recyclerView?.adapter = SubListAdapter(it)
                }
            }

            override fun onFailure(t: Throwable?) {
                toast(t?.message ?: "Null")
            }
        })
    }
}