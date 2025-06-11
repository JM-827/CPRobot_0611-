package com.example.campuspatrolrobot

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.campuspatrolrobot.ui.theme.CampusPatrolRobotAppTheme
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class ChatActivity1 : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CampusPatrolRobotAppTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ChatScreen()
                }
            }
        }
    }
}

@Composable
fun ChatScreen(modifier: Modifier = Modifier) {
    var message by remember { mutableStateOf("") }
    val messages = remember { mutableStateListOf<Pair<String, String>>() } // (text, sender)
    val context = androidx.compose.ui.platform.LocalContext.current
    var showClearDialog by remember { mutableStateOf(false) }

    // Firestore에서 실시간 메시지 로드
    LaunchedEffect(Unit) {
        val db = Firebase.firestore
        db.collection("Messages")
            .orderBy("timestamp")
            .addSnapshotListener { snapshot, _ ->
                snapshot?.documents?.forEach { doc ->
                    val text = doc.getString("text") ?: ""
                    val sender = doc.getString("sender") ?: ""
                    if (!messages.any { it.first == text && it.second == sender }) {
                        messages.add(text to sender)
                    }
                }
            }
    }

    // 메시지 Firestore에 저장
    fun sendMessage(text: String) {
        val db = Firebase.firestore
        val messageData = hashMapOf(
            "text" to text,
            "sender" to "user",
            "timestamp" to com.google.firebase.Timestamp.now()
        )
        db.collection("Messages")
            .add(messageData)
            .addOnSuccessListener {
                Log.d("Chat", "메시지 Firestore에 저장 성공: $text")
            }
            .addOnFailureListener { e ->
                Log.e("Chat", "메시지 저장 실패: ${e.message}")
            }
    }

    // SharedPreferences에서 로그 불러오기 (옵션)
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("ChatLogs", Context.MODE_PRIVATE)
        val savedLogs = prefs.getStringSet("logs", emptySet())?.toList() ?: emptyList()
        savedLogs.forEach { log ->
            val parts = log.split(":", limit = 2)
            if (parts.size == 2) {
                messages.add(parts[1].trim() to parts[0].trim())
            }
        }
    }

    // 메시지 SharedPreferences에 저장
    fun saveMessages() {
        val prefs = context.getSharedPreferences("ChatLogs", Context.MODE_PRIVATE)
        val logs = messages.map { "${it.second}: ${it.first}" }.toSet()
        prefs.edit().putStringSet("logs", logs).apply()
    }

    // 로그 지우기
    fun clearMessages() {
        messages.clear()
        val prefs = context.getSharedPreferences("ChatLogs", Context.MODE_PRIVATE)
        prefs.edit().remove("logs").apply()
    }

    // 로그 지우기 다이얼로그
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("로그 지우기") },
            text = { Text("모든 채팅 로그를 삭제하시겠습니까?") },
            confirmButton = {
                Button(onClick = {
                    clearMessages()
                    showClearDialog = false
                }) {
                    Text("삭제")
                }
            },
            dismissButton = {
                Button(onClick = { showClearDialog = false }) {
                    Text("취소")
                }
            }
        )
    }

    // 채팅 UI 구성
    Column(modifier = modifier.padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "명령어 채팅",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Button(
                onClick = { showClearDialog = true },
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text("로그 지우기")
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        LazyColumn(
            modifier = Modifier.weight(1f).padding(bottom = 16.dp),
            reverseLayout = true
        ) {
            items(messages.size) { index ->
                val reversedIndex = messages.size - 1 - index
                val (text, sender) = messages[reversedIndex]
                MessageBubble(
                    text = text,
                    isUser = sender == "user",
                    showIcon = sender != "user"
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            CommandButton("귀환", messages) { saveMessages() }
            CommandButton("잠시 대기", messages) { saveMessages() }
            CommandButton("다시 작동시작", messages) { saveMessages() }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(24.dp)
                )
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = message,
                onValueChange = { message = it },
                modifier = Modifier
                    .weight(1f)
                    .background(Color.Transparent),
                placeholder = { Text("명령어 입력...", color = Color.Gray) },
                colors = TextFieldDefaults.colors(
                    unfocusedContainerColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    cursorColor = MaterialTheme.colorScheme.primary,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                )
            )
            IconButton(
                onClick = {
                    if (message.isNotBlank()) {
                        sendMessage(message) // Firestore에 저장
                        messages.add(message to "user")
                        saveMessages() // 로컬 저장 (옵션)
                        message = ""
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "전송",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun CommandButton(command: String, messages: MutableList<Pair<String, String>>, onSave: () -> Unit) {
    var scale by remember { mutableStateOf(1f) }
    Surface(
        modifier = Modifier
            .scale(scale)
            .clickable(
                onClick = {
                    scale = 0.95f
                    val text = command
                    messages.add(text to "user")
                    onSave()
                }
            )
            .animateContentSize(animationSpec = spring()),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondary,
        tonalElevation = 4.dp
    ) {
        Text(
            text = command,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            color = MaterialTheme.colorScheme.onSecondary,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun MessageBubble(text: String, isUser: Boolean, showIcon: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showIcon) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "동작 수행",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp
            ) {
                Text(
                    text = text,
                    modifier = Modifier.padding(12.dp),
                    color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}