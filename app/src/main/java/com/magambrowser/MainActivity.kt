package com.magambrowser

import android.graphics.Bitmap
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.net.URLEncoder

class MainActivity : AppCompatActivity() {
    
    private lateinit var webView: WebView
    private lateinit var urlEditText: EditText
    private lateinit var goButton: ImageButton
    private lateinit var backButton: ImageButton
    private lateinit var refreshButton: ImageButton
    private lateinit var securityButton: ImageButton

    // ADBLOCK LISTA
    private val blockedDomains = listOf(
        "doubleclick.net", "googleadservices.com", "googlesyndication.com",
        "adsystem.com", "adservice.google.com", "facebook.com/tr/",
        "analytics.com", "tracking.com", "youtube.com/api/stats/ads",
        "youtube.com/pagead/", "youtube.com/ptracking"
    )

    // BIZTONSÁGOS FÁJLTÍPUSOK
    private val safeFileTypes = listOf(
        "pdf", "txt", "doc", "docx", "xls", "xlsx", "jpg", "jpeg", "png", 
        "gif", "bmp", "webp", "mp3", "wav", "ogg", "mp4", "avi", "mkv", "apk"
    )

    // KERESŐMOTOROK
    private val searchEngines = mapOf(
        "DuckDuckGo" to "https://duckduckgo.com/?q=",
        "Startpage" to "https://www.startpage.com/sp/search?q=",
        "Google" to "https://www.google.com/search?q=",
        "Bing" to "https://www.bing.com/search?q="
    )
    
    private var currentSearchEngine = "DuckDuckGo"
    private var currentSecurityLevel = "🔒 BIZTONSÁGOS"
    private var isUrlEditTextProgrammaticChange = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // ELEMENTEK ÖSSZEKÖTÉSE
        webView = findViewById(R.id.webView)
        urlEditText = findViewById(R.id.urlEditText)
        goButton = findViewById(R.id.goButton)
        backButton = findViewById(R.id.backButton)
        refreshButton = findViewById(R.id.refreshButton)
        securityButton = findViewById(R.id.securityButton)

        // ✅ COPY-PASTE JAVÍTÁSOK BEÁLLÍTÁSA
        setupUrlEditText()

        // WEBVIEW BEÁLLÍTÁSOK
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            useWideViewPort = true
            loadWithOverviewMode = true
        }

        // WEBVIEW CLIENT - MINDEN VÉDELEMMEL
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                if (url == null) return true
                
                if (isUrlBlocked(url)) {
                    Toast.makeText(this@MainActivity, "Reklám blokkolva", Toast.LENGTH_SHORT).show()
                    return true
                }
                
                if (isDownloadUrl(url)) {
                    handleDownload(url)
                    return true
                }
                
                updateSecurityIndicator(url)
                
                return when {
                    url.startsWith("https://") -> false
                    url.startsWith("http://") -> true
                    else -> true
                }
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                // ✅ OLDAL BETÖLTÉSKOR URL RÖVIDÍTÉS
                if (url != null) {
                    isUrlEditTextProgrammaticChange = true
                    urlEditText.setText(shortenUrlForDisplay(url))
                    isUrlEditTextProgrammaticChange = false
                }
            }
        }

        // GOMB ESEMÉNYEK
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

        goButton.setOnLongClickListener {
            showSearchEngineSelector()
            true
        }

        // KEZDŐ OLDAL
        loadUrl("https://duckduckgo.com")
    }

    // ✅ COPY-PASTE JAVÍTÁSOK
    private fun setupUrlEditText() {
        // Fókuszáláskor teljes kijelölés
        urlEditText.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                urlEditText.selectAll()
            }
        }
        
        // Kattintáskor teljes kijelölés
        urlEditText.setOnClickListener {
            urlEditText.selectAll()
        }
        
        // Hosszú szövegek kezelése
        urlEditText.ellipsize = TextUtils.TruncateAt.START
        
        // Real-time URL formázás
        urlEditText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                if (isUrlEditTextProgrammaticChange) return
                
                s?.let {
                    if (it.length > 50 && !it.toString().startsWith("http")) {
                        val displayText = shortenUrlForDisplay(it.toString())
                        if (displayText != it.toString()) {
                            urlEditText.removeTextChangedListener(this)
                            urlEditText.setText(displayText)
                            urlEditText.setSelection(displayText.length)
                            urlEditText.addTextChangedListener(this)
                        }
                    }
                }
            }
            
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    // ✅ URL RÖVIDÍTÉS
    private fun shortenUrlForDisplay(fullUrl: String): String {
        return when {
            fullUrl.length > 60 -> {
                val domain = fullUrl.substringAfter("://").substringBefore("/")
                val path = fullUrl.substringAfter(domain, "")
                if (path.length > 20) {
                    "$domain/...${path.takeLast(15)}"
                } else {
                    "$domain$path"
                }
            }
            else -> fullUrl
        }
    }

    // ✅ BEVITEL TISZTÍTÁS
    private fun sanitizeInput(input: String): String {
        val dangerousPatterns = listOf(
            "javascript:", "data:", "vbscript:", "file://"
        )
        
        var sanitized = input
        dangerousPatterns.forEach { pattern ->
            if (sanitized.contains(pattern, ignoreCase = true)) {
                sanitized = sanitized.replace(pattern, "", ignoreCase = true)
            }
        }
        
        return sanitized.trim()
    }

    // URL BETÖLTÉS VAGY KERESÉS
    private fun loadUrlOrSearch(input: String) {
        val cleanInput = sanitizeInput(input)
        
        when {
            cleanInput.matches(Regex("^https?://.*")) -> loadUrl(cleanInput)
            cleanInput.matches(Regex("^[a-zA-Z0-9-]+\\.[a-zA-Z]{2,}.*")) -> loadUrl(cleanInput)
            cleanInput.contains(".") && !cleanInput.contains(" ") -> loadUrl(cleanInput)
            else -> safeSearch(cleanInput)
        }
    }

    private fun loadUrl(url: String) {
        var finalUrl = url
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            finalUrl = "https://$url"
        }
        if (finalUrl.startsWith("http://")) {
            finalUrl = finalUrl.replace("http://", "https://")
        }
        webView.loadUrl(finalUrl)
    }

    // ✅ BIZTONSÁGOS KERESÉS - KIJELÖLÉSSEL
    private fun safeSearch(query: String) {
        val baseUrl = searchEngines[currentSearchEngine] ?: searchEngines["DuckDuckGo"]!!
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val searchUrl = baseUrl + encodedQuery
        loadUrl(searchUrl)
        
        // ✅ Keresés után csak a keresőkifejezés, teljes kijelöléssel
        isUrlEditTextProgrammaticChange = true
        urlEditText.setText(query)
        urlEditText.setSelection(0, query.length)
        isUrlEditTextProgrammaticChange = false
        
        Toast.makeText(this, "Kereső: $currentSearchEngine", Toast.LENGTH_SHORT).show()
    }

    // KERESŐMOTOR VÁLASZTÓ
    private fun showSearchEngineSelector() {
        val engines = searchEngines.keys.toTypedArray()
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Keresőmotor választás")
            .setItems(engines) { _, which ->
                currentSearchEngine = engines[which]
                Toast.makeText(this, "Kereső: $currentSearchEngine", Toast.LENGTH_LONG).show()
            }
            .show()
    }

    // BIZTONSÁGI INDIKÁTOR
    private fun updateSecurityIndicator(url: String) {
        currentSecurityLevel = when {
            url.startsWith("https://") -> "🔒 BIZTONSÁGOS"
            url.startsWith("http://") -> "⚠️ NEM BIZTONSÁGOS"
            else -> "❌ BLOKKOLVA"
        }
        securityButton.contentDescription = currentSecurityLevel
    }

    private fun showSecurityInfo() {
        Toast.makeText(this, currentSecurityLevel, Toast.LENGTH_LONG).show()
    }

    // ADBLOCK
    private fun isUrlBlocked(url: String): Boolean {
        return blockedDomains.any { domain -> url.contains(domain) }
    }

    // LETÖLTÉS ÉSZLELÉS
    private fun isDownloadUrl(url: String): Boolean {
        val downloadIndicators = listOf(
            "download", "attachment", ".zip", ".rar", ".exe", ".msi",
            "force-download", "download-file"
        )
        return downloadIndicators.any { indicator -> url.contains(indicator, ignoreCase = true) }
    }

    // ✅ LETÖLTÉS KEZELÉS - JAVÍTOTT (.toLowerCase())
    private fun handleDownload(url: String) {
        val fileExtension = url.substringAfterLast('.', "").toLowerCase() // ✅ JAVÍTVA
        
        when {
            fileExtension == "apk" -> {
                Toast.makeText(this, "APK letöltés - csak megbízható forrásból!", Toast.LENGTH_LONG).show()
            }
            safeFileTypes.contains(fileExtension) -> {
                Toast.makeText(this, "Biztonságos letöltés: $fileExtension", Toast.LENGTH_SHORT).show()
            }
            else -> {
                Toast.makeText(this, "Letöltés blokkolva: $fileExtension", Toast.LENGTH_LONG).show()
            }
        }
    }

    // MEMÓRIA OPTIMALIZÁLÁS
    override fun onDestroy() {
        webView.clearCache(true)
        webView.clearHistory()
        super.onDestroy()
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
