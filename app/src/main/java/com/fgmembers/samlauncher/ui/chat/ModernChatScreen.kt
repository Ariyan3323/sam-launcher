package com.fgmembers.samlauncher.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fgmembers.samlauncher.ui.components.RoboticMascot
import de.szalkowski.activitylauncher.agent.data.CaregiverMessageEntity
import de.szalkowski.activitylauncher.agent.data.CaregiverRepository

private val Night = Color(0xFF050811)
private val Glass = Color(0xFF111B2B).copy(alpha = .84f)
private val Neon = Color(0xFF00FF66)
private val Cyan = Color(0xFF35D9FF)

@Composable
fun ModernChatScreen(
    messages: List<CaregiverMessageEntity>, isOnline: Boolean, batteryPercent: Int, status: String,
    onSend: (String) -> Unit, onVoice: () -> Unit, onAttachment: () -> Unit,
    onAppearance: () -> Unit, onOtherAi: () -> Unit, onSettings: () -> Unit,
) {
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex) }
    fun send() { input.trim().takeIf { it.isNotEmpty() }?.let { onSend(it); input = "" } }

    MaterialTheme(colorScheme = darkColorScheme(background = Night, surface = Glass, primary = Neon)) {
        Column(Modifier.fillMaxSize().background(Night).padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                RoboticMascot(isOnline)
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    Text("سام", color = Color.White, fontSize = 21.sp)
                    Text(status, color = if (isOnline) Neon else Color(0xFFFF5566), fontSize = 12.sp)
                }
                Text("باتری $batteryPercent٪", color = Cyan, fontSize = 12.sp)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                TextButton(onClick = onAppearance) { Text("ظاهر") }
                TextButton(onClick = onOtherAi) { Text("هوش‌های دیگر") }
                TextButton(onClick = onSettings) { Text("تنظیمات") }
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, contentPadding = PaddingValues(vertical = 8.dp)) {
                items(messages, key = { it.id }) { message ->
                    val user = message.role == CaregiverRepository.ROLE_USER
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start) {
                        Surface(
                            color = if (user) Color(0xFF103C35).copy(alpha = .88f) else Glass,
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.widthIn(max = 320.dp).border(1.dp, if (user) Neon.copy(alpha = .55f) else Cyan.copy(alpha = .45f), RoundedCornerShape(18.dp))
                        ) { Text(message.content, color = Color.White, modifier = Modifier.padding(13.dp), fontSize = 16.sp) }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AssistChip(onClick = { onSend("فایل‌های من را باز کن") }, label = { Text("📂 فایل‌ها") })
                AssistChip(onClick = { onSend("وضعیت سیستم را بگو") }, label = { Text("⚡ وضعیت سیستم") })
                AssistChip(onClick = { input = "جستجو: " }, label = { Text("🔍 جستجو") })
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.Bottom) {
                IconButton(onClick = onAttachment) { Text("＋", color = Cyan, fontSize = 28.sp) }
                OutlinedTextField(
                    value = input, onValueChange = { input = it }, modifier = Modifier.weight(1f),
                    placeholder = { Text("پیام یا دستور…") }, maxLines = 4,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Neon, unfocusedBorderColor = Cyan.copy(alpha = .55f), cursorColor = Neon)
                )
                IconButton(onClick = onVoice) { Text("♩", color = Neon, fontSize = 24.sp) }
            }
            Button(onClick = ::send, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) { Text("ارسال به سام") }
        }
    }
}
