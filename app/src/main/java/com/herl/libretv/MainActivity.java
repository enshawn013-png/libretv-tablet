package com.herl.libretv;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
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

import androidx.appcompat.app.AppCompatActivity;

/**
 * LibreTV 安卓平板客户端。
 *
 * 为什么是「客户端 + 服务端」而不是单机 App：
 * 这个 LibreTV 是 Next.js 版本，带 16 个服务端 API 路由（搜索 / 详情 / 采集站代理 /
 * 直播流转发 / 豆瓣），前端离不开这些路由，没法静态导出成纯前端包。
 * 所以安卓端只做一件事：全屏 WebView 连到你自己的 LibreTV 服务（PC / NAS / Tailscale）。
 *
 * 关键设置（缺一个就会「能打开但播不了」）：
 *   - usesCleartextTraffic：服务器是 http://，不放开会直接白屏
 *   - setMediaPlaybackRequiresUserGesture(false)：hls.js 自动播放需要
 *   - setDomStorageEnabled：前端用 localStorage 存播放进度/设置
 *   - onShowCustomView：网页全屏播放（视频铺满）
 */
public class MainActivity extends AppCompatActivity {

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
        web = new WebView(this);
        root.addView(web, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }

        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                // 连不上服务器时不要停在错误页，直接让用户改地址
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

        String url = prefs().getString(KEY_URL, "");
        if (url == null || url.isEmpty()) {
            showServerDialog("第一次使用：填 LibreTV 服务地址\n（比如 http://192.168.1.10:8080）");
        } else {
            web.loadUrl(url);
        }
    }

    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    /** 服务器地址对话框：可编辑、可清空、记住 */
    private void showServerDialog(String tip) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad, pad, 0);

        TextView t = new TextView(this);
        t.setText(tip + "\n\n建议：PC 上跑着 LibreTV 时填 http://<电脑局域网IP>:8080\n装了 Tailscale 的话填 http://<tailscale-ip>:8080");
        t.setTextSize(14);
        box.addView(t);

        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        input.setHint("http://192.168.1.10:8080");
        input.setText(prefs().getString(KEY_URL, ""));
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
                    web.loadUrl(v);
                })
                .setNeutralButton("重试", (d, w) -> {
                    String cur = prefs().getString(KEY_URL, "");
                    if (cur != null && !cur.isEmpty()) web.loadUrl(cur);
                    else showServerDialog("还没有设置地址");
                })
                .show();
    }

    private void hideSystemBars() {
        View d = getWindow().getDecorView();
        d.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    private void showSystemBars() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // 音量键下 + 电源以外的「菜单」用法：长按返回键换服务器地址
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
        }
        super.onDestroy();
    }
}
