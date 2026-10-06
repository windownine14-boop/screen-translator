package com.screentranslate.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.screentranslate.app.R
import com.screentranslate.app.model.*
import com.screentranslate.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    isServiceRunning: Boolean,
    hasOverlayPermission: Boolean,
    hasCapturePermission: Boolean,
    quotaStatus: QuotaStatus,
    currentApiKey: String,
    onSaveApiKey: (String) -> Unit,
    onToggleService: (Boolean, EngineType) -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onRequestCapturePermission: () -> Unit
) {
    var selectedLanguage by remember { mutableStateOf(SupportedLanguage.ENGLISH) }
    var selectedMode by remember { mutableStateOf(TranslationMode.SNAP) }
    var selectedStyle by remember { mutableStateOf(PresentationStyle.IN_PLACE) }
    var selectedEngine by remember { mutableStateOf(EngineType.ML_KIT_OFFLINE) }

    var apiKeyInput by remember { mutableStateOf(currentApiKey) }
    var showApiKey by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_translate),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "Screen Translator",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground,
                    titleContentColor = TextPrimary
                )
            )
        },
        containerColor = DarkBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // 1. Service Master Switch Card
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isServiceRunning) "ระบบกำลังทำงาน (Active)" else "ระบบปิดอยู่ (Offline)",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isServiceRunning) AccentGreen else TextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isServiceRunning) "ปุ่มลอยกำลังแสดงบนหน้าจอ" else "เปิดเพื่อแสดงปุ่มลอยสำหรับแปล",
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                    }
                    Switch(
                        checked = isServiceRunning,
                        onCheckedChange = { onToggleService(it, selectedEngine) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }

            // 2. Permissions Checker Section
            Text("การอนุญาตของระบบ (Permissions)", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Overlay Permission
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("แสดงทับแอปอื่น (Overlay)", fontSize = 14.sp, color = TextPrimary)
                            Text("จำเป็นสำหรับแสดงปุ่มลอย", fontSize = 12.sp, color = TextSecondary)
                        }
                        if (hasOverlayPermission) {
                            Text("✓ อนุญาตแล้ว", color = AccentGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Button(
                                onClick = onRequestOverlayPermission,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("ตั้งค่า", fontSize = 12.sp)
                            }
                        }
                    }

                    HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.5f))

                    // Screen Capture Permission
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("ดึงภาพหน้าจอ (MediaProjection)", fontSize = 14.sp, color = TextPrimary)
                            Text("จำเป็นสำหรับอ่านข้อความ", fontSize = 12.sp, color = TextSecondary)
                        }
                        if (hasCapturePermission) {
                            Text("✓ อนุญาตแล้ว", color = AccentGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Button(
                                onClick = onRequestCapturePermission,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("อนุญาต", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // 3. AI Engine Selection (ML Kit vs Gemini 3.5 Flash-Lite)
            Text("โมเดล AI ในการแปล", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    EngineType.values().forEach { engine ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selectedEngine == engine) DarkSurface else Color.Transparent)
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = selectedEngine == engine, onClick = { selectedEngine = engine })
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(engine.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary)
                                Text(
                                    if (engine.isOffline) "ทำงานในเครื่อง 100% เร็วระดับเสี้ยววินาที ฟรีตลอดชีพ"
                                    else "แปลมังงะ/เกมสละสลวยพิเศษ เข้าใจมุกตลกและศัพท์แสลง",
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }

            // 4. Gemini Quota & API Key Dashboard (Shown when Flash-Lite is selected)
            if (selectedEngine == EngineType.GEMINI_FLASH_LITE) {
                Text("สถานะโควต้าฟรี Gemini 3.5 Flash-Lite", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF67E8F9))
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F243A)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        
                        // Quota Tracker Numbers
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("โควต้าฟรีวันนี้:", fontSize = 13.sp, color = TextSecondary)
                            Text(
                                "เหลือ ${quotaStatus.remaining} / ${quotaStatus.maxDaily} ครั้ง",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (quotaStatus.remaining > 100) AccentGreen else Color(0xFFF87171)
                            )
                        }

                        // Progress Bar (Remaining percentage)
                        val progress = (quotaStatus.remaining.toFloat() / quotaStatus.maxDaily.toFloat()).coerceIn(0f, 1f)
                        LinearProgressIndicator(
                            progress = progress,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = if (progress > 0.2f) AccentGreen else Color(0xFFEF4444),
                            trackColor = Color(0xFF1E293B)
                        )

                        // Reset Countdown
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("รีเซ็ตโควต้าใหม่ในอีก:", fontSize = 12.sp, color = TextSecondary)
                            Text(
                                "${quotaStatus.resetsInHours} ชั่วโมง ${quotaStatus.resetsInMinutes} นาที (เวลา ~14:00 น.)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF38BDF8)
                            )
                        }

                        Text(
                            "💡 หากโควต้าฟรีหมดลง ระบบจะสลับไปใช้ Google ML Kit (ออฟไลน์) แปลต่อให้อัตโนมัติทันที ไม่ต้องกลัวสะดุด",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8),
                            lineHeight = 15.sp
                        )

                        HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.5f))

                        // API Key Input
                        Text("ใส่ Gemini API Key ฟรี:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        OutlinedTextField(
                            value = apiKeyInput,
                            onValueChange = { apiKeyInput = it },
                            placeholder = { Text("วางรหัส AIzaSy... ที่นี่", fontSize = 12.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            trailingIcon = {
                                IconButton(onClick = { showApiKey = !showApiKey }) {
                                    Icon(
                                        if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = null
                                    )
                                }
                            }
                        )

                        Button(
                            onClick = { onSaveApiKey(apiKeyInput) },
                            modifier = Modifier.align(Alignment.End),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text("บันทึก API Key", fontSize = 12.sp)
                        }
                    }
                }
            }

            // 5. Language Configuration
            Text("ภาษาต้นทางที่ต้องการแปล", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SupportedLanguage.values().forEach { lang ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selectedLanguage == lang) DarkSurface else Color.Transparent)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedLanguage == lang,
                                onClick = { selectedLanguage = lang }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(lang.title, fontSize = 14.sp, color = TextPrimary)
                        }
                    }
                }
            }

            // 6. Translation Mode (Snap vs Live)
            Text("โหมดการทำงาน", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    TranslationMode.values().forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selectedMode == mode) DarkSurface else Color.Transparent)
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = selectedMode == mode, onClick = { selectedMode = mode })
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(mode.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary)
                                Text(mode.description, fontSize = 12.sp, color = TextSecondary)
                            }
                        }
                    }
                }
            }

            // 7. Presentation Style (In-Place vs Subtitle)
            Text("รูปแบบการแสดงผลคำแปล", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PresentationStyle.values().forEach { style ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selectedStyle == style) DarkSurface else Color.Transparent)
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = selectedStyle == style, onClick = { selectedStyle = style })
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(style.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary)
                                Text(style.description, fontSize = 12.sp, color = TextSecondary)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
