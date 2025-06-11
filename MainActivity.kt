package com.example.campuspatrolrobot

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.campuspatrolrobot.ui.theme.CampusPatrolRobotAppTheme
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.ktx.messaging
import com.google.firebase.remoteconfig.ktx.remoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfigSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    // MainActivity에서는 알림 채널을 생성하지 않고, MyFirebaseMessagingService에서 모든 채널을 생성하도록 합니다.
    // companion object {
    //     private const val CHANNEL_ID = "robot_state_channel"
    // }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // MyFirebaseMessagingService에서 모든 채널을 관리하므로 여기서는 채널 설정 제거
        // if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        //     val channel = NotificationChannel(
        //         CHANNEL_ID,
        //         "Robot State Notifications",
        //         NotificationManager.IMPORTANCE_HIGH
        //     ).apply {
        //         enableVibration(true)
        //         setBypassDnd(true)
        //         setSound(null, null) // 기본 소리 비활성화
        //     }
        //     getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        //     Log.d("FCM", "Notification channel initialized")
        // }

        // FCM 토픽 구독 수정: Cloud Functions에서 보내는 토픽과 일치하도록 변경
        CoroutineScope(Dispatchers.IO).launch {
            // 로봇 상태 변경 알림 토픽 구독
            Firebase.messaging.subscribeToTopic("patrol_complete_en")
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.d("FCM", "Subscribed to patrol_complete_en topic")
                    } else {
                        Log.e("FCM", "Subscription failed for patrol_complete_en: ${task.exception}")
                    }
                }
            Firebase.messaging.subscribeToTopic("kickboard_detected_en")
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.d("FCM", "Subscribed to kickboard_detected_en topic")
                    } else {
                        Log.e("FCM", "Subscription failed for kickboard_detected_en: ${task.exception}")
                    }
                }
            Firebase.messaging.subscribeToTopic("problem_detected_en")
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.d("FCM", "Subscribed to problem_detected_en topic")
                    } else {
                        Log.e("FCM", "Subscription failed for problem_detected_en: ${task.exception}")
                    }
                }

            // FCM 토큰 가져오기 (디버깅용)
            Firebase.messaging.token.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d("FCM", "FCM Token: ${task.result}")
                } else {
                    Log.e("FCM", "Failed to get FCM token: ${task.exception}")
                }
            }
        }

        // Firebase Remote Config 초기화 (기존 로직 유지)
        val remoteConfig = Firebase.remoteConfig
        val configSettings = remoteConfigSettings {
            minimumFetchIntervalInSeconds = 0
        }
        remoteConfig.setConfigSettingsAsync(configSettings)
        remoteConfig.setDefaultsAsync(mapOf("auth_key" to "default_auth_key"))

        remoteConfig.fetchAndActivate().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val authKey = remoteConfig.getString("auth_key")
                Log.d("RemoteConfig", "Auth Key: $authKey")
            } else {
                Log.e("RemoteConfig", "Fetch failed")
            }
        }

        setContent {
            CampusPatrolRobotAppTheme(darkTheme = true) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.background
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Campus Patrol Robot",
                            modifier = Modifier.padding(bottom = 32.dp),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        ActionCard(
                            icon = Icons.Default.ChatBubble,
                            label = "명령어 채팅",
                            onClick = {
                                startActivity(Intent(this@MainActivity, ChatActivity1::class.java))
                            }
                        )
                        ActionCard(
                            icon = Icons.Default.Notifications,
                            label = "이벤트 알림",
                            onClick = {
                                startActivity(Intent(this@MainActivity, ChatActivity2::class.java))
                            }
                        )
                        ActionCard(
                            icon = Icons.Default.Place,
                            label = "실시간 지도",
                            onClick = {
                                startActivity(Intent(this@MainActivity, MapActivity::class.java))
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ActionCard(icon: ImageVector, label: String, onClick: () -> Unit) {
    var scale by remember { mutableStateOf(1f) }
    Surface(
        modifier = Modifier
            .fillMaxWidth(0.8f)
            .padding(vertical = 8.dp)
            .scale(scale)
            .clickable(
                onClick = {
                    scale = 0.95f
                    onClick()
                }
            )
            .animateContentSize(animationSpec = spring()),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 16.dp)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Icon(
                imageVector = Icons.Default.ArrowForward,
                contentDescription = "이동",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Welcome to $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    CampusPatrolRobotAppTheme(darkTheme = true) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Campus Patrol Robot",
                modifier = Modifier.padding(bottom = 32.dp),
                style = MaterialTheme.typography.headlineMedium
            )
            ActionCard(Icons.Default.ChatBubble, "명령어 채팅") {}
            ActionCard(Icons.Default.Notifications, "이벤트 알림") {}
            ActionCard(Icons.Default.Place, "실시간 지도") {}
        }
    }
}