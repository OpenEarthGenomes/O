package com.magambrowser

import android.app.DownloadManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.View
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import android.view.WindowManager
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.SharedPreferences
import java.net.URLEncoder

class MainActivity : AppCompatActivity() {
    
    private lateinit var webView: WebView
    private lateinit var urlEditText: EditText
    private lateinit var goButton: ImageButton
    private lateinit var backButton: ImageButton
    private lateinit var refreshButton: ImageButton
    private lateinit var securityButton: ImageButton
    private lateinit var jsToggleButton: ImageButton

    private lateinit var sharedPreferences: SharedPreferences

    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var isFullscreen = false

    private val blockedDomains = listOf(
        "doubleclick.net", "googleadservices.com", "googlesyndication.com",
        "adsystem.com", "adservice.google.com", "facebook.com/tr/",
        "analytics.com", "tracking.com", "youtube.com/api/stats/ads",
        "youtube.com/pagead/", "youtube.com/ptracking"
    )

    private val searchEngines = mapOf(
        "DuckDuckGo" to "https://duckduckgo.com/?q=",
        "Startpage" to "https://www.startpage.com/sp/search?q=",
        "Google" to "https://www.google.com/search?q=",
        "Bing" to "https://www.bing.com/search?q="
    )
    
    private val supportedFileTypes = listOf(
        "html", "htm", "xhtml", "php", "asp", "jsp",
        "mp4", "webm", "ogg", "mp3", "wav", "m4a",
        "jpg", "jpeg", "png", "gif", "webp", "svg", "bmp",
        "pdf", "txt", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
        "zip", "rar", "7z", "tar", "gz", "apk"
    )
    
    private var currentSearchEngine = "DuckDuckGo"
    private var currentSecurityLevel = "🔒 BIZTONSÁGOS"
    private var isUrlEditTextProgrammaticChange = false
    private var isJavaScriptEnabled = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sharedPreferences = getSharedPreferences("BrowserSettings", Context.MODE_PRIVATE)
        loadSavedSettings()

        initViews()
        setupWebView()
        setupEventListeners()
        setupUrlEditText()

        loadUrl("https://duckduckgo.com")
    }

    private fun initViews() {
        webView = findViewById(R.id.webView)
        urlEditText = findViewById(R.id.urlEditText)
        goButton = findViewById(R.id.goButton)
        backButton = findViewById(R.id.backButton)
        refreshButton = findViewById(R.id.refreshButton)
        securityButton = findViewById(R.id.securityButton)
        jsToggleButton = findViewById(R.id.jsToggleButton)
    }

    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = false
            allowContentAccess = true
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (customView != null) {
                    callback?.onCustomViewHidden()
                    return
                }
                
                customView = view
                customViewCallback = callback
                isFullscreen = true
                
                val decorView = window.decorView as FrameLayout
                decorView.addView(customView)
                
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
                window.setFlags(
                    WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN
                )
                supportActionBar?.hide()
                
                webView.visibility = View.GONE
            }

            override fun onHideCustomView() {
                if (!isFullscreen || customView == null) return
                
                val decorView = window.decorView as FrameLayout
                decorView.removeView(customView)
                
                customViewCallback?.onCustomViewHidden()
                customView = null
                customViewCallback = null
                isFullscreen = false
                
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
                supportActionBar?.show()
                
                webView.visibility = View.VISIBLE
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                if (url == null) return true
                
                if (isUrlBlocked(url)) {
                    Toast.makeText(this@MainActivity, "🚫 Reklám blokkolva", Toast.LENGTH_SHORT).show()
                    return true
                }
                
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
                }
            }
        }
    }

    private fun setupEventListeners() {
        goButton.setOnClickListener {
            val input = urlEditText.text.toString()
            loadUrlOrSearch(input)
        }

        backButton.setOnClickListener {
            if (webView.canGoBack()) webView.goBack()
        }

        refreshButton.setOnClickListener {
            webView.reload()
        }

        securityButton.setOnClickListener {
            showSecurityInfo()
        }

        jsToggleButton.setOnClickListener {
            toggleJavaScript()
        }

        goButton.setOnLongClickListener {
            showSearchEngineSelector()
            true
        }
    }

    private fun setupUrlEditText() {
        urlEditText.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) urlEditText.selectAll()
        }
        urlEditText.setOnClickListener { urlEditText.selectAll() }
        urlEditText.ellipsize = TextUtils.TruncateAt.START
    }

    private fun loadSavedSettings() {
        currentSearchEngine = sharedPreferences.getString("search_engine", "DuckDuckGo") ?: "DuckDuckGo"
        isJavaScriptEnabled = sharedPreferences.getBoolean("javascript_enabled", true)
    }

    private fun saveSettings() {
        val editor = sharedPreferences.edit()
        editor.putString("search_engine", currentSearchEngine)
        editor.putBoolean("javascript_enabled", isJavaScriptEnabled)
        editor.apply()
    }

    private fun showSearchEngineSelector() {
        val engines = searchEngines.keys.toTypedArray()
        val currentIndex = engines.indexOf(currentSearchEngine)
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Keresőmotor választás")
            .setSingleChoiceItems(engines, currentIndex) { _, which ->
                currentSearchEngine = engines[which]
                saveSettings()
                Toast.makeText(this, "✅ Kereső: $currentSearchEngine", Toast.LENGTH_LONG).show()
            }
            .setPositiveButton("OK", null)
            .show()
    }

    private fun updateSecurityIndicator(url: String) {
        when {
            url.startsWith("https://") -> {
                securityButton.setImageResource(android.R.drawable.presence_online)
                currentSecurityLevel = "🔒 BIZTONSÁGOS"
            }
            url.startsWith("http://") -> {
                securityButton.setImageResource(android.R.drawable.presence_busy)
                currentSecurityLevel = "⚠️ NEM BIZTONSÁGOS"
            }
            else -> {
                securityButton.setImageResource(android.R.drawable.presence_offline)
                currentSecurityLevel = "❌ BLOKKOLVA"
            }
        }
        securityButton.contentDescription = currentSecurityLevel
    }

    private fun showSecurityInfo() {
        Toast.makeText(this, currentSecurityLevel, Toast.LENGTH_LONG).show()
    }

    private fun toggleJavaScript() {
        isJavaScriptEnabled = !isJavaScriptEnabled
        webView.settings.javaScriptEnabled = isJavaScriptEnabled
        
        if (isJavaScriptEnabled) {
            jsToggleButton.setImageResource(android.R.drawable.ic_lock_lock)
            Toast.makeText(this, "✅ JavaScript engedélyezve", Toast.LENGTH_SHORT).show()
        } else {
            jsToggleButton.setImageResource(android.R.drawable.ic_media_pause)
            Toast.makeText(this, "❌ JavaScript letiltva", Toast.LENGTH_SHORT).show()
        }
        saveSettings()
        webView.reload()
    }

    private fun shortenUrlForDisplay(fullUrl: String): String {
        return when {
            fullUrl.length > 60 -> {
                val domain = fullUrl.substringAfter("://").substringBefore("/")
                val path = fullUrl.substringAfter(domain, "")
                if (path.length > 20) "$domain/...${path.takeLast(15)}" else "$domain$path"
            }
            else -> fullUrl
        }
    }

    private fun sanitizeInput(input: String): String {
        val dangerousPatterns = listOf("javascript:", "data:", "vbscript:", "file://")
        var sanitized = input
        dangerousPatterns.forEach { pattern ->
            if (sanitized.contains(pattern, ignoreCase = true)) {
                sanitized = sanitized.replace(pattern, "", ignoreCase = true)
            }
        }
        return sanitized.trim()
    }

    private fun loadUrlOrSearch(input: String) {
        val cleanInput = sanitizeInput(input)
        
        when {
            cleanInput.startsWith("https://") -> loadUrl(cleanInput)
            cleanInput.startsWith("http://") -> {
                val httpsUrl = cleanInput.replace("http://", "https://")
                loadUrl(httpsUrl)
                Toast.makeText(this, "🔒 HTTPS-re átirányítva", Toast.LENGTH_SHORT).show()
            }
            cleanInput.matches(Regex("^[a-zA-Z0-9-]+\\.[a-zA-Z]{2,}.*")) -> loadUrl("https://$cleanInput")
            cleanInput.contains(".") && !cleanInput.contains(" ") -> loadUrl("https://$cleanInput")
            else -> safeSearch(cleanInput)
        }
    }

    private fun loadUrl(url: String) {
        webView.loadUrl(url)
    }

    private fun safeSearch(query: String) {
        val baseUrl = searchEngines[currentSearchEngine] ?: searchEngines["DuckDuckGo"]!!
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val searchUrl = baseUrl + encodedQuery
        loadUrl(searchUrl)
        
        isUrlEditTextProgrammaticChange = true
        urlEditText.setText(query)
        urlEditText.setSelection(0, query.length)
        isUrlEditTextProgrammaticChange = false
        
        Toast.makeText(this, "🔍 Kereső: $currentSearchEngine", Toast.LENGTH_SHORT).show()
    }

    private fun isUrlBlocked(url: String): Boolean {
        return blockedDomains.any { domain -> url.contains(domain) }
    }

    private fun isDownloadableFile(url: String): Boolean {
        val fileExtension = url.substringAfterLast('.').toLowerCase()
        return supportedFileTypes.any { it == fileExtension } &&
               (url.contains("download") || url.contains("attachment"))
    }

    private fun handleDownload(url: String) {
        try {
            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle("Letöltés: ${URLUtil.guessFileName(url, null, null)}")
                .setDescription("Fájl letöltése")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)

            val fileName = URLUtil.guessFileName(url, null, null)
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)

            val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadManager.enqueue(request)
            
            Toast.makeText(this, "📥 Letöltés elindult", Toast.LENGTH_LONG).show()
            
        } catch (e: Exception) {
            Toast.makeText(this, "❌ Letöltési hiba", Toast.LENGTH_LONG).show()
        }
    }

    override fun onBackPressed() {
        if (isFullscreen) {
            webView.webChromeClient?.onHideCustomView()
        } else if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        webView.clearCache(true)
        webView.clearHistory()
        super.onDestroy()
    }
}
