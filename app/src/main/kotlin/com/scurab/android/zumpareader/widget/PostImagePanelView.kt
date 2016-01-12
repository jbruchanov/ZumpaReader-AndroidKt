package com.scurab.android.zumpareader.widget

import android.content.Context
import android.graphics.Point
import android.os.Build
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.pawegio.kandroid.find
import com.scurab.android.zumpareader.R
import com.scurab.android.zumpareader.util.obtainStyledColor
import com.scurab.android.zumpareader.util.setImageTint

/**
 * Created by JBruchanov on 09/01/2016.
 */
public class PostImagePanelView : FrameLayout {

    public val upload by lazy { find<ImageButton>(R.id.send) }
    public val resize by lazy { find<ImageButton>(R.id.resize) }
    public val rotateRight by lazy { find<ImageButton>(R.id.rotate_right) }
    public val sizeSpinner by lazy { find<Spinner>(R.id.size_spinner) }
    private val resolutionOriginal by lazy { find<TextView>(R.id.resolution_original) }
    private val resolutionResized by lazy { find<TextView>(R.id.resolution_resized) }
    private val sizeOriginal by lazy { find<TextView>(R.id.size_original) }
    private val sizeResized by lazy { find<TextView>(R.id.size_resized) }

    constructor(context: Context?) : this(context, null)

    constructor(context: Context?, attrs: AttributeSet?) : this(context, attrs, 0)

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init(attrs)
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {
        init(attrs)
    }

    private fun init(attrs: AttributeSet?) {
        inflate(context, R.layout.widget_post_image_panel, this)
        sizeSpinner.adapter = SizeSpinnerAdapter(context)
        sizeSpinner.setSelection(1)
        initIcons()
    }

    private fun initIcons() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            val color = context.obtainStyledColor(R.attr.contextColor)
            upload.setImageTint(color)
            resize.setImageTint(color)
            rotateRight.setImageTint(color)
        }
    }

    public fun setImageSize(resolution: Point?, fileSize: Long) {
        resolutionOriginal.text = resolution?.toResolution()
        sizeOriginal.text = fileSize.toReadableSize()
    }

    public fun setResizedImageSize(resolution: Point, fileSize: Long) {
        resolutionResized.text = resolution.toResolution()
        sizeResized.text = fileSize.toReadableSize()
    }

    private fun Long.toReadableSize(): String {
        if (this == 0L) {
            return "0 B";
        }
        val mod = 1024;
        val units = arrayOf("B", "KiB", "MiB", "GiB", "TiB", "PiB");
        var i = 0;
        var size = this.toFloat();
        while(size > mod){
            size /= mod;
            i++
        }
        return "%.2f %s".format(size, units[i]);
    }

    private fun Point.toResolution(): String {
        return "%sx%s".format(x, y)
    }
}

private class SizeSpinnerAdapter : BaseAdapter {

    private val data: Array<String>
    private val layoutInflater : LayoutInflater

    constructor(context: Context) : super() {
        data = arrayOf("1/1", "1/2", "1/4", "1/8")
        layoutInflater = LayoutInflater.from(context)
    }

    override fun getCount() : Int {
        return data.size
    }

    override fun getItem(position: Int): String {
        return data[position]
    }

    fun getRatio(position: Int): Int {
        return 1 shl position
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View? {
        val tv: TextView
        if (convertView == null) {
            tv = layoutInflater.inflate(R.layout.item_spinner_item, parent, false) as TextView
        } else {
            tv = convertView as TextView
        }

        tv.text = data[position]
        return tv
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup?): View? {
        return getView(position, convertView, parent)
    }
}