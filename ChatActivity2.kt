package com.example.campuspatrolrobot

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log // Log import 추가
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.campuspatrolrobot.ui.theme.CampusPatrolRobotAppTheme
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat // SimpleDateFormat import 추가
import java.util.Date // Date import 추가
import java.util.Locale // Locale import 추가

class ChatActivity2 : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CampusPatrolRobotAppTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    EventScreen()
                }
            }
        }
    }

    // 이 함수는 UI 컴포저블 밖에서 호출되어야 합니다.
    fun saveEvents(context: Context, events: List<String>) {
        val prefs = context.getSharedPreferences("EventLogs", Context.MODE_PRIVATE)
        prefs.edit().putStringSet("logs", events.toSet()).apply()
        Log.d("ChatActivity2", "Events saved: ${events.size}")
    }

    @Composable
    fun EventScreen(modifier: Modifier = Modifier) {
        val events = remember { mutableStateListOf<String>() }
        val context = LocalContext.current
        var showClearDialog by remember { mutableStateOf(false) }
        val firestore = Firebase.firestore

        LaunchedEffect(Unit) {
            val prefs = context.getSharedPreferences("EventLogs", Context.MODE_PRIVATE)
            val savedLogs = prefs.getStringSet("logs", emptySet())?.toList() ?: emptyList()
            events.addAll(savedLogs)
            Log.d("ChatActivity2", "Loaded ${savedLogs.size} events from SharedPreferences.")

            firestore.collection("Event_Gps_Data")
                // 서버 타임스탬프를 사용하는 경우, orderBy("timestamp", Query.Direction.DESCENDING)가 정상 작동합니다.
                // 문자열 타임스탬프를 사용하는 경우, 문자열 비교가 되어 원하는 정렬이 아닐 수 있습니다.
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener { snapshot, e ->
                    if (e != null) {
                        Log.e("ChatActivity2", "Error listening for events: $e")
                        return@addSnapshotListener
                    }
                    snapshot?.documentChanges?.forEach { change ->
                        if (change.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                            val data = change.document.data
                            // Firebase 서버 타임스탬프를 Date 객체로 변환
                            val timestampObject = data["timestamp"] as? com.google.firebase.Timestamp
                            val formattedTimestamp = timestampObject?.toDate()?.let {
                                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(it)
                            } ?: "알 수 없는 시간"

                            val eventType = data["eventType"] as? String ?: "이벤트" // 'eventType' 필드 사용
                            val lat = data["latitude"] as? Double ?: 0.0
                            val lng = data["longitude"] as? Double ?: 0.0

                            // 실제 위도와 경도를 사용하여 구글 맵 URL 생성
                            val mapUrl = "http://maps.google.com/maps?q=$lat,$lng"

                            val event = "$formattedTimestamp: $eventType - 지도: $mapUrl"

                            if (event !in events) {
                                events.add(0, event) // 최신 이벤트를 목록 맨 앞에 추가
                                if (events.size > 50) events.removeAt(events.size - 1) // 50개 제한
                                saveEvents(context, events.toList()) // 변경된 events를 저장
                                Log.d("ChatActivity2", "New event added: $event")
                            } else {
                                Log.d("ChatActivity2", "Duplicate event detected, ignoring: $event")
                            }
                        }
                    }
                }
        }

        // 이 함수도 Composable 밖에서 정의되거나, 외부에서 주입되어야 합니다.
        // 현재는 이 Composable 함수 안에서 정의되어 Composable의 리컴포지션에 영향을 받을 수 있습니다.
        fun clearEvents() {
            events.clear()
            val prefs = context.getSharedPreferences("EventLogs", Context.MODE_PRIVATE)
            prefs.edit().remove("logs").apply()
            Log.d("ChatActivity2", "Events cleared from SharedPreferences.")
        }

        if (showClearDialog) {
            AlertDialog(
                onDismissRequest = { showClearDialog = false },
                title = { Text("이벤트 로그 지우기") },
                text = { Text("로컬 이벤트 로그를 삭제하시겠습니까? (Firestore 데이터는 유지됩니다)") },
                confirmButton = {
                    Button(onClick = {
                        clearEvents()
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

        Column(modifier = modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "이벤트 알림",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Button(
                    onClick = {
                        showClearDialog = true // 다이얼로그 표시 상태를 true로 설정
                        Log.d("ChatActivity2", "Clear logs button clicked, showClearDialog = true")
                    },
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text("로그 지우기")
                }
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                reverseLayout = true // 최신 항목이 아래에 표시되도록
            ) {
                items(events.size) { index ->
                    // LazyColumn의 reverseLayout이 true이므로, index를 그대로 사용하면 최신 항목부터 표시됩니다.
                    // val reversedIndex = events.size - 1 - index // reverseLayout 사용 시 이 줄은 불필요
                    val event = events[index]
                    EventItem(event = event)
                }
            }
        }
    }

    @Composable
    fun EventItem(event: String) {
        var expanded by remember { mutableStateOf(false) } // 현재 사용되지 않음 (확장 기능 제거)
        var scale by remember { mutableStateOf(1f) }
        val context = LocalContext.current
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .scale(scale)
                .clickable {
                    scale = 0.95f // 클릭 시 스케일 변화 (시각적 피드백)
                    val url = event.substringAfter("지도: ").trim()
                    if (url.isNotEmpty() && url.startsWith("http")) { // URL 유효성 검사 추가
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            context.startActivity(intent)
                            Log.d("ChatActivity2", "Opened map URL: $url")
                        } catch (e: Exception) {
                            Log.e("ChatActivity2", "Error opening map URL: $url, ${e.message}")
                            // 사용자에게 오류 메시지를 표시할 수도 있습니다.
                        }
                    } else {
                        Log.w("ChatActivity2", "Invalid map URL: $url")
                    }
                    // 클릭 후 다시 원래 스케일로 복귀 (선택 사항)
                    // scale = 1f
                }
                .animateContentSize(animationSpec = spring()),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = event,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium
                )
                // 확장 기능이 필요 없다면 이 부분 제거
                // if (expanded) {
                //     Text(
                //         text = "상세 정보: ${event.split(" - ")[0]}",
                //         color = MaterialTheme.colorScheme.onSurface,
                //         style = MaterialTheme.typography.bodySmall,
                //         modifier = Modifier.padding(top = 4.dp)
                //     )
                // }
            }
        }
    }
}