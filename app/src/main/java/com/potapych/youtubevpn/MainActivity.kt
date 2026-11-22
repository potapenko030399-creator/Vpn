package com.potapych.youtubevpn

import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wireguard.android.backend.Backend
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

private const val YOUTUBE_PACKAGE = "com.google.android.youtube"

class MainActivity : ComponentActivity() {
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var backend: Backend
    private val tunnel = AppTunnel()
    private var loadedConfig: Config? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        backend = GoBackend(this)
        val saved = File(filesDir, "youtube-only.conf")
        if (saved.exists()) runCatching { loadConfig(saved.readText()) }
        setContent {
            var connected by remember { mutableStateOf(false) }
            var hasConfig by remember { mutableStateOf(loadedConfig != null) }
            var status by remember { mutableStateOf(if (hasConfig) "Конфигурация загружена" else "Нужен файл сервера WireGuard") }
            var busy by remember { mutableStateOf(false) }

            val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri != null) {
                    runCatching {
                        contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                            ?: error("Не удалось прочитать файл")
                    }.onSuccess { raw ->
                        runCatching { loadConfig(raw) }
                            .onSuccess {
                                File(filesDir, "youtube-only.conf").writeText(makeYouTubeOnly(raw))
                                hasConfig = true
                                status = "Серверная конфигурация сохранена"
                            }
                            .onFailure { status = "Ошибка конфигурации: ${it.message ?: "проверьте файл"}" }
                    }.onFailure { status = "Не удалось открыть файл" }
                }
            }

            val vpnPermission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    busy = true
                    executor.execute {
                        runCatching {
                            val cfg = loadConfig(File(filesDir, "youtube-only.conf").readText())
                            backend.setState(tunnel, Tunnel.State.UP, cfg)
                        }.onSuccess {
                            runOnUiThread { connected = true; busy = false; status = "Защищённое соединение активно" }
                        }.onFailure {
                            runOnUiThread { busy = false; status = "Не удалось подключиться: ${it.message ?: "ошибка сервера"}" }
                        }
                    }
                } else status = "Разрешение VPN не выдано"
            }

            MaterialTheme(colorScheme = darkColorScheme(
                primary = Color(0xFFFF3030), background = Color(0xFF080A10), surface = Color(0xFF151923)
            )) {
                Surface(Modifier.fillMaxSize(), color = Color(0xFF080A10)) {
                    Column(
                        modifier = Modifier.fillMaxSize()
                            .background(Brush.verticalGradient(listOf(Color(0xFF171D2A), Color(0xFF080A10))))
                            .padding(horizontal = 24.dp, vertical = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(46.dp).background(Color(0xFFFF2525), CircleShape), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(34.dp))
                                }
                                Spacer(Modifier.width(12.dp))
                                Text("YouTube VPN", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Black)
                            }
                            Spacer(Modifier.height(18.dp))
                            Text("ТОЛЬКО ДЛЯ YOUTUBE", color = Color(0xFFFF5252), fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier.size(220.dp).background(
                                    Brush.radialGradient(listOf(Color(0x66FF2525), Color.Transparent)), CircleShape
                                ), contentAlignment = Alignment.Center
                            ) {
                                Button(
                                    onClick = {
                                        if (connected) {
                                            busy = true
                                            executor.execute {
                                                runCatching { backend.setState(tunnel, Tunnel.State.DOWN, null) }
                                                runOnUiThread { connected = false; busy = false; status = "VPN отключён" }
                                            }
                                        } else if (!hasConfig) filePicker.launch(arrayOf("*/*"))
                                        else {
                                            val intent = VpnService.prepare(this@MainActivity)
                                            if (intent != null) vpnPermission.launch(intent)
                                            else {
                                                busy = true
                                                executor.execute {
                                                    runCatching {
                                                        backend.setState(tunnel, Tunnel.State.UP, loadConfig(File(filesDir, "youtube-only.conf").readText()))
                                                    }.onSuccess {
                                                        runOnUiThread { connected = true; busy = false; status = "Защищённое соединение активно" }
                                                    }.onFailure {
                                                        runOnUiThread { busy = false; status = "Ошибка подключения: ${it.message}" }
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    enabled = !busy,
                                    modifier = Modifier.size(178.dp),
                                    shape = CircleShape,
                                    colors = ButtonDefaults.buttonColors(containerColor = if (connected) Color(0xFF167A50) else Color(0xFFFF2525))
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(Icons.Default.PowerSettingsNew, null, modifier = Modifier.size(52.dp), tint = Color.White)
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            if (busy) "ПОДОЖДИТЕ" else if (connected) "ОТКЛЮЧИТЬ" else if (hasConfig) "ПОДКЛЮЧИТЬ" else "ДОБАВИТЬ\nСЕРВЕР",
                                            textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, color = Color.White
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(24.dp))
                            Text(if (connected) "ПОДКЛЮЧЕНО" else "НЕ ПОДКЛЮЧЕНО", color = if (connected) Color(0xFF42D392) else Color(0xFFB6BBC7), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            Text(status, color = Color(0xFFB6BBC7), fontSize = 13.sp, textAlign = TextAlign.Center)
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            InfoRow("YouTube проходит через VPN")
                            InfoRow("Остальные приложения — напрямую")
                            InfoRow("Шифрование WireGuard")
                            OutlinedButton(
                                onClick = { filePicker.launch(arrayOf("*/*")) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                            ) { Text(if (hasConfig) "ЗАМЕНИТЬ КОНФИГУРАЦИЮ СЕРВЕРА" else "ЗАГРУЗИТЬ ФАЙЛ СЕРВЕРА (.CONF)") }
                            Text("Нужен личный файл WireGuard .conf от VPN-сервера.", color = Color(0xFF7F8797), fontSize = 11.sp, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }

    private fun makeYouTubeOnly(raw: String): String {
        val lines = raw.lines().filterNot {
            it.trim().startsWith("IncludedApplications", true) || it.trim().startsWith("ExcludedApplications", true)
        }.toMutableList()
        val interfaceIndex = lines.indexOfFirst { it.trim().equals("[Interface]", true) }
        require(interfaceIndex >= 0) { "В файле нет секции [Interface]" }
        val peerIndex = lines.indexOfFirst { it.trim().equals("[Peer]", true) }
        val insertAt = if (peerIndex > interfaceIndex) peerIndex else lines.size
        lines.add(insertAt, "IncludedApplications = $YOUTUBE_PACKAGE")
        return lines.joinToString("\n")
    }

    private fun loadConfig(raw: String): Config {
        val onlyYoutube = makeYouTubeOnly(raw)
        val cfg = Config.parse(ByteArrayInputStream(onlyYoutube.toByteArray(StandardCharsets.UTF_8)))
        require(cfg.getInterface().getIncludedApplications().contains(YOUTUBE_PACKAGE)) {
            "Не удалось ограничить VPN приложением YouTube"
        }
        loadedConfig = cfg
        return cfg
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }
}

private class AppTunnel : Tunnel {
    override fun getName(): String = "YouTube VPN"
    override fun onStateChange(newState: Tunnel.State) = Unit
}

@Composable
private fun InfoRow(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().background(Color(0xFF151923), RoundedCornerShape(14.dp)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Security, null, tint = Color(0xFF42D392), modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text("✓", color = Color(0xFF42D392), fontWeight = FontWeight.Bold, fontSize = 18.sp)
    }
}
