package id.popstudio.editor;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * POP Studio native Android shell. Semua komponen editor dan gambar contoh
 * terkemas di assets/index.html; tidak memerlukan koneksi internet.
 *
 * Format output disimpan melalui MediaStore tanpa meminta izin penyimpanan
 * untuk Android 10 ke atas. Photo picker memakai file chooser Android.
 */
public final class MainActivity extends Activity {
    private static final int FILE_PICKER_REQUEST = 8120;
    private static final String BASE_URL = "https://pop-studio.local/";

    private final ExecutorService diskWorker = Executors.newSingleThreadExecutor();
    private WebView editor;
    private ValueCallback<Uri[]> pendingFiles;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(16, 24, 39));
        getWindow().setNavigationBarColor(Color.rgb(16, 24, 39));

        editor = new WebView(this);
        editor.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        editor.setBackgroundColor(Color.WHITE);
        setContentView(editor);

        WebSettings settings = editor.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setDefaultTextEncodingName("UTF-8");
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);

        // Hanya konten sendiri yang dimuat ke WebView. Tidak ada izin INTERNET.
        editor.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return !BASE_URL.equals(request.getUrl().toString());
            }
        });
        editor.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView webView,
                                             ValueCallback<Uri[]> filePathCallback,
                                             FileChooserParams fileChooserParams) {
                if (pendingFiles != null) pendingFiles.onReceiveValue(null);
                pendingFiles = filePathCallback;
                try {
                    Intent select = new Intent(Intent.ACTION_GET_CONTENT);
                    select.addCategory(Intent.CATEGORY_OPENABLE);
                    select.setType("image/*");
                    select.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivityForResult(Intent.createChooser(select, "Pilih gambar dari galeri"),
                            FILE_PICKER_REQUEST);
                    return true;
                } catch (Exception error) {
                    pendingFiles = null;
                    filePathCallback.onReceiveValue(null);
                    showToast("Galeri tidak tersedia");
                    return true;
                }
            }
        });
        editor.addJavascriptInterface(new LocalBridge(), "NativeBridge");

        try {
            String html = readAsset("index.html");
            // https origin memungkinkan IndexedDB menyimpan pekerjaan otomatis.
            editor.loadDataWithBaseURL(BASE_URL, html, "text/html", "UTF-8", BASE_URL);
        } catch (IOException failure) {
            showToast("Tidak dapat membuka editor. Silakan instal ulang aplikasi.");
        }
    }

    private String readAsset(String file) throws IOException {
        try (InputStream input = getAssets().open(file);
             ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = input.read(buf)) != -1) bytes.write(buf, 0, n);
            return bytes.toString(StandardCharsets.UTF_8.name());
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != FILE_PICKER_REQUEST || pendingFiles == null) return;
        Uri[] chosen = null;
        if (resultCode == RESULT_OK && data != null) {
            if (data.getClipData() != null) {
                int length = data.getClipData().getItemCount();
                chosen = new Uri[length];
                for (int i = 0; i < length; i++) {
                    chosen[i] = data.getClipData().getItemAt(i).getUri();
                }
            } else if (data.getData() != null) {
                chosen = new Uri[] { data.getData() };
            }
        }
        pendingFiles.onReceiveValue(chosen);
        pendingFiles = null;
    }

    private void showToast(String message) {
        runOnUiThread(() -> Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show());
    }

    private void printPoster() {
        PrintManager manager = (PrintManager) getSystemService(Context.PRINT_SERVICE);
        if (manager == null || editor == null) {
            showToast("Layanan cetak tidak tersedia");
            return;
        }
        PrintDocumentAdapter adapter = editor.createPrintDocumentAdapter("POP Studio");
        PrintAttributes options = new PrintAttributes.Builder()
                .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                .build();
        manager.print("POP Studio", adapter, options);
    }

    private void saveInDownloads(String filename, String mime, byte[] bytes) {
        Uri fileUri = null;
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
            values.put(MediaStore.MediaColumns.MIME_TYPE, mime);
            values.put(MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS + "/POP Studio");
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);
            fileUri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (fileUri == null) throw new IOException("MediaStore gagal membuat file");
            try (OutputStream out = getContentResolver().openOutputStream(fileUri)) {
                if (out == null) throw new IOException("Tidak dapat membuka file output");
                out.write(bytes);
                out.flush();
            }
            values.clear();
            values.put(MediaStore.MediaColumns.IS_PENDING, 0);
            getContentResolver().update(fileUri, values, null, null);
            showToast("Tersimpan: Downloads/POP Studio/" + filename);
        } catch (Exception e) {
            if (fileUri != null) {
                try { getContentResolver().delete(fileUri, null, null); } catch (Exception ignored) { }
            }
            showToast("Gagal menyimpan file: " + e.getMessage());
        }
    }

    private final class LocalBridge {
        private ByteArrayOutputStream activeFile;
        private String activeName;
        private String activeMime;
        private static final int MAX_FILE_BYTES = 45 * 1024 * 1024;

        @JavascriptInterface
        public synchronized void beginDownload(String fileName, String mimeType) {
            activeFile = new ByteArrayOutputStream();
            activeName = fileName == null ? "POP-Studio.png" : fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
            if (activeName.isEmpty()) activeName = "POP-Studio.png";
            activeMime = mimeType == null ? "application/octet-stream" : mimeType;
        }

        @JavascriptInterface
        public synchronized void appendBase64(String chunk) {
            if (activeFile == null || chunk == null) return;
            try {
                byte[] part = Base64.decode(chunk, Base64.DEFAULT);
                if (activeFile.size() + part.length > MAX_FILE_BYTES) {
                    activeFile = null;
                    showToast("File terlalu besar (maksimum 45 MB)");
                    return;
                }
                activeFile.write(part, 0, part.length);
            } catch (Exception e) {
                activeFile = null;
                showToast("Gagal mengolah file hasil export");
            }
        }

        @JavascriptInterface
        public synchronized void finishDownload() {
            if (activeFile == null || activeFile.size() == 0) return;
            final byte[] bytes = activeFile.toByteArray();
            final String name = activeName;
            final String mime = activeMime;
            activeFile = null;
            activeName = null;
            activeMime = null;
            diskWorker.execute(() -> saveInDownloads(name, mime, bytes));
        }

        @JavascriptInterface
        public void printDocument() {
            runOnUiThread(MainActivity.this::printPoster);
        }
    }

    @Override
    public void onBackPressed() {
        new AlertDialog.Builder(this)
                .setTitle("Keluar dari POP Studio?")
                .setMessage("Perubahan otomatis disimpan di aplikasi. Untuk cadangan, gunakan Simpan Proyek.")
                .setNegativeButton("Batal", (d, which) -> d.dismiss())
                .setPositiveButton("Keluar", (d, which) -> finish())
                .show();
    }

    @Override
    protected void onDestroy() {
        if (pendingFiles != null) pendingFiles.onReceiveValue(null);
        pendingFiles = null;
        if (editor != null) {
            editor.removeJavascriptInterface("NativeBridge");
            editor.destroy();
            editor = null;
        }
        diskWorker.shutdown();
        super.onDestroy();
    }
}
