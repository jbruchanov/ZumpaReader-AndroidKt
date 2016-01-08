package com.scurab.android.zumpareader.content.post

import android.app.ProgressDialog
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.support.v4.app.DialogFragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import com.pawegio.kandroid.find
import com.scurab.android.zumpareader.R
import com.scurab.android.zumpareader.content.SendingFragment
import com.scurab.android.zumpareader.drawable.SimpleProgressDrawable
import com.scurab.android.zumpareader.util.toast
import com.squareup.picasso.Picasso

/**
 * Created by JBruchanov on 08/01/2016.
 */
public class PostImageFragment : DialogFragment(), SendingFragment {


    companion object {
        public fun newInstance(uri: String): PostImageFragment {
            return PostImageFragment().apply {
                arguments = arguments(uri)
            }
        }

        public fun arguments(uri: String): Bundle {
            return Bundle().apply {
                putString(Intent.EXTRA_STREAM, uri)
            }
        }
    }

    override var sendingDialog: ProgressDialog? = null

    private val image: ImageView get() {
        return view!!.find<ImageView>(R.id.image)
    }

    private val imageUri by lazy { Uri.parse(arguments.getString(Intent.EXTRA_STREAM)) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_post_image, container, false)
    }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        try {
            Picasso.with(context).load(imageUri).placeholder(SimpleProgressDrawable(context)).into(image)
        } catch (e: Throwable) {
            context.toast(e.message)
        }
    }
}