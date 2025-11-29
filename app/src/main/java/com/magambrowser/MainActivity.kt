package com.magambrowser

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.text.TextUtils
import android.view.View
import android.webkit.*
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import android.view.WindowManager
import android.content.pm.ActivityInfo
import android.content.SharedPreferences
import java.net.URLEncoder
import androidx.core.content.FileProvider
import java.io.File

class MainActivity : AppCompatActivity() {
    
    private lateinit var webView: WebView
    private lateinit var urlEditText: EditText
    private lateinit var goButton: ImageButton
    private lateinit var backButton: ImageButton
    private lateinit var refreshButton: ImageButton
    private lateinit var securityButton: ImageButton
    private lateinit var jsToggleButton: ImageButton

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var downloadManager: DownloadManager
    private var downloadId: Long = -1

    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var isFullscreen = false

    // LETÖLTÉS BEFEJEZÉS FIGYELŐ
    private val downloadCompleteReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            if (id == downloadId) {
                Toast.makeText(this@MainActivity, "✅ Letöltés kész!", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ... (a többi változó marad)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sharedPreferences = getSharedPreferences("BrowserSettings", Context.MODE_PRIVATE)
        downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        
        // Download receiver regisztrálása
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        registerReceiver(downloadCompleteReceiver, filter)

        loadSavedSettings()
        initViews()
        setupWebView()
        setupEventListeners()
        setupUrlEditText()

        loadUrl("https://duckduckgo.com")
    }

    // ... (egyéb metódusok változatlanul)

    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = true
            allowContentAccess = true
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            
            // LETÖLTÉSHEZ SZÜKSÉGES BEÁLLÍTÁSOK
            allowUniversalAccessFromFileURLs = true
            allowFileAccessFromFileURLs = true
        }

        // 🔥 MEGÚJULT WEBCHROMECLIENT LETÖLTÉSEKHEZ
        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                // ... (fullscreen kód változatlan)
            }

            override fun onHideCustomView() {
                // ... (fullscreen kód változatlan)
            }

            // FONTOS: File upload támogatás
            override fun onShowFileChooser(
                webView: WebView?, 
                filePathCallback: ValueCallback<Array<Uri>>?, 
                fileChooserParams: FileChooserParams?
            ): Boolean {
                // Egyszerűsített fájlválasztó
                Toast.makeText(this@MainActivity, "Fájlválasztás nem támogatott", Toast.LENGTH_SHORT).show()
                return false
            }
        }

        // 🔥 MEGÚJULT WEBVIEWCLIENT LETÖLTÉSEKKEZ
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                if (url == null) return true
                
                if (isUrlBlocked(url)) {
                    Toast.makeText(this@MainActivity, "🚫 Reklám blokkolva", Toast.LENGTH_SHORT).show()
                    return true
                }
                
                // 🔥 JAVÍTOTT LETÖLTÉS DETEKTÁLÁS
                if (isDownloadableFile(url)) {
                    handleDownload(url)
                    return true
                }
                
                updateSecurityIndicator(url)
                return false
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                if (url != null) {
                    isUrlEditTextProgrammaticChange = true
                    urlEditText.setText(shortenUrlForDisplay(url))
                    isUrlEditTextProgrammaticChange = false
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (url != null) {
                    updateSecurityIndicator(url)
                    
                    // 🔥 AUTOMATIKUS LETÖLTÉS DETEKTÁLÁS HTML5 ATTRIBÚTUMOKBÓL
                    view?.evaluateJavascript("""
                        var links = document.querySelectorAll('a[download], button[download]');
                        links.forEach(function(link) {
                            link.setAttribute('data-android-download', 'true');
                        });
                    """.trimIndent(), null)
                }
            }

            // 🔥 ÚJ: Blob URL-ek kezelése (modern letöltések)
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                request?.url?.let { url ->
                    if (url.toString().startsWith("blob:") && isDownloadableFile(url.toString())) {
                        runOnUiThread {
                            Toast.makeText(this@MainActivity, "📥 Blob letöltés észlelve", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                return super.shouldInterceptRequest(view, request)
            }
        }

        // 🔥 DIRECT DOWNLOAD HANDLER
        webView.setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
            handleDownload(url)
        }
    }

    // 🔥 TELJESEN ÁTÍRT LETÖLTÉSI METÓDUS
    private fun handleDownload(url: String) {
        try {
            // Fájlnév generálás
            var fileName = URLUtil.guessFileName(url, null, null)
            if (fileName.length < 4) {
                fileName = "download_${System.currentTimeMillis()}.bin"
            }

            // DownloadManager request
            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle(fileName)
                .setDescription("Letöltés: $fileName")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)

            // MIME típus beállítás
            val mimeType = URLConnection.guessContentTypeFromName(fileName)
            if (mimeType != null) {
                request.setMimeType(mimeType)
            }

            // Célkönyvtár beállítás (Android 10+ kompatibilis)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ - Scoped Storage
                request.setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS, 
                    "MyWebBrowser/$fileName"
                )
            } else {
                // Android 9- - Régi rendszer
                request.setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS, 
                    fileName
                )
            }

            // Letöltés indítása
            downloadId = downloadManager.enqueue(request)
            
            Toast.makeText(
                this, 
                "📥 Letöltés elindult: $fileName", 
                Toast.LENGTH_LONG
            ).show()

        } catch (e: SecurityException) {
            Toast.makeText(this, "❌ Nincs letöltési jogosultság", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "❌ Letöltési hiba: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // 🔥 JAVÍTOTT FÁJLTÍPUS DETEKTÁLÁS
    private fun isDownloadableFile(url: String): Boolean {
        val cleanUrl = url.toLowerCase().split('?')[0]
        val fileExtension = cleanUrl.substringAfterLast('.').trim()
        
        if (fileExtension.length > 10) return false // Túl hosszú kiterjesztés
        
        val downloadableExtensions = listOf(
            "pdf", "zip", "rar", "7z", "tar", "gz", "apk",
            "mp4", "avi", "mkv", "mov", "wmv", "flv", "webm",
            "mp3", "wav", "flac", "aac", "ogg", "m4a",
            "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "jpg", "jpeg", "png", "gif", "webp", "bmp", "svg",
            "exe", "msi", "dmg", "pkg", "deb", "rpm"
        )
        
        return downloadableExtensions.any { it == fileExtension } ||
               url.contains("download") ||
               url.contains("attachment") ||
               url.contains("blob:")
    }

    override fun onDestroy() {
        super.onDestroy()
        // Receiver leiratkozás
        try {
            unregisterReceiver(downloadCompleteReceiver)
        } catch (e: Exception) {
            // Már nincs regisztrálva
        }
        
        webView.clearCache(true)
        webView.clearHistory()
    }
}
