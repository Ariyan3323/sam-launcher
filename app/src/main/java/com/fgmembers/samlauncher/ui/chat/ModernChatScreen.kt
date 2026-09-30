package com.fgmembers.samlauncher.ui.chat

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.fgmembers.samlauncher.ui.components.MascotState
import de.szalkowski.activitylauncher.agent.data.CaregiverMessageEntity
import de.szalkowski.activitylauncher.agent.data.CaregiverRepository
import de.szalkowski.activitylauncher.ui.CyberHeadView

private val Night = Color(0xFF070B14)
private val SurfaceCard = Color(0xFF101928)
private val Neon = Color(0xFF00FF66)
private val Cyan = Color(0xFF35D9FF)
private val BorderGlow = Cyan.copy(alpha = 0.35f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModernChatScreen(
    messages: List<CaregiverMessageEntity>,
    isOnline: Boolean,
    batteryPercent: Int,
    status: String,
    mascotState: MascotState = MascotState.Idle,
    onSend: (String) -> Unit,
    onVoice: () -> Unit,
    onAttachment: () -> Unit,
    onQuickAction: (String) -> Unit,
    onAppearance: () -> Unit,
    onOtherAi: () -> Unit,
    onSettings: () -> Unit,
) {
    var input by remember { mutableStateOf("") }
    var showToolsSheet by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    fun submit() {
        val trimmed = input.trim()
        if (trimmed.isNotEmpty()) {
            onSend(trimmed)
            input = ""
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(background = Night, surface = SurfaceCard, primary = Cyan)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Night)
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .animateContentSize()
        ) {
            // Header: Live Animated Winged AI Orb Avatar (same as Launcher Hub)
            Surface(
                color = SurfaceCard,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderGlow, RoundedCornerShape(20.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(62.dp)
                            .padding(end = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        AndroidView(
                            factory = { context -> CyberHeadView(context) },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 10.dp)
                    ) {
                        Text(
                            text = "سام // دستیار هوشمند",
                            color = Color.White,
                            fontSize = 18.sp
                        )
                        Text(
                            text = if (isOnline) "🟢 متصل به هوش ابری و وب" else "🟡 حالت محلی و ابزارهای دستگاه",
                            color = if (isOnline) Neon else Color(0xFFFFB300),
                            fontSize = 12.sp
                        )
                    }

                    Surface(
                        color = Color(0xFF162235),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "⚡ $batteryPercent٪",
                            color = Cyan,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Messages List
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    val isUser = message.role == CaregiverRepository.ROLE_USER
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        if (!isUser) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .padding(end = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                AndroidView(
                                    factory = { context -> CyberHeadView(context) },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }

                        Surface(
                            color = if (isUser) Color(0xFF144D42) else SurfaceCard,
                            shape = RoundedCornerShape(
                                topStart = 18.dp,
                                topEnd = 18.dp,
                                bottomStart = if (isUser) 18.dp else 4.dp,
                                bottomEnd = if (isUser) 4.dp else 18.dp
                            ),
                            modifier = Modifier
                                .widthIn(max = 310.dp)
                                .border(
                                    1.dp,
                                    if (isUser) Neon.copy(alpha = 0.5f) else BorderGlow,
                                    RoundedCornerShape(18.dp)
                                )
                        ) {
                            Text(
                                text = message.content,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                fontSize = 15.sp,
                                lineHeight = 22.sp
                            )
                        }
                    }
                }
            }

            // Sleek Modern Input Bar with Plus button for tools and Mic
            Surface(
                color = SurfaceCard,
                shape = RoundedCornerShape(26.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderGlow, RoundedCornerShape(26.dp))
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // [+] Tools & Capabilities Button
                    IconButton(
                        onClick = { showToolsSheet = true }
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF1C2C45),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "＋",
                                    color = Cyan,
                                    fontSize = 24.sp
                                )
                            }
                        }
                    }

                    // Text Input Field
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp),
                        placeholder = {
                            Text(
                                text = "پیام، سوال یا دستور صوتی…",
                                color = Color.Gray,
                                fontSize = 14.sp
                            )
                        },
                        maxLines = 4,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            cursorColor = Cyan,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    // Voice Mic Button
                    IconButton(
                        onClick = onVoice
                    ) {
                        Text(
                            text = "🎤",
                            fontSize = 20.sp
                        )
                    }

                    // Send Button
                    if (input.isNotBlank()) {
                        IconButton(
                            onClick = ::submit
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Neon,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "➤",
                                        color = Color.Black,
                                        fontSize = 16.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Agent Tools & Capabilities Bottom Sheet
    if (showToolsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showToolsSheet = false },
            sheetState = sheetState,
            containerColor = Color(0xFF0C1420)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "مرکز قابلیت‌ها و ابزارهای سام",
                    color = Cyan,
                    fontSize = 18.sp
                )
                Text(
                    text = "ابزارهای هوشمند متصل به دستگاه و وب:",
                    color = Color.Gray,
                    fontSize = 13.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ToolCard(
                        title = "جستجوی وب",
                        icon = "🌐",
                        modifier = Modifier.weight(1f)
                    ) {
                        showToolsSheet = false
                        onQuickAction("search")
                    }
                    ToolCard(
                        title = "وضعیت سیستم",
                        icon = "⚡",
                        modifier = Modifier.weight(1f)
                    ) {
                        showToolsSheet = false
                        onQuickAction("system")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ToolCard(
                        title = "فایل‌های گوشی",
                        icon = "📁",
                        modifier = Modifier.weight(1f)
                    ) {
                        showToolsSheet = false
                        onQuickAction("files")
                    }
                    ToolCard(
                        title = "چراغ‌قوه",
                        icon = "💡",
                        modifier = Modifier.weight(1f)
                    ) {
                        showToolsSheet = false
                        onQuickAction("flashlight")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ToolCard(
                        title = "هوش‌های دیگر",
                        icon = "🧠",
                        modifier = Modifier.weight(1f)
                    ) {
                        showToolsSheet = false
                        onOtherAi()
                    }
                    ToolCard(
                        title = "تنظیمات سام",
                        icon = "⚙️",
                        modifier = Modifier.weight(1f)
                    ) {
                        showToolsSheet = false
                        onSettings()
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun ToolCard(
    title: String,
    icon: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = Color(0xFF142033),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.border(1.dp, BorderGlow, RoundedCornerShape(14.dp))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = icon, fontSize = 20.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                color = Color.White,
                fontSize = 14.sp
            )
        }
    }
}
