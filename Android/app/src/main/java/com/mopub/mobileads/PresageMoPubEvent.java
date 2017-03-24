package com.mopub.mobileads;

import io.presage.Presage;
import io.presage.IADHandler;

import java.util.Map;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import com.mopub.mobileads.CustomEventInterstitial;
import com.mopub.mobileads.MoPubErrorCode;

public class PresageMoPubEvent extends CustomEventInterstitial {

    private CustomEventInterstitialListener mListener;

    public PresageMoPubEvent() {
        super();
    }

    @Override
    protected void loadInterstitial(Context context,
            CustomEventInterstitialListener listener, Map<String, Object> arg2,
            Map<String, String> arg3) {

        mListener = listener;

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            mListener.onInterstitialFailed(MoPubErrorCode.ADAPTER_NOT_FOUND);
            return;
        }

        Presage.getInstance().adToServe(new IADHandler() {

            @Override
            public void onAdNotFound() {
                if (mListener != null)
                mListener.onInterstitialFailed(MoPubErrorCode.NETWORK_NO_FILL); 
            }

            @Override
            public void onAdFound() {
                if (mListener != null)
                mListener.onInterstitialLoaded();
            }

            @Override
            public void onAdClosed() {
                if (mListener != null)
                mListener.onInterstitialDismissed();
            }
            @Override
            public void onAdError(int code) {
                Log.i("PRESAGE", String.format("error with code %d", code));
            }

            @Override
            public void onAdDisplayed() {
                Log.i("PRESAGE", "ad displayed");
            }
        });
    }

    @Override
    protected void showInterstitial() {
        if (mListener != null) {
            mListener.onInterstitialShown();
        }
    }

    @Override
    protected void onInvalidate() {
        //nothing to do here
    }

}
