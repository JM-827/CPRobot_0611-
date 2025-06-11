package com.example.campuspatrolrobot

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val GROUP_KEY_ROBOT = "com.example.campuspatrolrobot.ROBOT_NOTIFICATIONS"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM", "새 토큰 생성: $token")
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d("FCM", "메시지 수신: ${remoteMessage.data}")

        val state = remoteMessage.data["state"] ?: "unknown"
        val title = remoteMessage.data["title"] ?: "새 알림"
        val body = remoteMessage.data["body"] ?: "내용 없음"

        val channelId = when (state) {
            "01" -> "patrol_complete_en"
            "10" -> "kickboard_detected_en"
            "11" -> "problem_detected_en"
            else -> {
                Log.w("FCM", "알 수 없는 상태: $state, 기본 채널 사용")
                "robot_state_en"
            }
        }

        // 아이콘과 색상 설정
        val iconRes = when (state) {
            "01" -> R.drawable.ic_patrol_complete // 순찰 완료
            "10" -> R.drawable.ic_kickboard // 킥보드 감지
            "11" -> R.drawable.ic_problem // 문제 감지
            else -> R.drawable.ic_notification // 기본 아이콘
        }

        val colorRes = when (state) {
            "01" -> 0xFF4CAF50.toInt() // 녹색
            "10" -> 0xFF2196F3.toInt() // 파란색
            "11" -> 0xFFF44336.toInt() // 빨간색
            else -> 0xFF9E9E9E.toInt() // 회색
        }

        // PendingIntent 설정
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0, // 고정된 요청 코드
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationId = System.currentTimeMillis().toInt()
        val notificationManager = getSystemService(NotificationManager::class.java)

        try {
            val builder = NotificationCompat.Builder(this, channelId)
                .setSmallIcon(iconRes)
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent) // 클릭 시 MainActivity로 이동
                .setColor(colorRes) // 색상 구분
                .setGroup(GROUP_KEY_ROBOT) // 그룹화로 알림 정리

            notificationManager.notify(notificationId, builder.build())
            Log.d("FCM", "알림 전송 성공: ID=$notificationId, 채널=$channelId")
        } catch (e: Exception) {
            Log.e("FCM", "알림 전송 실패: ${e.message}", e)
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channels = listOf(
                NotificationChannel(
                    "robot_state_en",
                    "로봇 상태 알림",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    enableVibration(true)
                    setBypassDnd(true)
                },
                NotificationChannel(
                    "patrol_complete_en",
                    "순찰 완료 알림",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    enableVibration(true)
                    setBypassDnd(true)
                },
                NotificationChannel(
                    "kickboard_detected_en",
                    "킥보드 감지 알림",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    enableVibration(true)
                    setBypassDnd(true)
                },
                NotificationChannel(
                    "problem_detected_en",
                    "문제 감지 알림",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    enableVibration(true)
                    setBypassDnd(true)
                }
            )

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannels(channels)
            Log.d("FCM", "알림 채널 생성: ${channels.map { it.id }}")
        }
    }
}