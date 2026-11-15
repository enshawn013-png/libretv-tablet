package com.herl.libretv;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * LibreTV 安卓平板客户端（全屏 WebView）。
 *
 * 刻意极简：只继承系统 Activity、不引 AppCompat —— 上一版在部分平板上
 * 启动即崩，很可能就是主题/依赖组合的问题，去掉一层依赖就少一处崩溃面。
 *
 * 关键 WebView 设置（缺一个就「能打开但播不了」）：
 *   usesCleartextTraffic   服务器是 http，不放开白屏
 *   setMediaPlayback...    hls.js 自动起播
 *   setDomStorageEnabled   前端用 localStorage 存设置和进度
 *   onShowCustomView       网页全屏播放要真全屏
 */
public class MainActivity extends Activity {

    private static final String PREFS = "libretv";
    private static final String KEY_URL = "server_url";

    private WebView web;
    private FrameLayout root;
    private View customView;
    private WebChromeClient.CustomViewCallback customCallback;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.parseColor("#0F1115"));
        setContentView(root);

        try {
            web = new WebView(this);
            configureWebView();
            root.addView(web, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } catch (Throwable t) {
            // WebView 起不来（系统组件缺失/被禁用）也要给一句人话，而不是直接闪退
            showFatal(t);
            return;
        }

        String url = prefs().getString(KEY_URL, "");
        if (url == null || url.isEmpty()) {
            showServerDialog("第一次使用：填 LibreTV 服务地址");
        } else {
            web.loadUrl(url);
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configureWebView() {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setAllowFileAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }

        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                showServerDialog("连不上服务器：" + description);
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customView != null) {
                    callback.onCustomViewHidden();
                    return;
                }
                customView = view;
                customCallback = callback;
                root.addView(view, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                web.setVisibility(View.GONE);
                hideSystemBars();
            }

            @Override
            public void onHideCustomView() {
                if (customView == null) return;
                root.removeView(customView);
                customView = null;
                web.setVisibility(View.VISIBLE);
                if (customCallback != null) customCallback.onCustomViewHidden();
                showSystemBars();
            }
        });
    }

    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    private void showFatal(Throwable t) {
        TextView tv = new TextView(this);
        tv.setTextColor(Color.WHITE);
        tv.setBackgroundColor(Color.parseColor("#0F1115"));
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        tv.setPadding(48, 120, 48, 48);
        tv.setText("WebView 无法初始化，系统组件可能被禁用。\n\n" + t);
        root.addView(tv, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void showServerDialog(String tip) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad, pad, 0);

        TextView t = new TextView(this);
        t.setText(tip + "\n\n填你自己跑着 LibreTV 的地址：\nhttp://<电脑局域网IP>:8080\n装了 Tailscale 也可以填 http://<tailscale-ip>:8080");
        t.setTextSize(14);
        box.addView(t);

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        input.setHint("http://192.168.1.10:8080");
        String saved = prefs().getString(KEY_URL, "");
        if (saved != null) input.setText(saved);
        box.addView(input);

        new AlertDialog.Builder(this)
                .setTitle("LibreTV 服务器")
                .setView(box)
                .setCancelable(false)
                .setPositiveButton("连接", (d, w) -> {
                    String v = input.getText().toString().trim();
                    if (!v.startsWith("http://") && !v.startsWith("https://")) v = "http://" + v;
                    if (v.endsWith("/")) v = v.substring(0, v.length() - 1);
                    prefs().edit().putString(KEY_URL, v).apply();
                    if (web != null) web.loadUrl(v);
                })
                .setNegativeButton("取消", (d, w) -> {
                    if (web != null) web.setVisibility(View.GONE);
                    TextView hint = new TextView(MainActivity.this);
                    hint.setTextColor(Color.parseColor("#9A9AA0"));
                    hint.setTextSize(16);
                    hint.setPadding(pad, pad * 3, pad, pad);
                    hint.setText("没有连接服务器。\n\n长按返回键可以重新弹出地址输入框。");
                    root.addView(hint);
                })
                .show();
    }

    private void hideSystemBars() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    private void showSystemBars() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.isLongPress()) {
            showServerDialog("修改服务器地址");
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_BACK && web != null && web.canGoBack()) {
            web.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.loadUrl("about:blank");
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
