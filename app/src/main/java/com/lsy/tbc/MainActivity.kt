package com.lsy.tbc

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import java.io.File
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lsy.tbc.ui.theme.TBCTheme
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

// 全局配置，允许忽略未知字段
private val jsonParser = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    encodeDefaults = true
}

val client = HttpClient(CIO) {
    install(ContentNegotiation) {
        json(jsonParser)
    }
    install(HttpTimeout) {
        requestTimeoutMillis = 120_000
        connectTimeoutMillis = 60_000
        socketTimeoutMillis = 120_000
    }
}

@Serializable
data class AppSettings(
    val serverUrl: String = "http://192.168.3.91:8000/",
    val apiKey: String = "adc8dd75497ef8de975c3f50b8bf9627a62fa6ba14f88d58",
    val selectedDeviceId: String = "auto"
)

@Serializable
data class ModelData(val id: String)

@Serializable
data class ModelListResponse(val data: List<ModelData>)

fun saveSettings(context: Context, settings: AppSettings) {
    try {
        val file = File(context.filesDir, "settings.json")
        val json = jsonParser.encodeToString(AppSettings.serializer(), settings)
        file.writeText(json)
        System.out.println("TBC_PRINT: 写入文件成功: " + file.absolutePath)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun loadSettings(context: Context): AppSettings {
    val file = File(context.filesDir, "settings.json")
    if (!file.exists()) return AppSettings()
    return try {
        jsonParser.decodeFromString(AppSettings.serializer(), file.readText())
    } catch (e: Exception) {
        AppSettings()
    }
}

@Serializable
data class ChatMessageData(
    val role: String,
    val content: String
)

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessageData>,
    val stream: Boolean = true
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        System.out.println("TBC_PRINT: MainActivity onCreate 启动")
        enableEdgeToEdge()
        setContent {
            TBCTheme {
                TBCApp()
            }
        }
    }
}

@Composable
fun TBCApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("tbc_prefs", Context.MODE_PRIVATE) }
    
    var isSettingsOpen by rememberSaveable { mutableStateOf(false) }
    
    // 初始化读取逻辑
    var serverUrl by remember { mutableStateOf(prefs.getString("url", "http://192.168.3.91:8000/") ?: "http://192.168.3.91:8000/") }
    var apiKey by remember { mutableStateOf(prefs.getString("key", "adc8dd75497ef8de975c3f50b8bf9627a62fa6ba14f88d58") ?: "adc8dd75497ef8de975c3f50b8bf9627a62fa6ba14f88d58") }
    var selectedDeviceId by remember { mutableStateOf(prefs.getString("dev", "auto") ?: "auto") }

    val messages = remember {
        mutableStateListOf(
            ChatMessage("您好！我是您的 TVBox 智能管家。现已支持多意图链式调度与任务规划！", false)
        )
    }

    if (isSettingsOpen) {
        SettingsScreen(
            currentUrl = serverUrl,
            currentApiKey = apiKey,
            onBack = { isSettingsOpen = false },
            onSave = { newUrl, newApiKey ->
                serverUrl = newUrl
                apiKey = newApiKey
                prefs.edit().putString("url", newUrl).putString("key", newApiKey).commit()
                Toast.makeText(context, "✅ 已保存", Toast.LENGTH_SHORT).show()
                isSettingsOpen = false
            }
        )
    } else {
        ChatScreen(
            serverUrl = serverUrl,
            apiKey = apiKey,
            selectedDeviceId = selectedDeviceId,
            messages = messages,
            onClearMessages = {
                messages.clear()
                messages.add(ChatMessage("您好！我是您的 TVBox 智能管家。现已支持多意图链式调度与任务规划！", false))
            },
            onDeviceSelected = { id ->
                selectedDeviceId = id
                prefs.edit().putString("dev", id).commit()
            },
            onOpenSettings = { isSettingsOpen = true }
        )
    }
}

data class ChatMessage(
    val content: String,
    val isUser: Boolean
)

@Composable
fun ChatScreen(
    serverUrl: String,
    apiKey: String,
    selectedDeviceId: String,
    messages: MutableList<ChatMessage>,
    onClearMessages: () -> Unit,
    onDeviceSelected: (String) -> Unit,
    onOpenSettings: () -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var showClearDialog by remember { mutableStateOf(false) }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("清除历史记录", color = Color.White) },
            text = { Text("确定要清除所有聊天记录吗？", color = Color(0xFF94A3B8)) },
            confirmButton = {
                TextButton(onClick = {
                    onClearMessages()
                    showClearDialog = false
                }) {
                    Text("确定", color = Color(0xFFEF4444))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("取消", color = Color.White)
                }
            },
            containerColor = Color(0xFF1E293B)
        )
    }

    // 强力滚动跟随：监听消息数量和最后一条消息的长度变化
    LaunchedEffect(messages.size, messages.lastOrNull()?.content?.length) {
        if (messages.isNotEmpty()) {
            // 使用无动画的滚动以实现流式输出时的“死死跟随”
            listState.scrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090D16))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "TVBox AI 智能管家", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(text = "实时控制您的电视终端", color = Color(0xFF94A3B8), fontSize = 12.sp)
            }
            Row {
                IconButton(onClick = { showClearDialog = true }) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Clear History", tint = Color(0xFF94A3B8))
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
                }
            }
        }

        DeviceSelector(
            serverUrl = serverUrl,
            apiKey = apiKey,
            selectedDeviceId = selectedDeviceId,
            onDeviceSelected = onDeviceSelected
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Chat List
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp) // 增加底部边距，防止被输入框遮挡
        ) {
            items(messages) { message -> ChatBubble(message) }
        }

        // Input Area Container
        Surface(
            modifier = Modifier.fillMaxWidth().imePadding(), // 键盘抬起时带动整个输入区
            color = Color(0xFF0B111E),
            tonalElevation = 8.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("输入控制指令...", color = Color(0xFF64748B)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF0F172A),
                        unfocusedContainerColor = Color(0xFF0F172A),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Button(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            val userText = inputText
                            messages.add(ChatMessage(userText, true))
                            inputText = ""

                            scope.launch(Dispatchers.IO) {
                                try {
                                    val responsePlaceholder = ChatMessage("", false)
                                    messages.add(responsePlaceholder)
                                    val responseIndex = messages.size - 1

                                    val baseUrl = serverUrl.removeSuffix("/")
                                    val fullUrl = "$baseUrl/v1/chat/completions"

                                    client.preparePost(fullUrl) {
                                        header(HttpHeaders.Authorization, "Bearer $apiKey")
                                        contentType(ContentType.Application.Json)
                                        setBody(ChatRequest(model = selectedDeviceId, messages = listOf(ChatMessageData("user", userText))))
                                    }.execute { response ->
                                        if (response.status != HttpStatusCode.OK) {
                                            val err = response.bodyAsText()
                                            messages[responseIndex] = ChatMessage("错误: ${response.status}\n$err", false)
                                            return@execute
                                        }

                                        val channel: ByteReadChannel = response.bodyAsChannel()
                                        while (!channel.isClosedForRead) {
                                            val line = channel.readUTF8Line() ?: break
                                            if (line.startsWith("data: ")) {
                                                val data = line.substring(6).trim()
                                                if (data == "[DONE]") break
                                                
                                                try {
                                                    val root = jsonParser.parseToJsonElement(data).jsonObject
                                                    
                                                    // 1. 处理工具调用
                                                    root["tool_call"]?.jsonObject?.let { tool ->
                                                        val status = tool["status"]?.jsonPrimitive?.contentOrNull
                                                        val rawObs = tool["observation"]?.jsonPrimitive?.contentOrNull
                                                        // 解析并处理换行符
                                                        val obs = rawObs?.replace("\\n", "\n")?.replace("\\r", "")

                                                        if (status == "running") {
                                                            val name = tool["name"]?.jsonPrimitive?.contentOrNull
                                                            val msg = "\n[⏳ 正在执行: $name...]\n"
                                                            if (!messages[responseIndex].content.contains(msg)) {
                                                                messages[responseIndex] = messages[responseIndex].copy(content = messages[responseIndex].content + msg)
                                                            }
                                                        }
                                                        if (!obs.isNullOrEmpty()) {
                                                            val obsMsg = "\n$obs\n"
                                                            // 简单的去重逻辑，取前20位特征值
                                                            val fingerprint = obs.take(20)
                                                            if (!messages[responseIndex].content.contains(fingerprint)) {
                                                                messages[responseIndex] = messages[responseIndex].copy(content = messages[responseIndex].content + obsMsg)
                                                            }
                                                        }
                                                    }

                                                    // 2. 处理回复内容
                                                    root["choices"]?.jsonArray?.getOrNull(0)?.jsonObject?.let { choice ->
                                                        val rawContent = choice["delta"]?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull
                                                        val content = rawContent?.replace("\\n", "\n")
                                                        if (!content.isNullOrEmpty()) {
                                                            messages[responseIndex] = messages[responseIndex].copy(content = messages[responseIndex].content + content)
                                                        }
                                                    }
                                                } catch (e: Exception) {
                                                    e.printStackTrace()
                                                }
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    messages.add(ChatMessage("网络错误: ${e.message}", false))
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("发送") }
            }
        }
    }
}

@Composable
fun DeviceSelector(
    serverUrl: String,
    apiKey: String,
    selectedDeviceId: String,
    onDeviceSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var devices by remember { mutableStateOf(listOf("auto")) }
    var isLoading by remember { mutableStateOf(false) }

    // 每次展开时重新请求后端获取设备列表
    LaunchedEffect(expanded) {
        if (expanded) {
            isLoading = true
            try {
                val baseUrl = serverUrl.removeSuffix("/")
                val fullUrl = "$baseUrl/v1/models"
                val response = client.get(fullUrl) {
                    header(HttpHeaders.Authorization, "Bearer $apiKey")
                }
                if (response.status == HttpStatusCode.OK) {
                    val bodyText = response.bodyAsText()
                    val modelList = jsonParser.decodeFromString(ModelListResponse.serializer(), bodyText)
                    devices = listOf("auto") + modelList.data.map { it.id }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        }
    }

    Surface(
        modifier = Modifier.padding(horizontal = 20.dp).wrapContentWidth(),
        color = Color(0xFF0F172A),
        shape = RoundedCornerShape(30.dp),
        border = BorderStroke(1.dp, Color(0xFF3B82F6).copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp).clickable { expanded = true },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = "🎯 目标电视:", color = Color(0xFF94A3B8), fontSize = 12.sp)
            Text(
                text = if (selectedDeviceId == "auto") "🌟 自动路由" else selectedDeviceId,
                color = Color(0xFF38BDF8),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            if (isLoading && expanded) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    color = Color(0xFF38BDF8),
                    strokeWidth = 2.dp
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(Color(0xFF0F172A))
            ) {
                devices.forEach { id ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = if (id == "auto") "🌟 自动路由" else id,
                                color = if (id == selectedDeviceId) Color(0xFF38BDF8) else Color.White
                            )
                        },
                        onClick = { onDeviceSelected(id); expanded = false }
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(currentUrl: String, currentApiKey: String, onBack: () -> Unit, onSave: (String, String) -> Unit) {
    var urlText by remember { mutableStateOf(currentUrl) }
    var apiKeyText by remember { mutableStateOf(currentApiKey) }
    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF090D16)).statusBarsPadding().navigationBarsPadding()) {
        Row(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White) }
            Text(text = "设置", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Column(modifier = Modifier.padding(20.dp)) {
            Text(text = "服务器地址", color = Color(0xFF94A3B8))
            OutlinedTextField(value = urlText, onValueChange = { urlText = it }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White))
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "API Key", color = Color(0xFF94A3B8))
            OutlinedTextField(value = apiKeyText, onValueChange = { apiKeyText = it }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White))
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = { onSave(urlText, apiKeyText) }, modifier = Modifier.fillMaxWidth()) { Text("保存配置") }
        }
    }
}

@Composable
fun ChatBubble(message: ChatMessage) {
    val alignment = if (message.isUser) Alignment.CenterEnd else Alignment.CenterStart
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = alignment) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.widthIn(max = 300.dp),
            color = if (message.isUser) Color(0xFF2563EB) else Color(0xFF1E293B)
        ) {
            SelectionContainer {
                Text(text = message.content, color = Color.White, modifier = Modifier.padding(12.dp), fontSize = 14.sp)
            }
        }
    }
}
