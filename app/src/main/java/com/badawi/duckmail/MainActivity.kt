package com.badawi.duckmail

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Html
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val BASE_URL = "https://api.duckmail.sbs"
private const val PREFS = "duckmail_perfect_v1"

data class Mailbox(
    val id: String,
    val address: String,
    val password: String,
    val token: String
)

data class MailSummary(
    val id: String,
    val senderName: String,
    val senderAddress: String,
    val subject: String,
    val seen: Boolean,
    val attachments: Boolean,
    val createdAt: String
)

data class Attachment(
    val filename: String,
    val contentType: String,
    val size: Int,
    val url: String
)

data class MailDetail(
    val id: String,
    val senderName: String,
    val senderAddress: String,
    val to: String,
    val subject: String,
    val text: String,
    val html: String,
    val createdAt: String,
    val attachments: List<Attachment>
)

data class DomainItem(val id: String, val domain: String)

private class DuckApi {
    private fun call(
        method: String,
        path: String,
        token: String? = null,
        body: String? = null
    ): String {
        val c = (URL(BASE_URL + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 20_000
            useCaches = false
            setRequestProperty("Accept", "application/json")
            if (token != null) setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }
        try {
            if (body != null) c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val result = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val msg = runCatching { JSONObject(result).optString("message") }.getOrNull()
                throw IllegalStateException(msg?.takeIf { it.isNotBlank() } ?: "HTTP $code")
            }
            return result
        } finally {
            c.disconnect()
        }
    }

    suspend fun domains(apiKey: String?): List<DomainItem> = withContext(Dispatchers.IO) {
        val c = (URL("$BASE_URL/domains?page=1").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/json")
            if (!apiKey.isNullOrBlank()) setRequestProperty("Authorization", "Bearer $apiKey")
        }
        try {
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IllegalStateException(errorMessage(text, code))
            val arr = JSONObject(text).optJSONArray("hydra:member") ?: JSONArray()
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val d = o.optString("domain")
                    if (d.isNotBlank()) add(DomainItem(o.optString("id"), d))
                }
            }
        } finally {
            c.disconnect()
        }
    }

    suspend fun create(address: String, password: String, expiry: Int?): JSONObject =
        withContext(Dispatchers.IO) {
            val b = JSONObject().put("address", address).put("password", password)
            if (expiry != null) b.put("expiresIn", expiry)
            JSONObject(call("POST", "/accounts", body = b.toString()))
        }

    suspend fun token(address: String, password: String): JSONObject =
        withContext(Dispatchers.IO) {
            JSONObject(call("POST", "/token", body = JSONObject()
                .put("address", address).put("password", password).toString()))
        }

    suspend fun messages(token: String): List<MailSummary> = withContext(Dispatchers.IO) {
        val root = JSONObject(call("GET", "/messages?page=1", token))
        val arr = root.optJSONArray("hydra:member") ?: JSONArray()
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val from = o.optJSONObject("from")
                add(
                    MailSummary(
                        id = o.optString("id"),
                        senderName = from?.optString("name").orEmpty(),
                        senderAddress = from?.optString("address").orEmpty(),
                        subject = o.optString("subject").ifBlank { "(No subject)" },
                        seen = o.optBoolean("seen"),
                        attachments = o.optBoolean("hasAttachments"),
                        createdAt = o.optString("createdAt")
                    )
                )
            }
        }
    }

    suspend fun detail(token: String, id: String): MailDetail = withContext(Dispatchers.IO) {
        val o = JSONObject(call("GET", "/messages/$id", token))
        val from = o.optJSONObject("from")
        val toArr = o.optJSONArray("to")
        val to = if (toArr != null) (0 until toArr.length()).mapNotNull {
            toArr.optJSONObject(it)?.optString("address")?.takeIf { a -> a.isNotBlank() }
        }.joinToString(", ") else ""
        val html = when (val h = o.opt("html")) {
            is JSONArray -> (0 until h.length()).joinToString("\n") { h.optString(it) }
            else -> h?.toString().orEmpty()
        }
        val at = o.optJSONArray("attachments")
        val attachments = buildList {
            if (at != null) {
                for (i in 0 until at.length()) {
                    val a = at.optJSONObject(i) ?: continue
                    add(
                        Attachment(
                            a.optString("filename").ifBlank { "attachment" },
                            a.optString("contentType"),
                            a.optInt("size"),
                            a.optString("downloadUrl")
                        )
                    )
                }
            }
        }
        MailDetail(
            id = o.optString("id"),
            senderName = from?.optString("name").orEmpty(),
            senderAddress = from?.optString("address").orEmpty(),
            to = to,
            subject = o.optString("subject").ifBlank { "(No subject)" },
            text = o.optString("text"),
            html = html,
            createdAt = o.optString("createdAt"),
            attachments = attachments
        )
    }

    suspend fun markRead(token: String, id: String) = withContext(Dispatchers.IO) {
        call("PATCH", "/messages/$id", token)
    }

    suspend fun deleteMessage(token: String, id: String) = withContext(Dispatchers.IO) {
        call("DELETE", "/messages/$id", token)
    }

    suspend fun deleteAccount(token: String, id: String) = withContext(Dispatchers.IO) {
        call("DELETE", "/accounts/$id", token)
    }

    private fun errorMessage(text: String, code: Int): String =
        runCatching { JSONObject(text).optString("message") }
            .getOrNull()?.takeIf { it.isNotBlank() } ?: "HTTP $code"
}

private fun errorMessage(e: Throwable): String = e.message?.takeIf { it.isNotBlank() } ?: "Something went wrong"

private class MailStore(private val context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): List<Mailbox> = runCatching {
        val arr = JSONArray(prefs.getString("accounts", "[]") ?: "[]")
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(Mailbox(
                    o.optString("id"),
                    o.optString("address"),
                    o.optString("password"),
                    o.optString("token")
                ))
            }
        }
    }.getOrDefault(emptyList())

    fun save(list: List<Mailbox>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject()
                .put("id", it.id)
                .put("address", it.address)
                .put("password", it.password)
                .put("token", it.token))
        }
        prefs.edit().putString("accounts", arr.toString()).apply()
    }

    fun apiKey(): String = prefs.getString("apiKey", "").orEmpty()
    fun saveApiKey(v: String) = prefs.edit().putString("apiKey", v).apply()
    fun interval(): Int = prefs.getInt("interval", 15)
    fun saveInterval(v: Int) = prefs.edit().putInt("interval", v).apply()
    fun auto(): Boolean = prefs.getBoolean("auto", true)
    fun saveAuto(v: Boolean) = prefs.edit().putBoolean("auto", v).apply()
    fun selected(): Int = prefs.getInt("selected", 0)
    fun saveSelected(v: Int) = prefs.edit().putInt("selected", v).apply()
}

private class AppState(context: Context, private val scope: CoroutineScope) {
    private val api = DuckApi()
    private val store = MailStore(context)

    var accounts by mutableStateOf(store.load())
    var selectedIndex by mutableIntStateOf(store.selected().coerceAtLeast(0))
    var domains by mutableStateOf<List<DomainItem>>(emptyList())
    var messages by mutableStateOf<List<MailSummary>>(emptyList())
    var detail by mutableStateOf<MailDetail?>(null)
    var loading by mutableStateOf(false)
    var messageError by mutableStateOf<String?>(null)
    var autoRefresh by mutableStateOf(store.auto())
    var interval by mutableIntStateOf(store.interval())
    var apiKey by mutableStateOf(store.apiKey())
    var lastRefresh by mutableStateOf("")
    var query by mutableStateOf("")

    private var refreshJob: Job? = null
    private var lastUnread = 0

    val active: Mailbox? get() = accounts.getOrNull(selectedIndex)

    val filtered: List<MailSummary>
        get() {
            val q = query.trim().lowercase(Locale.ROOT)
            if (q.isBlank()) return messages
            return messages.filter {
                it.senderAddress.lowercase(Locale.ROOT).contains(q) ||
                    it.senderName.lowercase(Locale.ROOT).contains(q) ||
                    it.subject.lowercase(Locale.ROOT).contains(q)
            }
        }

    fun init() {
        if (active != null) {
            refresh()
            restartPolling()
        } else {
            loadDomains()
        }
    }

    fun setError(t: Throwable) { messageError = errorMessage(t) }
    fun clearError() { messageError = null }

    fun loadDomains() {
        scope.launch {
            loading = true
            clearError()
            runCatching { api.domains(store.apiKey().ifBlank { null }) }
                .onSuccess { domains = it }
                .onFailure(::setError)
            loading = false
        }
    }

    fun login(address: String, password: String) {
        if (!address.contains("@") || password.length < 6) {
            messageError = "Enter a valid email and a password of at least 6 characters."
            return
        }
        scope.launch {
            loading = true
            clearError()
            runCatching {
                val t = api.token(address.trim(), password)
                val id = t.optString("id")
                val m = Mailbox(id, address.trim(), password, t.getString("token"))
                val old = accounts.indexOfFirst { it.address.equals(m.address, true) }
                accounts = if (old >= 0) accounts.toMutableList().also { it[old] = m } else accounts + m
                selectedIndex = accounts.indexOfFirst { it.address.equals(m.address, true) }
                persist()
                api.messages(m.token)
            }.onSuccess {
                messages = it
                lastRefresh = clock()
                restartPolling()
            }.onFailure(::setError)
            loading = false
        }
    }

    fun create(domain: String, expiry: Int?) {
        scope.launch {
            loading = true
            clearError()
            runCatching {
                val address = "${randomUser()}@$domain"
                val password = randomPassword()
                val created = api.create(address, password, expiry)
                val t = api.token(address, password)
                val m = Mailbox(
                    created.optString("id").ifBlank { t.optString("id") },
                    address, password, t.getString("token")
                )
                accounts += m
                selectedIndex = accounts.lastIndex
                persist()
                api.messages(m.token)
            }.onSuccess {
                messages = it
                lastRefresh = clock()
                restartPolling()
            }.onFailure(::setError)
            loading = false
        }
    }

    fun select(index: Int) {
        if (index !in accounts.indices) return
        selectedIndex = index
        persist()
        detail = null
        query = ""
        refresh()
        restartPolling()
    }

    fun refresh(silent: Boolean = false) {
        val a = active ?: return
        scope.launch {
            if (!silent) loading = true
            runCatching { api.messages(a.token) }
                .onSuccess {
                    val oldUnread = lastUnread
                    val newUnread = it.count { m -> !m.seen }
                    messages = it
                    lastUnread = newUnread
                    lastRefresh = clock()
                    if (silent && newUnread > oldUnread) {
                        // The UI surfaces the unread badge; no notification permission is required.
                    }
                }
                .onFailure {
                    messageError = errorMessage(it)
                }
            if (!silent) loading = false
        }
    }

    fun openMessage(m: MailSummary) {
        val a = active ?: return
        scope.launch {
            loading = true
            clearError()
            runCatching {
                api.markRead(a.token, m.id)
                api.detail(a.token, m.id)
            }.onSuccess {
                detail = it
                messages = messages.map { x -> if (x.id == m.id) x.copy(seen = true) else x }
            }.onFailure(::setError)
            loading = false
        }
    }

    fun deleteMessage(id: String) {
        val a = active ?: return
        scope.launch {
            runCatching { api.deleteMessage(a.token, id) }
                .onSuccess {
                    messages = messages.filterNot { it.id == id }
                    detail = null
                }.onFailure(::setError)
        }
    }

    fun deleteMailbox(index: Int) {
        if (index !in accounts.indices) return
        val a = accounts[index]
        scope.launch {
            loading = true
            runCatching { api.deleteAccount(a.token, a.id) }
                .onFailure(::setError)
                .onSuccess {
                    accounts = accounts.toMutableList().also { it.removeAt(index) }
                    selectedIndex = selectedIndex.coerceIn(0, (accounts.size - 1).coerceAtLeast(0))
                    persist()
                    messages = emptyList()
                    detail = null
                    if (active == null) loadDomains() else refresh()
                }
            loading = false
        }
    }

    fun removeLocal(index: Int) {
        if (index !in accounts.indices) return
        accounts = accounts.toMutableList().also { it.removeAt(index) }
        selectedIndex = selectedIndex.coerceIn(0, (accounts.size - 1).coerceAtLeast(0))
        persist()
        messages = emptyList()
        detail = null
        if (active == null) loadDomains() else {
            refresh()
            restartPolling()
        }
    }

    fun saveApiKey(value: String) {
        apiKey = value.trim()
        store.saveApiKey(apiKey)
        loadDomains()
    }

    fun setAuto(v: Boolean) {
        autoRefresh = v
        store.saveAuto(v)
        restartPolling()
    }

    fun setInterval(v: Int) {
        interval = v
        store.saveInterval(v)
        restartPolling()
    }

    fun restartPolling() {
        refreshJob?.cancel()
        if (!autoRefresh || active == null) return
        refreshJob = scope.launch {
            while (true) {
                delay(interval * 1000L)
                if (!loading && active != null) refresh(true)
            }
        }
    }

    fun stop() { refreshJob?.cancel() }

    private fun persist() {
        store.save(accounts)
        store.saveSelected(selectedIndex)
    }

    private fun randomUser(): String =
        "duck" + (1..8).map { ('a'..'z').random() }.joinToString("")

    private fun randomPassword(): String =
        (1..18).map { "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789".random() }.joinToString("")

    private fun clock(): String =
        DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault()).format(Instant.now())
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DuckMailApp() }
    }
}

@Composable
private fun DuckMailApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = remember { AppState(context.applicationContext, scope) }

    DisposableEffect(Unit) {
        state.init()
        onDispose { state.stop() }
    }

    DuckTheme {
        DuckRoot(state)
    }
}

@Composable
private fun DuckTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context),
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DuckRoot(state: AppState) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showAccounts by rememberSaveable { mutableStateOf(false) }
    val snack = remember { SnackbarHostState() }

    LaunchedEffect(state.messageError) {
        state.messageError?.let {
            snack.showSnackbar(it)
            state.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            if (state.detail != null) {
                CenterAlignedTopAppBar(
                    title = { Text("Message", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton({ state.detail = null }) {
                            Icon(Icons.Default.ArrowBack, "Back")
                        }
                    },
                    actions = {
                        IconButton({ state.deleteMessage(state.detail!!.id) }) {
                            Icon(Icons.Default.Delete, "Delete")
                        }
                    }
                )
            } else if (state.active != null) {
                CenterAlignedTopAppBar(
                    title = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("DuckMail", fontWeight = FontWeight.Bold)
                            Text(
                                state.active!!.address,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton({ showAccounts = true }) {
                            Icon(Icons.Default.MoreVert, "Mailboxes")
                        }
                    },
                    actions = {
                        IconButton({ state.refresh() }) {
                            Icon(Icons.Default.Refresh, "Refresh")
                        }
                    }
                )
            } else {
                CenterAlignedTopAppBar(
                    title = { Text("DuckMail", fontWeight = FontWeight.Bold) }
                )
            }
        },
        bottomBar = {
            if (state.detail == null && state.active != null) {
                NavigationBar(windowInsets = WindowInsets.navigationBars) {
                    val unread = state.messages.count { !it.seen }
                    NavigationBarItem(
                        selected = tab == 0,
                        onClick = { tab = 0 },
                        icon = {
                            BadgedBox(
                                badge = { if (unread > 0) Badge { Text(unread.coerceAtMost(99).toString()) } }
                            ) {
                                Icon(Icons.Default.Inbox, null)
                            }
                        },
                        label = { Text("Inbox") }
                    )
                    NavigationBarItem(
                        selected = tab == 1,
                        onClick = { tab = 1 },
                        icon = { Icon(Icons.Default.Add, null) },
                        label = { Text("New") }
                    )
                    NavigationBarItem(
                        selected = tab == 2,
                        onClick = { tab = 2 },
                        icon = { Icon(Icons.Default.Settings, null) },
                        label = { Text("Settings") }
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            AnimatedContent(
                targetState = state.detail,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "screen"
            ) { detail ->
                when {
                    detail != null -> MessageScreen(detail)
                    state.active == null -> WelcomeScreen(state)
                    tab == 0 -> InboxScreen(state)
                    tab == 1 -> NewMailboxScreen(state)
                    else -> SettingsScreen(state)
                }
            }

            if (state.loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
            }
        }
    }

    if (showAccounts) {
        AccountSwitcher(
            state = state,
            onDismiss = { showAccounts = false }
        )
    }
}

@Composable
private fun WelcomeScreen(state: AppState) {
    var address by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var visible by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Spacer(Modifier.height(26.dp))
            Surface(
                modifier = Modifier.size(100.dp),
                shape = RoundedCornerShape(32.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Email, null, Modifier.size(52.dp))
                }
            }
            Spacer(Modifier.height(18.dp))
            Text("DuckMail", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black)
            Text(
                "Your temporary inboxes, organized.",
                style = MaterialTheme.typography.bodyLarge
            )
        }

        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp)
            ) {
                Column(
                    Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Use an existing mailbox", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text("Email address") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton({ visible = !visible }) {
                                Icon(
                                    if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    null
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = { state.login(address, password) },
                        enabled = address.contains("@") && password.length >= 6,
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text("Add mailbox")
                    }
                }
            }
        }

        item {
            OutlinedButton(
                onClick = { state.loadDomains() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Create a new mailbox")
            }
        }

        if (state.domains.isNotEmpty()) {
            item { QuickCreateCard(state) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickCreateCard(state: AppState) {
    var domain by remember { mutableStateOf(state.domains.first().domain) }
    var expiryLabel by rememberSaveable { mutableStateOf("24 hours") }
    var domainOpen by remember { mutableStateOf(false) }
    var expiryOpen by remember { mutableStateOf(false) }

    ElevatedCard(Modifier.fillMaxWidth(), RoundedCornerShape(28.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Quick create", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

            ExposedDropdownMenuBox(domainOpen, { domainOpen = !domainOpen }) {
                OutlinedTextField(
                    value = domain,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Domain") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(domainOpen) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(domainOpen, { domainOpen = false }) {
                    state.domains.forEach { d ->
                        DropdownMenuItem(
                            text = { Text(d.domain) },
                            onClick = { domain = d.domain; domainOpen = false }
                        )
                    }
                }
            }

            ExposedDropdownMenuBox(expiryOpen, { expiryOpen = !expiryOpen }) {
                OutlinedTextField(
                    value = expiryLabel,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Lifetime") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expiryOpen) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(expiryOpen, { expiryOpen = false }) {
                    listOf(
                        "24 hours" to 86400,
                        "3 days" to 259200,
                        "7 days" to 604800,
                        "Never" to 0
                    ).forEach { (label, _) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = { expiryLabel = label; expiryOpen = false }
                        )
                    }
                }
            }

            Button(
                onClick = {
                    val expiry = when (expiryLabel) {
                        "3 days" -> 259200
                        "7 days" -> 604800
                        "Never" -> 0
                        else -> 86400
                    }
                    state.create(domain, expiry)
                },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text("Generate mailbox")
            }
        }
    }
}

@Composable
private fun InboxScreen(state: AppState) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Inbox", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                val unread = state.messages.count { !it.seen }
                Text(
                    "${state.messages.size} messages • $unread unread" +
                        if (state.lastRefresh.isNotBlank()) " • ${state.lastRefresh}" else "",
                    style = MaterialTheme.typography.labelMedium
                )
            }
            FilledTonalButton({ state.refresh() }) {
                Icon(Icons.Default.Refresh, null)
                Spacer(Modifier.width(6.dp))
                Text("Refresh")
            }
        }

        OutlinedTextField(
            value = state.query,
            onValueChange = { state.query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, null) },
            placeholder = { Text("Search inbox") },
            shape = RoundedCornerShape(18.dp)
        )

        if (state.filtered.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Inbox, null, Modifier.size(60.dp))
                    Spacer(Modifier.height(10.dp))
                    Text(
                        if (state.messages.isEmpty()) "No messages yet" else "Nothing found",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text("Auto-refresh is ${if (state.autoRefresh) "on" else "off"}")
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(state.filtered, key = { it.id }) { mail ->
                    MailCard(mail) { state.openMessage(mail) }
                }
            }
        }
    }
}

@Composable
private fun MailCard(mail: MailSummary, onClick: () -> Unit) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (!mail.seen)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
            else MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                Modifier.size(50.dp),
                RoundedCornerShape(17.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        initials(mail.senderAddress),
                        fontWeight = FontWeight.Black
                    )
                }
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        mail.senderName.ifBlank { mail.senderAddress.ifBlank { "Unknown sender" } },
                        fontWeight = if (!mail.seen) FontWeight.Black else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (mail.attachments) Icon(Icons.Default.AttachFile, "Attachments", Modifier.size(17.dp))
                }
                if (mail.senderName.isNotBlank()) {
                    Text(mail.senderAddress, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    mail.subject,
                    fontWeight = if (!mail.seen) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(formatDate(mail.createdAt), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun NewMailboxScreen(state: AppState) {
    LazyColumn(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("New mailbox", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            Text("Create another inbox. All mailboxes stay saved and can be switched instantly.")
        }
        if (state.domains.isEmpty()) {
            item {
                Button({ state.loadDomains() }, Modifier.fillMaxWidth()) { Text("Load domains") }
            }
        } else {
            items(state.domains) { d ->
                ElevatedCard(
                    onClick = { state.create(d.domain, 86400) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(Modifier.size(44.dp), CircleShape, MaterialTheme.colorScheme.primaryContainer) {
                            Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Email, null) }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(d.domain, fontWeight = FontWeight.Bold)
                            Text("24-hour mailbox", style = MaterialTheme.typography.labelMedium)
                        }
                        Icon(Icons.Default.Add, null)
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountSwitcher(state: AppState, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Your mailboxes", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.accounts.forEachIndexed { index, account ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable {
                            state.select(index)
                            onDismiss()
                        },
                        color = if (index == state.selectedIndex)
                            MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Email, null)
                            Spacer(Modifier.width(10.dp))
                            Text(account.address, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (index == state.selectedIndex) Icon(Icons.Default.Check, null)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun SettingsScreen(state: AppState) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var intervalOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
        }

        item {
            ElevatedCard(Modifier.fillMaxWidth(), RoundedCornerShape(26.dp)) {
                Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Active mailbox", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(state.active?.address.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton({
                            clipboard.setText(AnnotatedString(state.active?.address.orEmpty()))
                            toast(context, "Email copied")
                        }) {
                            Icon(Icons.Default.ContentCopy, null)
                            Spacer(Modifier.width(5.dp))
                            Text("Copy email")
                        }
                        OutlinedButton({
                            clipboard.setText(AnnotatedString(state.active?.password.orEmpty()))
                            toast(context, "Password copied")
                        }) {
                            Icon(Icons.Default.Key, null)
                            Spacer(Modifier.width(5.dp))
                            Text("Copy password")
                        }
                    }
                }
            }
        }

        item {
            ElevatedCard(Modifier.fillMaxWidth(), RoundedCornerShape(26.dp)) {
                Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Auto-refresh", fontWeight = FontWeight.Bold)
                            Text("Only the active mailbox is polled.", style = MaterialTheme.typography.labelMedium)
                        }
                        Switch(state.autoRefresh, state::setAuto)
                    }
                    if (state.autoRefresh) {
                        Box {
                            OutlinedButton({ intervalOpen = true }) {
                                Text("Every ${state.interval} seconds")
                                Icon(Icons.Default.ArrowDropDown, null)
                            }
                            DropdownMenu(intervalOpen, { intervalOpen = false }) {
                                listOf(10, 15, 30, 60).forEach {
                                    DropdownMenuItem(
                                        text = { Text("Every $it seconds") },
                                        onClick = { state.setInterval(it); intervalOpen = false }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            ElevatedCard(Modifier.fillMaxWidth(), RoundedCornerShape(26.dp)) {
                Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("Private domains", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Optional DuckMail API key. It starts with dk_.", style = MaterialTheme.typography.labelMedium)
                    OutlinedTextField(
                        value = state.apiKey,
                        onValueChange = { state.apiKey = it },
                        label = { Text("API key") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Key, null) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = { state.saveApiKey(state.apiKey) },
                        enabled = state.apiKey.isBlank() || state.apiKey.startsWith("dk_"),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save API key & reload domains")
                    }
                }
            }
        }

        item {
            Text("Danger zone", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            OutlinedButton(
                onClick = { confirmRemove = true },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.active != null
            ) { Text("Remove mailbox from this app") }
            OutlinedButton(
                onClick = { confirmDelete = true },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.active != null
            ) { Text("Delete mailbox from DuckMail") }
        }

        item {
            Text("DuckMail API • HTTPS", style = MaterialTheme.typography.labelSmall)
            Text(
                "Credentials are kept locally on this device. The app uses DuckMail's official API endpoints.",
                style = MaterialTheme.typography.labelSmall
            )
        }
    }

    if (confirmRemove) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Remove mailbox?") },
            text = { Text("This only removes the saved account from the app. The remote mailbox is not deleted.") },
            confirmButton = {
                TextButton({
                    state.removeLocal(state.selectedIndex)
                    confirmRemove = false
                }) { Text("Remove") }
            },
            dismissButton = { TextButton({ confirmRemove = false }) { Text("Cancel") } }
        )
    }

    if (confirmDelete) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete mailbox?") },
            text = { Text("This permanently deletes the currently logged-in mailbox from DuckMail.") },
            confirmButton = {
                TextButton({
                    state.deleteMailbox(state.selectedIndex)
                    confirmDelete = false
                }) { Text("Delete") }
            },
            dismissButton = { TextButton({ confirmDelete = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun MessageScreen(message: MailDetail) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val body = message.text.ifBlank {
        Html.fromHtml(message.html, Html.FROM_HTML_MODE_LEGACY).toString()
    }.trim()

    LazyColumn(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        item {
            Text(message.subject, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            if (message.senderName.isNotBlank()) Text(message.senderName, fontWeight = FontWeight.Bold)
            Text(message.senderAddress, style = MaterialTheme.typography.bodyMedium)
            if (message.to.isNotBlank()) Text("To: ${message.to}", style = MaterialTheme.typography.labelMedium)
            Text(formatDate(message.createdAt), style = MaterialTheme.typography.labelSmall)
        }

        item {
            ElevatedCard(Modifier.fillMaxWidth(), RoundedCornerShape(26.dp)) {
                SelectionContainer {
                    Text(
                        body.ifBlank { "(Empty message)" },
                        Modifier.padding(19.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }

        if (message.attachments.isNotEmpty()) {
            item {
                Text("Attachments", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            items(message.attachments) { a ->
                ElevatedCard(
                    onClick = {
                        if (a.url.isNotBlank()) {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BASE_URL + a.url)))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AttachFile, null)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(a.filename, fontWeight = FontWeight.Bold)
                            Text(
                                "${a.contentType.ifBlank { "file" }} • ${formatBytes(a.size)}",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }

        item {
            OutlinedButton(
                onClick = {
                    clipboard.setText(AnnotatedString(body))
                    toast(context, "Message copied")
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.ContentCopy, null)
                Spacer(Modifier.width(7.dp))
                Text("Copy message")
            }
        }
    }
}

private fun initials(email: String): String =
    email.substringBefore("@").take(2).uppercase(Locale.ROOT).ifBlank { "DM" }

private fun formatDate(value: String): String = runCatching {
    DateTimeFormatter.ofPattern("dd MMM • HH:mm", Locale.getDefault())
        .withZone(ZoneId.systemDefault()).format(Instant.parse(value))
}.getOrDefault(value)

private fun formatBytes(bytes: Int): String =
    when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> String.format(Locale.getDefault(), "%.1f MB", bytes / 1024f / 1024f)
    }

private fun toast(context: Context, text: String) =
    Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
