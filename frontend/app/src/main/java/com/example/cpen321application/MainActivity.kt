package com.example.cpen321application

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.cpen321application.ui.theme.CPEN321ApplicationTheme
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CPEN321ApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    var showLiveUpdates by remember { mutableStateOf(false) }
                    if (showLiveUpdates) {
                        LivePixelScreen(
                            apiBaseUrl = BuildConfig.API_BASE_URL,
                            onBack = { showLiveUpdates = false },
                            modifier = Modifier.padding(innerPadding)
                        )
                    } else {
                        HomeScreen(
                            apiBaseUrl = BuildConfig.API_BASE_URL,
                            onOpenLiveUpdates = { showLiveUpdates = true },
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    apiBaseUrl: String,
    onOpenLiveUpdates: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Greeting(apiBaseUrl = apiBaseUrl)
        Button(onClick = onOpenLiveUpdates) {
            Text("Button 2: Live Updates")
        }
    }
}

@Composable
fun Greeting(apiBaseUrl: String, modifier: Modifier = Modifier) {
    var statusText by remember { mutableStateOf("Checking backend at $apiBaseUrl/health...") }

    LaunchedEffect(apiBaseUrl) {
        statusText = fetchHealthStatus(apiBaseUrl)
    }

    Text(
        text = statusText,
        modifier = modifier
    )
}

@Composable
private fun LivePixelScreen(
    apiBaseUrl: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cells = remember {
        mutableStateListOf<Color>().apply {
            repeat(16 * 16) { add(Color.White) }
        }
    }
    var connectionText by remember { mutableStateOf("Connecting...") }
    val socketUrl = remember(apiBaseUrl) { toWebSocketUrl(apiBaseUrl) }
    val scope = rememberCoroutineScope()

    DisposableEffect(socketUrl) {
        val socketClient = PixelSocketClient(
            url = "$socketUrl/ws/pixels",
            onStatus = { status -> scope.launch { connectionText = status } },
            onPixel = { x, y, color ->
                scope.launch {
                    cells[y * 16 + x] = color
                }
            }
        )
        val socket = socketClient.connect()

        onDispose {
            socket?.close(1000, "Screen closed")
            socketClient.shutdown()
        }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onBack) { Text("Back") }
            Spacer(Modifier.weight(1f))
            Text(connectionText)
        }
        Text("Live pixel updates")
        PixelGrid(cells = cells, modifier = Modifier.fillMaxWidth().aspectRatio(1f))
    }
}

@Composable
private fun PixelGrid(cells: List<Color>, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.background(Color.Black)) {
        val cellWidth = size.width / 16f
        val cellHeight = size.height / 16f
        cells.forEachIndexed { index, color ->
            val x = index % 16
            val y = index / 16
            drawRect(
                color = color,
                topLeft = Offset(x * cellWidth, y * cellHeight),
                size = androidx.compose.ui.geometry.Size(cellWidth, cellHeight)
            )
            drawRect(
                color = Color.Black.copy(alpha = 0.25f),
                topLeft = Offset(x * cellWidth, y * cellHeight),
                size = androidx.compose.ui.geometry.Size(cellWidth, cellHeight),
                style = Stroke(width = 1f)
            )
        }
    }
}

private class PixelSocketClient(
    private val url: String,
    private val onStatus: (String) -> Unit,
    private val onPixel: (Int, Int, Color) -> Unit
) {
    private val client = OkHttpClient()
    private var socket: WebSocket? = null

    fun connect(): WebSocket? {
        return try {
            val request = Request.Builder().url(url).build()
            socket = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    onStatus("Connected")
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    try {
                        val update = JSONObject(text)
                        val x = update.getInt("x")
                        val y = update.getInt("y")
                        if (x in 0 until 16 && y in 0 until 16) {
                            onPixel(x, y, Color(android.graphics.Color.parseColor(update.getString("color"))))
                        }
                    } catch (_: Exception) {
                        onStatus("Invalid pixel update")
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    onStatus("Connection failed")
                }
            })
            socket
        } catch (_: Exception) {
            onStatus("Invalid WebSocket URL")
            null
        }
    }

    fun shutdown() {
        socket?.cancel()
        client.dispatcher.executorService.shutdown()
    }
}

private fun toWebSocketUrl(apiBaseUrl: String): String = when {
    apiBaseUrl.startsWith("https://") -> apiBaseUrl.replaceFirst("https://", "wss://")
    apiBaseUrl.startsWith("http://") -> apiBaseUrl.replaceFirst("http://", "ws://")
    else -> apiBaseUrl
}.trimEnd('/')

private suspend fun fetchHealthStatus(apiBaseUrl: String): String = withContext(Dispatchers.IO) {
    val healthUrl = "${apiBaseUrl.trimEnd('/')}/health"
    try {
        val connection = (URL(healthUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 5_000
            readTimeout = 5_000
        }

        when (val code = connection.responseCode) {
            HttpURLConnection.HTTP_OK -> {
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                "Backend healthy ($healthUrl): $body"
            }
            else -> {
                val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() }
                "Backend error ($healthUrl): HTTP $code${errorBody?.let { " — $it" } ?: ""}"
            }
        }
    } catch (e: Exception) {
        "Backend unreachable ($healthUrl): ${e.message ?: e.javaClass.simpleName}"
    }
}