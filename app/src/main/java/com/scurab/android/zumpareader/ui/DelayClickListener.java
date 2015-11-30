package com.scurab.android.zumpareader.ui;

import android.os.Build;
import android.view.View;

/**
 * Created by JBruchanov on 12/11/2015.
 */
public class DelayClickListener implements View.OnClickListener {

    private final View.OnClickListener mClickListener;
    private boolean mPending = false;
    private boolean mDelayedClick;
    private int mClickDelay = 200;

    public DelayClickListener(View.OnClickListener clickListener) {
        this(clickListener, Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP);
    }

    public DelayClickListener(View.OnClickListener clickListener, boolean delayedClick) {
        mClickListener = clickListener;
        mDelayedClick = delayedClick;
    }

    public boolean isDelayedClick() {
        return mDelayedClick;
    }

    public void setDelayedClick(boolean delayedClick) {
        mDelayedClick = delayedClick;
    }

    @Override
    public void onClick(final View v) {
        if (mDelayedClick) {
            if (!mPending) {
                mPending = true;
                final int delay = mClickDelay;
                v.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        mPending = false;
                        mClickListener.onClick(v);
                    }
                }, delay);
            }
        } else {
            mClickListener.onClick(v);
        }
    }
}