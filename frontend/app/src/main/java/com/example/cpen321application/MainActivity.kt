package com.example.cpen321application

import android.media.MediaPlayer
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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
                    var showTimer by remember { mutableStateOf(false) }
                    if (showLiveUpdates) {
                        LivePixelScreen(
                            apiBaseUrl = BuildConfig.API_BASE_URL,
                            onBack = { showLiveUpdates = false },
                            modifier = Modifier.padding(innerPadding)
                        )
                    } else if (showTimer) {
                        TimerScreen(
                            onBack = { showTimer = false },
                            modifier = Modifier.padding(innerPadding)
                        )
                    } else {
                        HomeScreen(
                            apiBaseUrl = BuildConfig.API_BASE_URL,
                            onOpenLiveUpdates = { showLiveUpdates = true },
                            onOpenTimer = { showTimer = true },
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
    onOpenTimer: () -> Unit,
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
        Button(onClick = onOpenTimer) {
            Text("Button 3: Timer")
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
private fun TimerScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var minutesText by rememberSaveable { mutableStateOf("0") }
    var secondsText by rememberSaveable { mutableStateOf("0") }
    var remainingSeconds by rememberSaveable { mutableStateOf(0) }
    var isRunning by rememberSaveable { mutableStateOf(false) }
    var hasStarted by rememberSaveable { mutableStateOf(false) }
    var surpriseVisible by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.release()
        }
    }

    LaunchedEffect(isRunning) {
        while (isRunning && remainingSeconds > 0) {
            kotlinx.coroutines.delay(1_000)
            remainingSeconds -= 1
            if (remainingSeconds == 0) {
                isRunning = false
                surpriseVisible = true
            }
        }
    }

    LaunchedEffect(surpriseVisible) {
        if (surpriseVisible) {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer.create(context, R.raw.xbc_kazoo)
            mediaPlayer?.start()
        }
    }

    val displayedSeconds = if (hasStarted) remainingSeconds else {
        timerInputSeconds(minutesText, secondsText)
    }

    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onBack) { Text("Back") }
            Spacer(Modifier.weight(1f))
            Text("Button 3")
        }

        Text("Set a timer")
        Text(
            text = formatTimer(displayedSeconds),
            modifier = Modifier.padding(vertical = 20.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = minutesText,
                onValueChange = { value ->
                    if (!isRunning && value.all(Char::isDigit) && value.length <= 3) {
                        minutesText = value
                        hasStarted = false
                    }
                },
                label = { Text("Minutes") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = secondsText,
                onValueChange = { value ->
                    if (!isRunning && value.all(Char::isDigit) && value.length <= 2) {
                        secondsText = value
                        hasStarted = false
                    }
                },
                label = { Text("Seconds") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = {
                    if (remainingSeconds == 0 || !hasStarted) {
                        remainingSeconds = timerInputSeconds(minutesText, secondsText)
                        surpriseVisible = false
                    }
                    if (remainingSeconds > 0) {
                        hasStarted = true
                        isRunning = true
                    }
                },
                enabled = !isRunning
            ) {
                Text(if (hasStarted) "Resume" else "Start")
            }
            Button(
                onClick = { isRunning = false },
                enabled = isRunning
            ) {
                Text("Pause")
            }
            Button(onClick = {
                isRunning = false
                hasStarted = false
                remainingSeconds = 0
                surpriseVisible = false
                mediaPlayer?.stop()
                mediaPlayer?.release()
                mediaPlayer = null
            }) {
                Text("Reset")
            }
        }

        if (hasStarted && !isRunning && remainingSeconds == 0) {
            if (surpriseVisible) {
                Text("Surprise!")
                Image(
                    painter = painterResource(R.drawable.lebron_sunshine),
                    contentDescription = "LeBron James smiling in sunshine",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
            } else {
                Text("Timer finished")
            }
        }
    }
}

private fun timerInputSeconds(minutesText: String, secondsText: String): Int {
    val minutes = minutesText.toIntOrNull() ?: 0
    val seconds = secondsText.toIntOrNull() ?: 0
    return (minutes * 60 + seconds.coerceIn(0, 59)).coerceAtLeast(0)
}

private fun formatTimer(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
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