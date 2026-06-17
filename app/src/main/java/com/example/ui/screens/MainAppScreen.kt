package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.LiveStreamEntity
import com.example.ui.components.VideoPlayerView
import com.example.viewmodel.IptvSyncState
import com.example.viewmodel.MatchSearchState
import com.example.viewmodel.SportsMatchViewModel

// Deep Space Theme Colors
val SpaceDarkBg = Color(0xFF0C0E14)
val SpaceCardBg = Color(0xFF161924)
val NeonGreen = Color(0xFF00FF66)
val NeonBlue = Color(0xFF00D2FF)
val TextWhite = Color(0xFFF5F7FA)
val TextGray = Color(0xFF7E8494)
val AlertRed = Color(0xFFFF3B30)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(
    viewModel: SportsMatchViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
    val searchState by viewModel.searchState.collectAsStateWithLifecycle()
    val playingStream by viewModel.playingStream.collectAsStateWithLifecycle()
    val manualInputText by viewModel.manualInputText.collectAsStateWithLifecycle()

    val isListening by viewModel.sttManager.isListening.collectAsStateWithLifecycle()
    val micSoundLevel by viewModel.sttManager.soundLevel.collectAsStateWithLifecycle()
    val geminiApiKey by viewModel.geminiApiKey.collectAsStateWithLifecycle()

    // Permission Handler
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            hasMicPermission = isGranted
            if (isGranted) {
                viewModel.toggleRecording()
            } else {
                Toast.makeText(context, "الرجاء توفير صلاحيات المايكروفون لتتمكن من استخدام ميزة مطابقة الصوت", Toast.LENGTH_LONG).show()
            }
        }
    )

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(SpaceDarkBg),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Soccer logo",
                            tint = NeonGreen,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "المساعد الرياضي الذكي",
                            color = TextWhite,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                actions = {
                    Box(modifier = Modifier.padding(end = 8.dp)) {
                        when (val s = syncState) {
                            is IptvSyncState.Syncing -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = NeonGreen,
                                    strokeWidth = 2.dp
                                )
                            }
                            is IptvSyncState.Loaded -> {
                                IconButton(
                                    onClick = { viewModel.syncIptvChannels(force = true) },
                                    modifier = Modifier.testTag("refresh_channels_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Force reload live channels",
                                        tint = NeonGreen
                                    )
                                }
                            }
                            is IptvSyncState.Error -> {
                                IconButton(
                                    onClick = { viewModel.syncIptvChannels(force = true) },
                                    modifier = Modifier.testTag("retry_sync_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = "Sync channels error. Retry.",
                                        tint = AlertRed
                                    )
                                }
                            }
                            else -> {}
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = SpaceDarkBg
                )
            )
        },
        containerColor = SpaceDarkBg
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // LazyColumn to enable scrollable screen content
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Feature Banner
                item {
                    MainAppBanner()
                }

                // Sync status indicator alert
                item {
                    when (val s = syncState) {
                        is IptvSyncState.Syncing -> {
                            SyncStatusCard(
                                message = "جاري تحديث قنوات البث من السيرفر...",
                                subMessage = "يرجى الانتظار لحين مزامنة الاشتراك الرياضي.",
                                loading = true,
                                color = NeonBlue
                            )
                        }
                        is IptvSyncState.Loaded -> {
                            SyncStatusCard(
                                message = "تم تحميل قنوات IPTV الحية بنجاح!",
                                subMessage = "القنوات المتوفرة في اشتراكك: ${s.channelCount} قناة حية.",
                                loading = false,
                                color = NeonGreen
                            )
                        }
                        is IptvSyncState.Error -> {
                            SyncStatusCard(
                                message = "فشل تحميل قنوات السيرفر: ${s.message}",
                                subMessage = "يرجى التحقق من اتصال الإنترنت أو الضغط على زر التحديث في الأعلى لإعادة المحاولة.",
                                loading = false,
                                color = AlertRed
                            )
                        }
                        else -> {}
                    }
                }

                // Gemini API Configuration Card
                item {
                    GeminiApiKeyCard(
                        savedKey = geminiApiKey,
                        onKeySaved = { viewModel.saveGeminiApiKey(it) }
                    )
                }

                // Audio microphone button area
                item {
                    VoiceRecordingConsole(
                        isListening = isListening,
                        soundLevel = micSoundLevel,
                        onClick = {
                            if (hasMicPermission) {
                                viewModel.toggleRecording()
                            } else {
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    )
                }

                // Status message displays
                item {
                    StatusOverviewSection(
                        searchState = searchState,
                        onRetryVoice = {
                            if (hasMicPermission) {
                                viewModel.toggleRecording()
                            } else {
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    )
                }

                // Manual Input Fallback
                item {
                    ManualSearchBox(
                        value = manualInputText,
                        onValueChange = viewModel::updateManualInputText,
                        onSearch = {
                            keyboardController?.hide()
                            viewModel.performManualSearch()
                        }
                    )
                }

                // Matched Channels Display list
                when (val state = searchState) {
                    is MatchSearchState.FoundMatches -> {
                        item {
                            Text(
                                text = "القنوات الرياضية التي تم العثور عليها:",
                                color = TextWhite,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                textAlign = TextAlign.Right
                            )
                        }

                        items(state.matches) { matched ->
                            MatchedChannelCard(
                                name = matched.stream.name,
                                category = matched.stream.categoryName ?: "رياضة حية",
                                score = matched.matchScore,
                                onClick = {
                                    viewModel.playStream(matched.stream)
                                }
                            )
                        }
                    }
                    is MatchSearchState.NoMatchFound -> {
                        item {
                            NoMatchDisplayCard(
                                explanation = state.explanation,
                                recChannels = state.recChannels
                            )
                        }
                    }
                    else -> {}
                }

                // Add empty padding spacer at bottom of list
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }

    // In-App Video Player Dialog overlay (supports seamless internal playback)
    playingStream?.let { stream ->
        val fullStreamUrl = viewModel.getStreamUrl(stream.streamId)
        Log.d("MainAppScreen", "Constructed playing stream URL: $fullStreamUrl")

        Dialog(
            onDismissRequest = { viewModel.stopPlayback() },
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.95f))
                    .testTag("in_app_player_container")
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Title Bar inside Player Overlay
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = { viewModel.stopPlayback() },
                            modifier = Modifier
                                .background(Color.White.copy(alpha = 0.2f), CircleShape)
                                .testTag("close_player_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close stream video",
                                tint = Color.White
                            )
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.End
                        ) {
                            Text(
                                text = stream.name,
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Right
                            )
                            Text(
                                text = stream.categoryName ?: "IPTV البث المباشر",
                                color = TextGray,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Right
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Embedded ExoPlayer
                    VideoPlayerView(
                        url = fullStreamUrl,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .testTag("exo_video_player"),
                        onPlayerError = { errMsg ->
                            Toast.makeText(context, errMsg, Toast.LENGTH_LONG).show()
                        }
                    )

                    Spacer(modifier = Modifier.weight(1.5f))

                    // Helpful overlay indicator
                    Row(
                        modifier = Modifier
                            .padding(bottom = 32.dp)
                            .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Screen indicator",
                            tint = NeonGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "مشغل متطور مدمج في مساعد المباريات",
                            color = Color.White,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MainAppBanner() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp)
            .border(
                border = BorderStroke(1.dp, Brush.horizontalGradient(listOf(NeonGreen, NeonBlue))),
                shape = RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(id = R.drawable.img_sports_banner),
                contentDescription = "Sports match banner background",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            // Ambient Overlay gradient
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = "تابع فريقك بضغطة زر واحدة",
                    color = TextWhite,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Right
                )
                Text(
                    text = "شغل مباريات اليوم عبر التحكم الصوتي والذكاء الاصطناعي",
                    color = TextWhite.copy(alpha = 0.8f),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

@Composable
fun SyncStatusCard(
    message: String,
    subMessage: String,
    loading: Boolean,
    color: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = SpaceCardBg
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = color,
                    strokeWidth = 2.dp
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(color, CircleShape)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = message,
                    color = TextWhite,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Right,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subMessage,
                    color = TextGray,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.padding(top = 4.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun VoiceRecordingConsole(
    isListening: Boolean,
    soundLevel: Float,
    onClick: () -> Unit
) {
    // Elegant pulsing and breathing speech animations
    val transition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    // Dynamic wave expansion depending on active voice DB levels
    val micScale = if (isListening) {
        pulseScale + (soundLevel * 0.25f)
    } else {
        1.0f
    }

    val glowColor = if (isListening) NeonGreen else NeonGreen.copy(alpha = 0.15f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Glowing Breathing Mic Wrapper
        Box(
            modifier = Modifier
                .size(140.dp),
            contentAlignment = Alignment.Center
        ) {
            // Ripple background
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .scale(micScale)
                    .background(
                        Brush.radialGradient(
                            listOf(glowColor.copy(alpha = 0.40f), Color.Transparent)
                        ),
                        CircleShape
                    )
            )

            // Inner solid microphone button circle
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .shadow(elevation = 12.dp, shape = CircleShape)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF202330), Color(0xFF141620))
                        ),
                        CircleShape
                    )
                    .border(
                        border = BorderStroke(
                            2.dp,
                            if (isListening) NeonGreen else Color.White.copy(alpha = 0.1f)
                        ),
                        shape = CircleShape
                    )
                    .clickable(
                        onClick = onClick
                    )
                    .testTag("microphone_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Speak sport match command",
                    tint = if (isListening) NeonGreen else TextWhite,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = if (isListening) "جاري الاستماع لصوتك..." else "اضغط للتحدث بمباراة اليوم",
            color = if (isListening) NeonGreen else TextWhite,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp
        )
    }
}

@Composable
fun StatusOverviewSection(
    searchState: MatchSearchState,
    onRetryVoice: () -> Unit
) {
    AnimatedContent(
        targetState = searchState,
        transitionSpec = {
            fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(220))
        },
        label = "status_anim"
    ) { state ->
        when (state) {
            is MatchSearchState.Listening -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(NeonGreen.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "قل بوضوح: 'شغل لي مباراة [الفريقين]'",
                        color = NeonGreen,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "مثال: 'شغل لي كلاسيكو برشلونة وريال مدريد'",
                        color = TextGray,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            is MatchSearchState.AnalyzingMatch -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(NeonBlue.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = NeonBlue,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "المساعد الذكي يبحث ويحلل القنوات الحالية الناقلة لمباراة:",
                        color = TextWhite,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "\"${state.voiceInput}\"",
                        color = NeonBlue,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            is MatchSearchState.MatchingStreams -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = NeonGreen,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "جاري مطابقة القنوات مع اشتراكك IPTV الحذر...",
                        color = TextGray,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
            is MatchSearchState.Error -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AlertRed.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Error notification",
                        tint = AlertRed,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = state.message,
                        color = TextWhite,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("error_status_label")
                    )
                    Button(
                        onClick = onRetryVoice,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AlertRed),
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .testTag("retry_voice_button"),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Text("إعادة المحاولة الصوتية", fontSize = 11.sp, color = Color.White)
                    }
                }
            }
            else -> {
                // Idle / Default instructions
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SpaceCardBg, RoundedCornerShape(12.dp))
                        .padding(16.dp)
                ) {
                    Text(
                        text = "كيف يعمل المساعد؟",
                        color = TextWhite,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Right
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "1. اضغط على المايكروفون وتحدث بصوتك باسم المباراة الحالية.\n" +
                               "2. يقوم الذكاء الاصطناعي بالبحث الفوري وتحديد القنوات الناقلة بدقة.\n" +
                               "3. نقوم بمطابقة القنوات ديناميكياً مع اشتراكك وعرضها لك لتشغيلها والفرجة فوراً داخل مشغل التطبيق المدمج.",
                        color = TextGray,
                        fontSize = 11.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Right
                    )
                }
            }
        }
    }
}

@Composable
fun ManualSearchBox(
    value: String,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = SpaceCardBg
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = "هل تفضل البحث اليدوي؟ اكتب اسم المباراة أو القناة هنا:",
                color = TextWhite,
                fontSize = 12.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                textAlign = TextAlign.Right
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onSearch,
                    modifier = Modifier
                        .background(NeonGreen, RoundedCornerShape(8.dp))
                        .size(44.dp)
                        .testTag("manual_search_submit")
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Submit manual text search",
                        tint = SpaceDarkBg
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                TextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("manual_search_input"),
                    placeholder = {
                        Text(
                            text = "مثل: مدريد، SSC، الأهلي، beIN...",
                            fontSize = 12.sp,
                            color = TextGray,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Right
                        )
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = SpaceDarkBg,
                        unfocusedContainerColor = SpaceDarkBg,
                        disabledContainerColor = SpaceDarkBg,
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Search
                    ),
                    keyboardActions = KeyboardActions(
                        onSearch = { onSearch() }
                    )
                )
            }
        }
    }
}

@Composable
fun MatchedChannelCard(
    name: String,
    category: String,
    score: Double,
    onClick: () -> Unit
) {
    val percentage = (score * 100).toInt().coerceIn(0, 100)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("matched_channel_card")
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SpaceCardBg)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Match score percentage pill
            Box(
                modifier = Modifier
                    .background(
                        if (percentage >= 70) NeonGreen.copy(alpha = 0.15f) else TextGray.copy(alpha = 0.15f),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "$percentage% تطابق",
                    color = if (percentage >= 70) NeonGreen else TextWhite,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Channel descriptors
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = name,
                    color = TextWhite,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Right,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = category,
                    color = TextGray,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // TV icon display
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(NeonGreen.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Live sport on TV",
                    tint = NeonGreen,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun NoMatchDisplayCard(
    explanation: String,
    recChannels: List<String>
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("no_match_found_card"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SpaceCardBg)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.End
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Channels mismatch alert",
                    tint = AlertRed,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "القناة غير متوفرة في اشتراكك",
                    color = AlertRed,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Right
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = explanation,
                color = TextWhite,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                textAlign = TextAlign.Right,
                modifier = Modifier.fillMaxWidth()
            )

            if (recChannels.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "القنوات التي رصدها مساعد الذكاء الاصطناعي للعبة:",
                    color = TextGray,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    recChannels.take(3).forEach { chan ->
                        Box(
                            modifier = Modifier
                                .padding(start = 6.dp)
                                .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(text = chan, color = TextWhite, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeminiApiKeyCard(
    savedKey: String,
    onKeySaved: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var tempKey by remember(savedKey) { mutableStateOf(savedKey) }

    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SpaceCardBg)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "Toggle Gemini API Key configuration",
                    tint = NeonGreen,
                    modifier = Modifier.size(24.dp)
                )
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "إعدادات مفتاح الذكاء الاصطناعي (Gemini Key)",
                            color = TextWhite,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Right
                        )
                        Text(
                            text = if (savedKey.isNotEmpty()) "تم حفظ مفتاح التشغيل بنجاح ✔" else "اضغط هنا للصق مفتاح Gemini API وتفعيل الذكاء الاصطناعي",
                            color = if (savedKey.isNotEmpty()) NeonGreen else TextGray,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Right
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Key Icon",
                        tint = if (savedKey.isNotEmpty()) NeonGreen else TextGray,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    text = "لصق مفتاح Gemini API لتمكين المحلل الرياضي الصوتي والمزامنة:",
                    color = TextWhite,
                    fontSize = 11.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Right
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            onKeySaved(tempKey.trim())
                            expanded = false
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonGreen),
                        modifier = Modifier
                            .height(44.dp)
                            .testTag("save_api_key_button"),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Text("حفظ المفتاح", fontSize = 11.sp, color = SpaceDarkBg, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    TextField(
                        value = tempKey,
                        onValueChange = { tempKey = it },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("gemini_api_key_input"),
                        placeholder = {
                            Text(
                                text = "الصق مفتاح API الخاص بك هنا...",
                                fontSize = 11.sp,
                                color = TextGray,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Right
                            )
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = SpaceDarkBg,
                            unfocusedContainerColor = SpaceDarkBg,
                            disabledContainerColor = SpaceDarkBg,
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "ملاحظة: يمكنك الحصول على مفتاح مجاني تماماً من موقع Google AI Studio ولصقه هنا لمتابعة تشغيل وبحث الذكاء الاصطناعي فورا.",
                    color = TextGray,
                    fontSize = 10.sp,
                    lineHeight = 15.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Right
                )
            }
        }
    }
}
