package com.fgmembers.samlauncher.ui.chat

import androidx.compose.foundation.background
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

private val Night = Color(0xFF070B12)
private val Surface = Color(0xFF101827)
private val Neon = Color(0xFF00FF66)

@Composable
fun ModernChatScreen(
    messages: List<CaregiverMessageEntity>,
    isOnline: Boolean,
    batteryPercent: Int,
    status: String,
    onSend: (String) -> Unit,
    onVoice: () -> Unit,
    onAppearance: () -> Unit,
    onOtherAi: () -> Unit,
    onSettings: () -> Unit,
) {
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }
    fun send() {
        input.trim().takeIf { it.isNotEmpty() }?.let { onSend(it); input = "" }
    }
    MaterialTheme(colorScheme = darkColorScheme(background = Night, surface = Surface, primary = Neon)) {
        Column(Modifier.fillMaxSize().background(Night).padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                RoboticMascot(isOnline = isOnline)
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text("سام", color = Color.White, fontSize = 20.sp)
                    Text(status, color = if (isOnline) Neon else Color(0xFFFF6677), fontSize = 12.sp)
                }
                Text("$batteryPercent٪", color = Neon, fontSize = 12.sp)
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                TextButton(onClick = onAppearance) { Text("ظاهر") }
                TextButton(onClick = onOtherAi) { Text("هوش‌های دیگر") }
                TextButton(onClick = onSettings) { Text("تنظیمات AI") }
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, contentPadding = PaddingValues(vertical = 8.dp)) {
                items(messages, key = { it.id }) { message ->
                    val isUser = message.role == CaregiverRepository.ROLE_USER
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
                        Surface(
                            color = if (isUser) Color(0xFF123D36) else Surface,
                            shape = RoundedCornerShape(16.dp),
                            tonalElevation = 2.dp,
                            modifier = Modifier.widthIn(max = 310.dp)
                        ) { Text(message.content, color = Color.White, modifier = Modifier.padding(13.dp), fontSize = 16.sp) }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                OutlinedTextField(
                    value = input, onValueChange = { input = it }, modifier = Modifier.weight(1f),
                    placeholder = { Text("پیام یا دستور خود را بنویسید") }, maxLines = 4,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Neon, cursorColor = Neon)
                )
                TextButton(onClick = onVoice, modifier = Modifier.height(56.dp)) { Text("صدا") }
            }
            Button(onClick = ::send, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("ارسال به سام") }
        }
    }
}
