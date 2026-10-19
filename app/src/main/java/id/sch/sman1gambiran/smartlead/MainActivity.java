package id.sch.sman1gambiran.smartlead;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;
import androidx.webkit.JavaScriptReplyProxy;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanner;
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * SMART LEAD – aplikasi Android pembungkus Web App Google Apps Script.
 * - Kamera (scan QR), lokasi (presensi), pilih/ambil foto, dan unduhan file berfungsi di dalam aplikasi.
 * - Hanya halaman Google Apps Script yang dibuka di dalam aplikasi; tautan lain (WhatsApp, Maps, Drive)
 *   dibuka dengan aplikasi masing-masing.
 */
public class MainActivity extends Activity {

    private static final int REQ_CAMERA = 11, REQ_LOCATION = 12, REQ_CAMERA_FOR_FILE = 13, REQ_FILE = 21, REQ_NOTIF = 31;
    private String pendingHalaman = null; // halaman yang dibuka dari notifikasi
    private JavaScriptReplyProxy lastProxy = null, pushProxy = null;

    private WebView web;
    private View splash, errorView;
    private ProgressBar bar;
    private TextView errorText;
    private boolean firstLoad = true, mainError = false;
    private long lastBack = 0;

    private PermissionRequest pendingMedia;
    private GeolocationPermissions.Callback pendingGeoCb;
    private String pendingGeoOrigin;
    private ValueCallback<Uri[]> fileCallback;
    private WebChromeClient.FileChooserParams pendingFileParams;
    private Uri cameraOutput;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        pendingHalaman = getIntent() != null ? getIntent().getStringExtra("halaman") : null;
        PushService.siapkanChannel(this);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.parseColor("#F2F5F4"));

        web = new WebView(this);
        root.addView(web, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(4));
        bp.gravity = Gravity.TOP;
        root.addView(bar, bp);

        splash = buildSplash();
        root.addView(splash, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        errorView = buildError();
        errorView.setVisibility(View.GONE);
        root.addView(errorView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);

        setupWebView();
        if (state != null) web.restoreState(state);
        if (state == null || web.getUrl() == null) loadApp();
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private View buildSplash() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setGravity(Gravity.CENTER);
        l.setBackgroundColor(Color.parseColor("#0B5C4F"));
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.logo);
        l.addView(logo, new LinearLayout.LayoutParams(dp(96), dp(96)));
        TextView t = new TextView(this);
        t.setText("SMART LEAD");
        t.setTextColor(Color.WHITE);
        t.setTextSize(24);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(16), 0, dp(4));
        l.addView(t);
        TextView s = new TextView(this);
        s.setText("SMAN 1 Gambiran · memuat…");
        s.setTextColor(Color.parseColor("#CFE3DD"));
        s.setTextSize(14);
        l.addView(s);
        ProgressBar p = new ProgressBar(this);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(dp(36), dp(36));
        pp.topMargin = dp(22);
        l.addView(p, pp);
        return l;
    }

    private View buildError() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setGravity(Gravity.CENTER);
        l.setPadding(dp(28), dp(28), dp(28), dp(28));
        l.setBackgroundColor(Color.parseColor("#0B5C4F"));
        TextView h = new TextView(this);
        h.setText("Tidak bisa membuka SMART LEAD");
        h.setTextColor(Color.WHITE);
        h.setTextSize(20);
        h.setTypeface(Typeface.DEFAULT_BOLD);
        h.setGravity(Gravity.CENTER);
        l.addView(h);
        errorText = new TextView(this);
        errorText.setTextColor(Color.parseColor("#CFE3DD"));
        errorText.setTextSize(15);
        errorText.setGravity(Gravity.CENTER);
        errorText.setPadding(0, dp(12), 0, dp(20));
        l.addView(errorText);
        Button b = new Button(this);
        b.setText("Coba lagi");
        b.setOnClickListener(v -> loadApp());
        l.addView(b);
        return l;
    }

    private boolean online() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            NetworkInfo n = cm != null ? cm.getActiveNetworkInfo() : null;
            return n != null && n.isConnected();
        } catch (Exception e) { return true; }
    }

    private void loadApp() {
        String url = BuildConfig.APP_URL;
        if (url == null || !url.startsWith("https://")) { showError("Alamat aplikasi belum diatur. Isi SMARTLEAD_URL di file gradle.properties lalu buat ulang APK."); return; }
        if (!online()) { showError("Tidak ada koneksi internet. Nyalakan data seluler atau Wi-Fi sekolah, lalu coba lagi."); return; }
        mainError = false;
        errorView.setVisibility(View.GONE);
        web.loadUrl(url);
        cekPembaruan(false);
    }

    private void showError(String msg) {
        errorText.setText(msg);
        errorView.setVisibility(View.VISIBLE);
        splash.setVisibility(View.GONE);
    }

    /** Domain yang boleh dibuka di dalam aplikasi. */
    private static boolean dalamAplikasi(Uri u) {
        if (u == null || u.getHost() == null) return false;
        String h = u.getHost().toLowerCase();
        return h.equals("script.google.com") || h.endsWith(".googleusercontent.com") || h.equals("script.googleusercontent.com")
                || h.equals("accounts.google.com") || h.endsWith(".gstatic.com") || h.equals("www.google.com") && u.getPath() != null && u.getPath().startsWith("/recaptcha");
    }

    private static boolean asalTepercaya(Uri u) {
        if (u == null || u.getHost() == null || !"https".equals(u.getScheme())) return false;
        String h = u.getHost().toLowerCase();
        return h.equals("script.google.com") || h.endsWith(".googleusercontent.com");
    }

    /* ---------- Pembaruan aplikasi: cek rilis terbaru di GitHub ---------- */
    private void cekPembaruan(boolean paksa) {
        final String repo = BuildConfig.UPDATE_REPO;
        if (repo == null || repo.isEmpty()) return;
        final android.content.SharedPreferences sp = getSharedPreferences("sl", MODE_PRIVATE);
        long now = System.currentTimeMillis();
        if (!paksa && now - sp.getLong("cek_update", 0) < 3L * 3600 * 1000) return; // paling sering tiap 3 jam
        sp.edit().putLong("cek_update", now).apply();
        new Thread(() -> {
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL("https://api.github.com/repos/" + repo + "/releases/latest").openConnection();
                c.setConnectTimeout(8000); c.setReadTimeout(8000);
                c.setRequestProperty("Accept", "application/vnd.github+json");
                c.setRequestProperty("User-Agent", "SmartLeadAndroid");
                if (c.getResponseCode() != 200) return;
                StringBuilder sb = new StringBuilder();
                BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
                String line; while ((line = br.readLine()) != null) sb.append(line);
                br.close();
                JSONObject j = new JSONObject(sb.toString());
                final String tag = j.optString("tag_name", "");            // contoh: v1.2.41 (angka terakhir = nomor build)
                int build = 0;
                try { build = Integer.parseInt(tag.substring(tag.lastIndexOf('.') + 1)); } catch (Exception ignored) { }
                if (build <= BuildConfig.VERSION_CODE) return;
                if (tag.equals(sp.getString("lewati_tag", "")) && System.currentTimeMillis() - sp.getLong("lewati_waktu", 0) < 24L * 3600 * 1000) return;
                String apk = null;
                org.json.JSONArray as = j.optJSONArray("assets");
                for (int i = 0; as != null && i < as.length(); i++) { JSONObject a = as.optJSONObject(i); if (a != null && a.optString("name", "").endsWith(".apk")) { apk = a.optString("browser_download_url", ""); break; } }
                final String link = apk != null && !apk.isEmpty() ? apk : j.optString("html_url", "");
                if (link.isEmpty()) return;
                runOnUiThread(() -> tawarkanPembaruan(tag, link, sp));
            } catch (Exception ignored) {
            } finally { if (c != null) c.disconnect(); }
        }).start();
    }
    private void tawarkanPembaruan(String tag, String link, android.content.SharedPreferences sp) {
        if (isFinishing()) return;
        new AlertDialog.Builder(this)
                .setTitle("Versi baru SMART LEAD")
                .setMessage("Tersedia " + tag + " (terpasang v" + BuildConfig.VERSION_NAME + "." + BuildConfig.VERSION_CODE + ").\n\nKetuk Perbarui, tunggu unduhan selesai, lalu buka file APK dan pilih Pasang/Update. Data dan akun tidak hilang.")
                .setPositiveButton("Perbarui", (d, w) -> bukaLuar(Uri.parse(link)))
                .setNegativeButton("Nanti", (d, w) -> sp.edit().putString("lewati_tag", tag).putLong("lewati_waktu", System.currentTimeMillis()).apply())
                .setCancelable(true)
                .show();
    }

    private void bukaLuar(Uri u) {
        try {
            Intent i;
            if ("intent".equals(u.getScheme())) {
                i = Intent.parseUri(u.toString(), Intent.URI_INTENT_SCHEME);
            } else {
                i = new Intent(Intent.ACTION_VIEW, u);
            }
            i.addCategory(Intent.CATEGORY_BROWSABLE);
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Tidak ada aplikasi untuk membuka tautan ini", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Tautan tidak bisa dibuka", Toast.LENGTH_SHORT).show();
        }
    }

    @SuppressWarnings("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setGeolocationEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        // Tampilan selalu pas selebar layar (tidak bisa ter-zoom / bergeser ke samping)
        s.setLoadWithOverviewMode(false);
        s.setUseWideViewPort(false);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setSupportMultipleWindows(false);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setUserAgentString(s.getUserAgentString() + " SmartLeadAndroid/" + BuildConfig.VERSION_NAME);
        // Matikan saran isi-otomatis (username/sandi tersimpan) agar akun orang lain tidak muncul di HP bersama.
        s.setSaveFormData(false);
        s.setTextZoom(100); // ukuran huruf tetap pas walau setelan huruf HP diperbesar
        if (Build.VERSION.SDK_INT >= 26) web.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(web, true);

        // Jembatan unduhan: halaman SMART LEAD mengirim file (CSV/Excel) sebagai base64.
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            Set<String> asal = new HashSet<>();
            asal.add("https://script.google.com");
            asal.add("https://*.googleusercontent.com");
            WebViewCompat.addWebMessageListener(web, "SLAndroid", asal, (view, message, sourceOrigin, isMainFrame, replyProxy) -> {
                try {
                    JSONObject o = new JSONObject(message.getData());
                    String t = o.optString("t");
                    if ("save".equals(t)) simpanFile(o.optString("name", "unduhan"), o.optString("mime", "application/octet-stream"), o.optString("b64", ""));
                    else if ("hello".equals(t)) {
                        lastProxy = replyProxy;
                        JSONObject h = new JSONObject().put("t", "hello").put("scan", true).put("push", pushSiap()).put("ver", BuildConfig.VERSION_NAME + "." + BuildConfig.VERSION_CODE);
                        if (pendingHalaman != null) { h.put("halaman", pendingHalaman); pendingHalaman = null; }
                        balas(replyProxy, h);
                    }
                    else if ("push".equals(t)) mintaPush(replyProxy);
                    else if ("update".equals(t)) cekPembaruan(true);
                    else if ("scan".equals(t)) pindaiQR(replyProxy);
                } catch (Exception e) {
                    Toast.makeText(this, "Perintah aplikasi gagal", Toast.LENGTH_SHORT).show();
                }
            });
        }

        web.setDownloadListener((url, userAgent, contentDisposition, mimetype, contentLength) -> {
            Uri u = Uri.parse(url);
            if ("https".equals(u.getScheme())) bukaLuar(u);
            else Toast.makeText(this, "Gunakan tombol unduh di aplikasi", Toast.LENGTH_SHORT).show();
        });

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                String sc = u.getScheme() == null ? "" : u.getScheme();
                if (!req.isForMainFrame() && ("https".equals(sc) || "about".equals(sc) || "blob".equals(sc) || "data".equals(sc))) return false;
                if ("https".equals(sc) && dalamAplikasi(u)) return false;
                bukaLuar(u);
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                Uri pu = Uri.parse(url);
                if (pu != null && "script.google.com".equals(pu.getHost())) view.evaluateJavascript(TUTUP_PITA, null);
                if (!mainError) {
                    splash.setVisibility(View.GONE);
                    errorView.setVisibility(View.GONE);
                }
                firstLoad = false;
                view.getSettings().setTextZoom(100);
                for (int i = 0; i < 10 && view.zoomOut(); i++) { /* kembalikan ke skala 100% */ }
                CookieManager.getInstance().flush();
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest req, WebResourceError err) {
                if (req.isForMainFrame()) {
                    mainError = true;
                    showError(online() ? "Server Google tidak menjawab (" + err.getDescription() + "). Coba lagi sebentar." : "Tidak ada koneksi internet. Nyalakan data seluler atau Wi-Fi sekolah, lalu coba lagi.");
                }
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int p) {
                bar.setProgress(p);
                bar.setVisibility(p >= 100 ? View.GONE : View.VISIBLE);
            }

            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(() -> {
                    if (!asalTepercaya(request.getOrigin())) { request.deny(); return; }
                    boolean wantsVideo = false;
                    for (String r : request.getResources()) if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(r)) wantsVideo = true;
                    if (!wantsVideo) { request.deny(); return; }
                    if (punya(Manifest.permission.CAMERA)) {
                        request.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
                    } else {
                        if (pendingMedia != null) pendingMedia.deny();
                        pendingMedia = request;
                        requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
                    }
                });
            }

            @Override
            public void onPermissionRequestCanceled(PermissionRequest request) {
                if (pendingMedia == request) pendingMedia = null;
            }

            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                if (!asalTepercaya(Uri.parse(origin))) { callback.invoke(origin, false, false); return; }
                if (punya(Manifest.permission.ACCESS_FINE_LOCATION) || punya(Manifest.permission.ACCESS_COARSE_LOCATION)) {
                    callback.invoke(origin, true, false);
                } else {
                    pendingGeoOrigin = origin;
                    pendingGeoCb = callback;
                    requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
                }
            }

            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                pendingFileParams = params;
                if (terimaGambar(params) && !punya(Manifest.permission.CAMERA)) {
                    requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA_FOR_FILE);
                } else {
                    bukaPemilihFile(params);
                }
                return true;
            }
        });
    }

    /** Menekan tombol tutup (X) pada pita pemberitahuan Google di atas halaman, sama seperti pengguna menekannya. */
    private static final String TUTUP_PITA =
        "(function(){var n=0;function cari(){n++;var el=null,all=document.querySelectorAll('div,section,aside');" +
        "for(var i=0;i<all.length;i++){var t=all[i].textContent||'';if(t.length<400&&/Google Apps Script/i.test(t)){el=all[i];break;}}" +
        "if(el){var b=null,p=el;for(var k=0;k<5&&p&&!b;k++){b=p.querySelector('[aria-label=\"Tutup\"],[aria-label=\"Close\"],[aria-label=\"Dismiss\"],[aria-label=\"Tutup pemberitahuan\"],button[aria-label],div[role=button][aria-label]');p=p.parentElement;}" +
        "if(b){b.click();return;}}if(n<40)setTimeout(cari,250);}cari();})();";

    private boolean punya(String perm) { return checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED; }

    private static boolean terimaGambar(WebChromeClient.FileChooserParams p) {
        String[] acc = p.getAcceptTypes();
        if (acc == null || acc.length == 0) return true;
        for (String a : acc) if (a == null || a.isEmpty() || a.startsWith("image") || a.equals("*/*")) return true;
        return false;
    }

    private void bukaPemilihFile(WebChromeClient.FileChooserParams params) {
        List<Intent> extra = new ArrayList<>();
        cameraOutput = null;
        if (terimaGambar(params) && punya(Manifest.permission.CAMERA)) {
            try {
                File dir = new File(getCacheDir(), "foto");
                if (!dir.exists()) dir.mkdirs();
                File f = new File(dir, "qr_" + System.currentTimeMillis() + ".jpg");
                cameraOutput = FileProvider.getUriForFile(this, getPackageName() + ".files", f);
                Intent cam = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                cam.putExtra(MediaStore.EXTRA_OUTPUT, cameraOutput);
                cam.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                extra.add(cam);
            } catch (Exception ignored) { cameraOutput = null; }
        }
        Intent pick = new Intent(Intent.ACTION_GET_CONTENT);
        pick.addCategory(Intent.CATEGORY_OPENABLE);
        String[] acc = params.getAcceptTypes();
        String type = (acc != null && acc.length == 1 && acc[0] != null && !acc[0].isEmpty() && !acc[0].startsWith(".")) ? acc[0] : "*/*";
        pick.setType(type);
        if (params.getMode() == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE) pick.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);

        Intent chosen;
        if (params.isCaptureEnabled() && !extra.isEmpty()) {
            chosen = extra.get(0); // input capture: langsung buka kamera
        } else {
            chosen = Intent.createChooser(pick, "Pilih file");
            if (!extra.isEmpty()) chosen.putExtra(Intent.EXTRA_INITIAL_INTENTS, extra.toArray(new Intent[0]));
        }
        try {
            startActivityForResult(chosen, REQ_FILE);
        } catch (Exception e) {
            if (fileCallback != null) fileCallback.onReceiveValue(null);
            fileCallback = null;
            Toast.makeText(this, "Kamera / pemilih file tidak tersedia", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_FILE || fileCallback == null) return;
        Uri[] res = null;
        if (resultCode == RESULT_OK) {
            if (data != null && (data.getData() != null || data.getClipData() != null)) {
                if (data.getClipData() != null) {
                    int n = data.getClipData().getItemCount();
                    res = new Uri[n];
                    for (int i = 0; i < n; i++) res[i] = data.getClipData().getItemAt(i).getUri();
                } else {
                    res = new Uri[]{data.getData()};
                }
            } else if (cameraOutput != null) {
                res = new Uri[]{cameraOutput};
            }
        }
        fileCallback.onReceiveValue(res);
        fileCallback = null;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        boolean ok = results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED;
        if (requestCode == REQ_CAMERA && pendingMedia != null) {
            if (ok) pendingMedia.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
            else { pendingMedia.deny(); Toast.makeText(this, "Izin kamera ditolak. Aktifkan di Pengaturan > Aplikasi > SMART LEAD > Izin.", Toast.LENGTH_LONG).show(); }
            pendingMedia = null;
        } else if (requestCode == REQ_LOCATION && pendingGeoCb != null) {
            boolean any = false;
            for (int r : results) if (r == PackageManager.PERMISSION_GRANTED) any = true;
            pendingGeoCb.invoke(pendingGeoOrigin, any, false);
            if (!any) Toast.makeText(this, "Izin lokasi ditolak. Presensi membutuhkan lokasi.", Toast.LENGTH_LONG).show();
            pendingGeoCb = null;
            pendingGeoOrigin = null;
        } else if (requestCode == REQ_CAMERA_FOR_FILE && pendingFileParams != null) {
            bukaPemilihFile(pendingFileParams);
        } else if (requestCode == REQ_NOTIF) {
            ambilTokenPush();
        }
    }

    /* ---------- Notifikasi (Firebase Cloud Messaging) ---------- */
    private boolean pushSiap() {
        if (!BuildConfig.PUSH) return false;
        try { return !FirebaseApp.getApps(this).isEmpty(); } catch (Exception e) { return false; }
    }
    private boolean notifBoleh() {
        return Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }
    /** Halaman meminta token: minta izin notifikasi (Android 13+), lalu kirim token ke halaman untuk didaftarkan di server. */
    private void mintaPush(JavaScriptReplyProxy rp) {
        pushProxy = rp;
        if (!pushSiap()) return;
        if (!notifBoleh()) { requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF); return; }
        ambilTokenPush();
    }
    private void ambilTokenPush() {
        if (pushProxy == null || !pushSiap()) return;
        final JavaScriptReplyProxy rp = pushProxy;
        try {
            FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
                try {
                    String tok = task.isSuccessful() && task.getResult() != null ? task.getResult() : "";
                    if (!tok.isEmpty()) getSharedPreferences("sl", MODE_PRIVATE).edit().putString("fcm", tok).apply();
                    balas(rp, new JSONObject().put("t", "push").put("token", tok).put("izin", notifBoleh()));
                } catch (Exception ignored) { }
            });
        } catch (Exception ignored) { }
    }

    /** Notifikasi diketuk saat aplikasi sudah terbuka: minta halaman pindah ke menu terkait. */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String h = intent != null ? intent.getStringExtra("halaman") : null;
        if (h == null || h.isEmpty()) return;
        boolean terkirim = false;
        if (lastProxy != null) { try { lastProxy.postMessage(new JSONObject().put("t", "buka").put("halaman", h).toString()); terkirim = true; } catch (Exception ignored) { } }
        if (!terkirim) pendingHalaman = h;
    }

    /** Menyimpan file kiriman halaman ke folder Download, lalu membukanya. */
    private void balas(JavaScriptReplyProxy rp, JSONObject o) {
        try { rp.postMessage(o.toString()); } catch (Exception ignored) { }
    }

    // Pemindai QR bawaan Google Play services: tidak butuh izin kamera WebView/halaman.
    private void pindaiQR(JavaScriptReplyProxy rp) {
        try {
            GmsBarcodeScannerOptions opsi = new GmsBarcodeScannerOptions.Builder()
                    .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                    .enableAutoZoom()
                    .build();
            GmsBarcodeScanner sc = GmsBarcodeScanning.getClient(this, opsi);
            sc.startScan()
                    .addOnSuccessListener(b -> {
                        try { balas(rp, new JSONObject().put("t", "scan").put("ok", true).put("data", b.getRawValue() == null ? "" : b.getRawValue())); } catch (Exception ignored) { }
                    })
                    .addOnCanceledListener(() -> {
                        try { balas(rp, new JSONObject().put("t", "scan").put("ok", false).put("batal", true)); } catch (Exception ignored) { }
                    })
                    .addOnFailureListener(e -> {
                        try { balas(rp, new JSONObject().put("t", "scan").put("ok", false).put("err", String.valueOf(e.getMessage()))); } catch (Exception ignored) { }
                    });
        } catch (Exception e) {
            try { balas(rp, new JSONObject().put("t", "scan").put("ok", false).put("err", String.valueOf(e.getMessage()))); } catch (Exception ignored) { }
        }
    }

    private void simpanFile(String name, String mime, String b64) {
        try {
            String safe = name.replaceAll("[\\\\/:*?\"<>|]", "_");
            if (safe.isEmpty()) safe = "unduhan";
            byte[] bytes = Base64.decode(b64, Base64.DEFAULT);
            Uri uri;
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues v = new ContentValues();
                v.put(MediaStore.Downloads.DISPLAY_NAME, safe);
                v.put(MediaStore.Downloads.MIME_TYPE, mime);
                v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/SMART LEAD");
                uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                if (uri == null) throw new Exception("insert");
                try (OutputStream os = getContentResolver().openOutputStream(uri)) { if (os == null) throw new Exception("stream"); os.write(bytes); }
            } else {
                File dir = new File(getExternalFilesDir(null), "Download");
                if (!dir.exists()) dir.mkdirs();
                File f = new File(dir, safe);
                try (FileOutputStream os = new FileOutputStream(f)) { os.write(bytes); }
                uri = FileProvider.getUriForFile(this, getPackageName() + ".files", f);
            }
            Toast.makeText(this, "Tersimpan di Download/SMART LEAD: " + safe, Toast.LENGTH_LONG).show();
            try {
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setDataAndType(uri, mime);
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(i, "Buka " + safe));
            } catch (Exception ignored) { }
        } catch (Exception e) {
            Toast.makeText(this, "Gagal menyimpan file", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onBackPressed() {
        if (errorView.getVisibility() == View.VISIBLE) { super.onBackPressed(); return; }
        if (web.canGoBack()) { web.goBack(); return; }
        long now = System.currentTimeMillis();
        if (now - lastBack < 2000) { super.onBackPressed(); return; }
        lastBack = now;
        Toast.makeText(this, "Tekan sekali lagi untuk keluar", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    protected void onResume() { super.onResume(); web.onResume(); }

    @Override
    protected void onPause() { web.onPause(); CookieManager.getInstance().flush(); super.onPause(); }

    @Override
    protected void onDestroy() {
        if (web != null) { web.stopLoading(); web.destroy(); }
        super.onDestroy();
    }
}
