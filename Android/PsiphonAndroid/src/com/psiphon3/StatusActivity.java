/*
 * Copyright (c) 2014, Psiphon Inc.
 * All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.psiphon3;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TabHost;
import android.widget.TextView;

import com.mopub.mobileads.MoPubErrorCode;
import com.mopub.mobileads.MoPubInterstitial;
import com.mopub.mobileads.MoPubInterstitial.InterstitialAdListener;
import com.mopub.mobileads.MoPubView;
import com.mopub.mobileads.MoPubView.BannerAdListener;
import com.psiphon3.psiphonlibrary.EmbeddedValues;
import com.psiphon3.psiphonlibrary.PsiphonData;
import com.psiphon3.util.IabHelper;
import com.psiphon3.util.IabResult;
import com.psiphon3.util.Inventory;
import com.psiphon3.util.Purchase;


public class StatusActivity
    extends com.psiphon3.psiphonlibrary.MainBase.TabbedActivityBase
{
    public static final String BANNER_FILE_NAME = "bannerImage";

    private ImageView m_banner;
    private ImageButton m_statusImage;
    private TextView m_versionLine;
    private TextView m_logLine;
    private MoPubView m_moPubBannerAdView = null;
    private MoPubView m_moPubBannerLargeAdView = null;
    private MoPubInterstitial m_moPubInterstitial = null;
    private int m_fullScreenAdCounter = 0;
    private boolean m_fullScreenAdPending = false;
    private boolean m_tunnelWholeDevicePromptShown = false;
    private boolean m_loadedSponsorTab = false;
    private boolean m_temporarilyDisableInterstitial = false;
    private IabHelper m_iabHelper = null;
    private boolean m_validSubscription = false;

    public StatusActivity()
    {
        super();
        m_eventsInterface = new Events();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        setContentView(R.layout.main);

        m_banner = (ImageView)findViewById(R.id.banner);
        m_statusImage = (ImageButton)findViewById(R.id.statusViewImage);
        m_versionLine = (TextView)findViewById(R.id.versionline);
        m_logLine = (TextView)findViewById(R.id.lastlogline);
        m_tabHost = (TabHost)findViewById(R.id.tabHost);
        m_toggleButton = (Button)findViewById(R.id.toggleButton);

        // NOTE: super class assumes m_tabHost is initialized in its onCreate

        // Don't let this tab change trigger an interstitial ad
        // OnResume() will reset this flag
        m_temporarilyDisableInterstitial = true;
        
        super.onCreate(savedInstanceState);

        if (m_firstRun)
        {
            EmbeddedValues.initialize(this);
        }

        // Play Store Build instances should use existing banner from previously installed APK
        // (if present). To enable this, non-Play Store Build instances write their banner to
        // a private file.
        try
        {
            if (EmbeddedValues.IS_PLAY_STORE_BUILD)
            {
                File bannerImageFile = new File(getFilesDir(), BANNER_FILE_NAME);
                if (bannerImageFile.exists())
                {
                    Bitmap bitmap = BitmapFactory.decodeFile(bannerImageFile.getAbsolutePath());
                    m_banner.setImageBitmap(bitmap);
                }
            }
            else
            {
                Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.banner);
                if (bitmap != null)
                {
                    FileOutputStream out = openFileOutput(BANNER_FILE_NAME, Context.MODE_PRIVATE);
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                    out.close();
                }
            }
        }
        catch (IOException e)
        {
            // Ignore failure
        }

        PsiphonData.getPsiphonData().setDownloadUpgrades(true);
        
        LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(StatusActivity.this);
        localBroadcastManager.registerReceiver(new ConnectionStateChangeReceiver(), new IntentFilter(TUNNEL_STOPPING));
        localBroadcastManager.registerReceiver(new ConnectionStateChangeReceiver(), new IntentFilter(UNEXPECTED_DISCONNECT));

        // Auto-start on app first run
        if (m_firstRun)
        {
            m_firstRun = false;
            startUp();
        }
        
        m_loadedSponsorTab = false;
        HandleCurrentIntent();
        
        // HandleCurrentIntent() may have already loaded the sponsor tab
        if (PsiphonData.getPsiphonData().getDataTransferStats().isConnected() &&
                !m_loadedSponsorTab)
        {
            loadSponsorTab(false);
        }
    }

    @Override
    public void onPause()
    {
        super.onPause();
    }
    
    @Override
    public void onResume()
    {
        super.onResume();
        m_temporarilyDisableInterstitial = false;
        initIab();
        initAds();
    }
    
    @Override
    public void onDestroy()
    {
        deInitAds();
        super.onDestroy();
        deInitIab();
    }
    
    private void loadSponsorTab(boolean freshConnect)
    {
        if (!PsiphonData.getPsiphonData().getSkipHomePage())
        {
            resetSponsorHomePage(freshConnect);
        }
    }

    @Override
    protected void onNewIntent(Intent intent)
    {
        super.onNewIntent(intent);

        // If the app is already foreground (so onNewIntent is being called),
        // the incoming intent is not automatically set as the activity's intent
        // (i.e., the intent returned by getIntent()). We want this behaviour,
        // so we'll set it explicitly.
        setIntent(intent);

        // Handle explicit intent that is received when activity is already running
        HandleCurrentIntent();
    }

    @Override
    protected void doToggle()
    {
        super.doToggle();
    }
    
    @Override
    public void onTabChanged(String tabId)
    {
        showFullScreenAd();
        super.onTabChanged(tabId);
    }
    
    public class ConnectionStateChangeReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            deInitAds();
        }
    }

    static final String MOPUB_BANNER_PROPERTY_ID = "";
    static final String MOPUB_LARGE_BANNER_PROPERTY_ID = "";
    static final String MOPUB_INTERSTITIAL_PROPERTY_ID = "";
    
    private boolean shouldShowAds()
    {
        // For now, only show ads when the tunnel is connected, since WebViewProxySettings are
        // probably set and webviews won't load successfully when the tunnel is not connected
        return PsiphonData.getPsiphonData().getShowAds() &&
                PsiphonData.getPsiphonData().getDataTransferStats().isConnected() &&
                Build.VERSION.SDK_INT > Build.VERSION_CODES.FROYO &&
                !m_validSubscription;
    }
    
    private void showFullScreenAd()
    {
        if (shouldShowAds() && !m_temporarilyDisableInterstitial)
        {
            m_fullScreenAdCounter++;

            if (m_fullScreenAdCounter % 3 == 1)
            {
                if (m_moPubInterstitial != null)
                {
                    m_moPubInterstitial.destroy();
                }
                m_moPubInterstitial = new MoPubInterstitial(this, MOPUB_INTERSTITIAL_PROPERTY_ID);
                m_moPubInterstitial.setInterstitialAdListener(new InterstitialAdListener() {
                    @Override
                    public void onInterstitialClicked(MoPubInterstitial arg0) {
                    }
                    @Override
                    public void onInterstitialDismissed(MoPubInterstitial arg0) {
                    }
                    @Override
                    public void onInterstitialFailed(MoPubInterstitial arg0,
                            MoPubErrorCode arg1) {
                    }
                    @Override
                    public void onInterstitialLoaded(MoPubInterstitial interstitial) {
                        if (interstitial != null && interstitial.isReady())
                        {
                            interstitial.show();
                        }
                    }
                    @Override
                    public void onInterstitialShown(MoPubInterstitial arg0) {
                    }
                });
                
                m_moPubInterstitial.load();
            }
        }
    }
    
    private void initBanners()
    {
        if (shouldShowAds())
        {
            if (m_moPubBannerAdView == null)
            {
                m_moPubBannerAdView = new MoPubView(this);
                m_moPubBannerAdView.setAdUnitId(MOPUB_BANNER_PROPERTY_ID);
                
                m_moPubBannerAdView.setBannerAdListener(new BannerAdListener() {
                    @Override
                    public void onBannerLoaded(MoPubView banner)
                    {
                        if (m_moPubBannerAdView.getParent() == null)
                        {
                            LinearLayout layout = (LinearLayout)findViewById(R.id.bannerLayout);
                            layout.removeAllViewsInLayout();
                            layout.addView(m_moPubBannerAdView);
                        }
                    }
                    @Override
                    public void onBannerClicked(MoPubView arg0) {
                    }
                    @Override
                    public void onBannerCollapsed(MoPubView arg0) {
                    }
                    @Override
                    public void onBannerExpanded(MoPubView arg0) {
                    }
                    @Override
                    public void onBannerFailed(MoPubView arg0,
                            MoPubErrorCode arg1) {
                    }
                });
                
                m_moPubBannerAdView.loadAd();
                m_moPubBannerAdView.setAutorefreshEnabled(true);
            }
            
            if (!PsiphonData.getPsiphonData().showFirstHomePageInApp() && m_moPubBannerLargeAdView == null)
            {
                m_moPubBannerLargeAdView = new MoPubView(this);
                m_moPubBannerLargeAdView.setAdUnitId(MOPUB_LARGE_BANNER_PROPERTY_ID);
                
                m_moPubBannerLargeAdView.setBannerAdListener(new BannerAdListener() {
                    @Override
                    public void onBannerLoaded(MoPubView banner)
                    {
                        if (m_moPubBannerLargeAdView.getParent() == null)
                        {
                            LinearLayout layout = (LinearLayout)findViewById(R.id.statusLayout);
                            layout.removeAllViewsInLayout();
                            layout.addView(m_moPubBannerLargeAdView);
                        }
                    }
                    @Override
                    public void onBannerClicked(MoPubView arg0) {
                    }
                    @Override
                    public void onBannerCollapsed(MoPubView arg0) {
                    }
                    @Override
                    public void onBannerExpanded(MoPubView arg0) {
                    }
                    @Override
                    public void onBannerFailed(MoPubView arg0,
                            MoPubErrorCode arg1) {
                    }
                });
                
                m_moPubBannerLargeAdView.loadAd();
                m_moPubBannerLargeAdView.setAutorefreshEnabled(true);
            }
        }
    }
    
    private void deInitAds()
    {
        LinearLayout layout = (LinearLayout)findViewById(R.id.bannerLayout);
        layout.removeAllViewsInLayout();
        layout.addView(m_banner);

        LinearLayout statusLayout = (LinearLayout)findViewById(R.id.statusLayout);
        statusLayout.removeAllViewsInLayout();
        statusLayout.addView(m_statusImage);
        statusLayout.addView(m_versionLine);
        statusLayout.addView(m_logLine);

        if (m_moPubBannerAdView != null)
        {
            m_moPubBannerAdView.destroy();
        }
        m_moPubBannerAdView = null;

        if (m_moPubBannerLargeAdView != null)
        {
            m_moPubBannerLargeAdView.destroy();
        }
        m_moPubBannerLargeAdView = null;

        if (m_moPubInterstitial != null)
        {
            m_moPubInterstitial.destroy();
        }
        m_moPubInterstitial = null;
    }
    
    private void initAds()
    {
        if (PsiphonData.getPsiphonData().getShowAds())
        {
            initBanners();
            
            if (m_fullScreenAdPending)
            {
                showFullScreenAd();
                m_fullScreenAdPending = false;
            }
        }
    }
    
    static final String IAB_PUBLIC_KEY = "";
    static final String IAB_BASIC_MONTHLY_SUBSCRIPTION_SKU = "";
    static final int IAB_REQUEST_CODE = 10001;
    
    private void initIab()
    {
        if (PsiphonData.getPsiphonData().getShowAds() && m_iabHelper == null)
        {
            m_iabHelper = new IabHelper(this, IAB_PUBLIC_KEY);
            m_iabHelper.startSetup(new IabHelper.OnIabSetupFinishedListener()
            {
                @Override
                public void onIabSetupFinished(IabResult result)
                {
                    if (!result.isSuccess())
                    {
                        // try again next time
                        deInitIab();
                    }
                    else
                    {
                        if (m_iabHelper != null && !m_validSubscription)
                        {
                            m_iabHelper.queryInventoryAsync(m_queryInventoryFinishedListener);
                        }
                    }
                }
            });
        }
    }
            
    private IabHelper.QueryInventoryFinishedListener m_queryInventoryFinishedListener =
                    new IabHelper.QueryInventoryFinishedListener()
    {
        @Override
        public void onQueryInventoryFinished(IabResult result, Inventory inventory)
        {
            if (result.isFailure())
            {
                // try again next time
                deInitIab();
            }
            else
            {
                m_validSubscription = inventory.hasPurchase(IAB_BASIC_MONTHLY_SUBSCRIPTION_SKU);
                deInitAds();
            }
        }
    };
    
    private IabHelper.OnIabPurchaseFinishedListener m_purchaseFinishedListener = 
            new IabHelper.OnIabPurchaseFinishedListener()
    {
        @Override
        public void onIabPurchaseFinished(IabResult result, Purchase purchase) 
        {
            if (result.isFailure())
            {
                // Do nothing
            }      
            else if (purchase.getSku().equals(IAB_BASIC_MONTHLY_SUBSCRIPTION_SKU))
            {
                m_validSubscription = true;
                deInitAds();
            }
        }
    };

    private void deInitIab()
    {
        if (m_iabHelper != null)
        {
            m_iabHelper.dispose();
            m_iabHelper = null;
        }
    }
    
    private void launchIabSubscriptionPurchaseFlow()
    {
        
        if (m_iabHelper != null)
        {
            m_iabHelper.launchSubscriptionPurchaseFlow(this, IAB_BASIC_MONTHLY_SUBSCRIPTION_SKU,
                    IAB_REQUEST_CODE, m_purchaseFinishedListener);
        }
    }
    
    protected void HandleCurrentIntent()
    {
        Intent intent = getIntent();

        if (intent == null || intent.getAction() == null)
        {
            return;
        }

        if (0 == intent.getAction().compareTo(HANDSHAKE_SUCCESS))
        {
            // Show the home page. Always do this in browser-only mode, even
            // after an automated reconnect -- since the status activity was
            // brought to the front after an unexpected disconnect. In whole
            // device mode, after an automated reconnect, we don't re-invoke
            // the browser.
            if (!PsiphonData.getPsiphonData().getTunnelWholeDevice()
                || !intent.getBooleanExtra(HANDSHAKE_SUCCESS_IS_RECONNECT, false))
            {
                // Don't let this tab change trigger an interstitial ad
                // OnResume() will reset this flag
                m_temporarilyDisableInterstitial = true;

                // Show the full screen ad after OnResume() has initialized ads
                m_fullScreenAdPending = true;
                
                m_tabHost.setCurrentTabByTag("home");
                
                if (PsiphonData.getPsiphonData().getShowAds() && !m_validSubscription)
                {
                    new AlertDialog.Builder(this)
                    .setCancelable(false)
                    .setOnKeyListener(
                            new DialogInterface.OnKeyListener() {
                                @Override
                                public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
                                    // Don't dismiss when hardware search button is clicked (Android 2.3 and earlier)
                                    return keyCode == KeyEvent.KEYCODE_SEARCH;
                                }})
                    .setTitle("Support Psiphon")
                    .setMessage("Please help keep the Psiphon network running.")
                    .setPositiveButton("OK!",
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int whichButton) {
                                    if (m_iabHelper != null)
                                    {
                                        launchIabSubscriptionPurchaseFlow();
                                    }
                                }})
                    .setNegativeButton("No thanks",
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int whichButton) {
                                    loadSponsorTab(true);
                                    m_loadedSponsorTab = true;
                                }})
                    .setOnCancelListener(
                            new DialogInterface.OnCancelListener() {
                                @Override
                                public void onCancel(DialogInterface dialog) {
                                    loadSponsorTab(true);
                                    m_loadedSponsorTab = true;
                                }})
                    .show();
                }
                else
                {
                    loadSponsorTab(true);
                    m_loadedSponsorTab = true;
                }
            }

            // We only want to respond to the HANDSHAKE_SUCCESS action once,
            // so we need to clear it (by setting it to a non-special intent).
            setIntent(new Intent(
                            "ACTION_VIEW",
                            null,
                            this,
                            this.getClass()));
        }

        // No explicit action for UNEXPECTED_DISCONNECT, just show the activity
    }

    public void onToggleClick(View v)
    {
        doToggle();
    }

    public void onOpenBrowserClick(View v)
    {
        m_eventsInterface.displayBrowser(this);
    }

    @Override
    public void onFeedbackClick(View v)
    {
        Intent feedbackIntent = new Intent(this, FeedbackActivity.class);
        startActivity(feedbackIntent);
    }

    @Override
    protected void startUp()
    {
        // If the user hasn't set a whole-device-tunnel preference, show a prompt
        // (and delay starting the tunnel service until the prompt is completed)

        boolean hasPreference = PreferenceManager.getDefaultSharedPreferences(this).contains(TUNNEL_WHOLE_DEVICE_PREFERENCE);

        if (m_tunnelWholeDeviceToggle.isEnabled() &&
            !hasPreference &&
            !isServiceRunning())
        {
            if (!m_tunnelWholeDevicePromptShown)
            {
                final Context context = this;

                AlertDialog dialog = new AlertDialog.Builder(context)
                    .setCancelable(false)
                    .setOnKeyListener(
                            new DialogInterface.OnKeyListener() {
                                @Override
                                public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
                                    // Don't dismiss when hardware search button is clicked (Android 2.3 and earlier)
                                    return keyCode == KeyEvent.KEYCODE_SEARCH;
                                }})
                    .setTitle(R.string.StatusActivity_WholeDeviceTunnelPromptTitle)
                    .setMessage(R.string.StatusActivity_WholeDeviceTunnelPromptMessage)
                    .setPositiveButton(R.string.StatusActivity_WholeDeviceTunnelPositiveButton,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int whichButton) {
                                    // Persist the "on" setting
                                    updateWholeDevicePreference(true);
                                    startTunnel(context);
                                }})
                    .setNegativeButton(R.string.StatusActivity_WholeDeviceTunnelNegativeButton,
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int whichButton) {
                                        // Turn off and persist the "off" setting
                                        m_tunnelWholeDeviceToggle.setChecked(false);
                                        updateWholeDevicePreference(false);
                                        startTunnel(context);
                                    }})
                    .setOnCancelListener(
                            new DialogInterface.OnCancelListener() {
                                @Override
                                public void onCancel(DialogInterface dialog) {
                                    // Don't change or persist preference (this prompt may reappear)
                                    startTunnel(context);
                                }})
                    .show();
                
                // Our text no longer fits in the AlertDialog buttons on Lollipop, so force the
                // font size (on older versions, the text seemed to be scaled down to fit).
                // TODO: custom layout
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                {
                    dialog.getButton(DialogInterface.BUTTON_POSITIVE).setTextSize(TypedValue.COMPLEX_UNIT_DIP, 10);
                    dialog.getButton(DialogInterface.BUTTON_NEGATIVE).setTextSize(TypedValue.COMPLEX_UNIT_DIP, 10);
                }
                
                m_tunnelWholeDevicePromptShown = true;
            }
            else
            {
                // ...there's a prompt already showing (e.g., user hit Home with the
                // prompt up, then resumed Psiphon)
            }

            // ...wait and let onClick handlers will start tunnel
        }
        else
        {
            // No prompt, just start the tunnel (if not already running)

            startTunnel(this);
        }
    }
}
