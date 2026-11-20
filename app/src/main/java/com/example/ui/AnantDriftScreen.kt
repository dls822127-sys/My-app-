package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.ElectricBolt
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Star
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.SavedProgress
import com.example.game.GameMode
import com.example.game.GameViewModel
import com.example.game.GliderSkin
import com.example.game.HudSnapshot

@Composable
fun AnantDriftScreen(
    viewModel: GameViewModel
) {
    val gameMode by viewModel.gameMode.collectAsStateWithLifecycle()
    val hud by viewModel.hud.collectAsStateWithLifecycle()
    val progress by viewModel.savedProgress.collectAsStateWithLifecycle()
    val frameTick by viewModel.frameTick.collectAsStateWithLifecycle()

    val equippedSkin = GliderSkin.byId(progress.selectedSkinId)

    // 60 FPS continuous frame loop — runs seamlessly on Title & Playing
    LaunchedEffect(Unit) {
        var lastNanos = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val dt = ((now - lastNanos) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
            lastNanos = now
            viewModel.stepFrame(dt)
        }
    }

    // Back button pauses during play or resumes from pause
    BackHandler(enabled = gameMode == GameMode.PLAYING || gameMode == GameMode.PAUSED) {
        if (gameMode == GameMode.PLAYING) {
            viewModel.pauseGame()
        } else if (gameMode == GameMode.PAUSED) {
            viewModel.resumeGame()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(gameMode) {
                if (gameMode == GameMode.PLAYING) {
                    detectHorizontalDragGestures { _, dragAmount ->
                        if (size.width > 0) {
                            viewModel.onDragSteer(dragAmount / size.width.toFloat())
                        }
                    }
                }
            }
            .pointerInput(gameMode) {
                if (gameMode == GameMode.PLAYING) {
                    detectTapGestures { offset ->
                        viewModel.onTapSide(isRight = offset.x >= size.width * 0.5f)
                    }
                }
            }
    ) {
        // 1. Live 60 FPS Endless Canvas
        GameCanvas(
            engine = viewModel.engine,
            skin = equippedSkin,
            frameTick = frameTick,
            modifier = Modifier.fillMaxSize()
        )

        // 2. Floating Action Banners (Biome Shift, Shield Absorbed, Boost)
        if (gameMode == GameMode.PLAYING) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center)
                    .padding(bottom = 120.dp),
                contentAlignment = Alignment.Center
            ) {
                viewModel.engine.banners.forEach { banner ->
                    Text(
                        text = banner.text,
                        style = MaterialTheme.typography.titleLarge,
                        color = banner.color.copy(alpha = banner.life.coerceIn(0f, 1f)),
                        modifier = Modifier.offset { IntOffset(0, -banner.yOffset.toInt()) }
                    )
                }
            }
        }

        // 3. Playing HUD
        AnimatedVisibility(
            visible = gameMode == GameMode.PLAYING,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            PlayingHud(
                hud = hud,
                soundEnabled = progress.soundEnabled,
                onToggleSound = viewModel::toggleSound,
                onPause = viewModel::pauseGame,
                onToggleAutoPilot = viewModel::toggleAutoPilot,
                onCycleSpeed = viewModel::cycleSpeedTier,
                onCycleCamera = viewModel::cycleCameraView,
                onSelectCarColor = viewModel::selectCarColor,
                onSelectVehicle = viewModel::selectVehicleType
            )
        }

        // 4. Title Overlay
        AnimatedVisibility(
            visible = gameMode == GameMode.TITLE,
            enter = fadeIn() + scaleIn(initialScale = 0.96f),
            exit = fadeOut() + scaleOut(targetScale = 0.96f)
        ) {
            TitleOverlay(
                progress = progress,
                onStart = viewModel::startDrift,
                onOpenHangar = viewModel::openHangar,
                onToggleSound = viewModel::toggleSound
            )
        }

        // 5. Pause Overlay
        AnimatedVisibility(
            visible = gameMode == GameMode.PAUSED,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            PauseOverlay(
                hud = hud,
                onResume = viewModel::resumeGame,
                onOpenHangar = viewModel::openHangar,
                onReturnTitle = viewModel::returnToTitle
            )
        }

        // 6. Craft Hangar Overlay
        AnimatedVisibility(
            visible = gameMode == GameMode.HANGAR,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            HangarOverlay(
                progress = progress,
                onSelectSkin = viewModel::selectOrUnlockSkin,
                onUpgradeStat = viewModel::upgradeStat,
                onClose = viewModel::closeHangar
            )
        }
    }
}

@Composable
private fun PlayingHud(
    hud: HudSnapshot,
    soundEnabled: Boolean,
    onToggleSound: () -> Unit,
    onPause: () -> Unit,
    onToggleAutoPilot: () -> Unit,
    onCycleSpeed: () -> Unit,
    onCycleCamera: () -> Unit,
    onSelectCarColor: (String) -> Unit,
    onSelectVehicle: (com.example.game.VehicleType) -> Unit
) {
    var showColorPalette by remember { mutableStateOf(false) }
    var showVehiclePicker by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Top HUD Bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 640.dp)
                .align(Alignment.TopCenter),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Score & Multiplier Glass Pill
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                    modifier = Modifier.border(
                        1.dp,
                        MaterialTheme.colorScheme.outline,
                        RoundedCornerShape(18.dp)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.label_score),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = hud.score.toString(),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Multiplier Badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (hud.multiplier >= 3) MaterialTheme.colorScheme.secondaryContainer
                                    else MaterialTheme.colorScheme.primaryContainer
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "x${hud.multiplier}",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (hud.multiplier >= 3) MaterialTheme.colorScheme.secondary
                                else MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // Right Controls: Stars + Sound + Pause
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                        modifier = Modifier.border(
                            1.dp,
                            MaterialTheme.colorScheme.outline,
                            RoundedCornerShape(50)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Star,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = hud.runStars.toString(),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }

                    IconButton(
                        onClick = onToggleSound,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                            .testTag("hud_sound_button")
                    ) {
                        Icon(
                            imageVector = if (soundEnabled) Icons.AutoMirrored.Rounded.VolumeUp else Icons.AutoMirrored.Rounded.VolumeOff,
                            contentDescription = stringResource(R.string.cd_toggle_sound),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    IconButton(
                        onClick = onPause,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                            .testTag("hud_pause_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Pause,
                            contentDescription = stringResource(R.string.cd_toggle_pause),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Second Row: Distance + Biome + Shield Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.58f))
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "${hud.distanceMeters}m",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "•",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = hud.biomeName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Shield ready indicator
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (hud.shieldReady) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)
                            else MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)
                        )
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Shield,
                        contentDescription = null,
                        tint = if (hud.shieldReady) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (hud.shieldReady) "SHIELD" else "CHARGING",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (hud.shieldReady) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Active Power-Up Bars
            if (hud.magnetRemainingFrac > 0f) {
                PowerUpBar(
                    label = "MAGNET",
                    icon = Icons.Rounded.ElectricBolt,
                    progress = hud.magnetRemainingFrac,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (hud.boostRemainingFrac > 0f) {
                PowerUpBar(
                    label = "HYPER BOOST",
                    icon = Icons.Rounded.Bolt,
                    progress = hud.boostRemainingFrac,
                    color = Color(0xFFFF2E93)
                )
            }
        }

        // Right-Side Optional Quick Controls: Vehicle Picker, Camera Switcher & Car Color Picker
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 2.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Vehicle Model Switcher Button (Patrol / Thar / Lambo / F1 / Monster Truck)
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                modifier = Modifier
                    .border(1.5.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f), RoundedCornerShape(24.dp))
                    .clip(RoundedCornerShape(24.dp))
                    .clickable {
                        showVehiclePicker = !showVehiclePicker
                        if (showVehiclePicker) showColorPalette = false
                    }
                    .testTag("hud_vehicle_button")
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DirectionsCar,
                        contentDescription = stringResource(R.string.cd_switch_vehicle),
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = hud.vehicleType.shortLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            // Expandable Vehicle Strip (PATROL, THAR, LAMBO, F1, TRUCK)
            AnimatedVisibility(
                visible = showVehiclePicker,
                enter = fadeIn() + scaleIn(initialScale = 0.9f),
                exit = fadeOut() + scaleOut(targetScale = 0.9f)
            ) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    modifier = Modifier.border(
                        1.dp,
                        MaterialTheme.colorScheme.outline,
                        RoundedCornerShape(18.dp)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        com.example.game.VehicleType.entries.forEach { vType ->
                            val isSelected = vType == hud.vehicleType
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                    )
                                    .border(
                                        width = if (isSelected) 1.5.dp else 0.5.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable {
                                        onSelectVehicle(vType)
                                        showVehiclePicker = false
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                                    .testTag("vehicle_opt_${vType.name.lowercase()}")
                            ) {
                                Text(
                                    text = vType.displayName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSelected) MaterialTheme.colorScheme.secondary
                                    else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // Camera Switch Button
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                modifier = Modifier
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp))
                    .clip(RoundedCornerShape(24.dp))
                    .clickable(onClick = onCycleCamera)
                    .testTag("hud_camera_button")
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Videocam,
                        contentDescription = stringResource(R.string.cd_camera_view),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = hud.cameraView.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Car Color Palette Toggle Button
            val selectedOpt = com.example.game.CarColorOption.byId(hud.carColorId)
            IconButton(
                onClick = {
                    showColorPalette = !showColorPalette
                    if (showColorPalette) showVehiclePicker = false
                },
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.82f))
                    .border(
                        width = 2.dp,
                        color = if (selectedOpt.isRainbow) Color(0xFFFF2E93) else selectedOpt.primary,
                        shape = CircleShape
                    )
                    .testTag("hud_color_toggle_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Palette,
                    contentDescription = stringResource(R.string.cd_car_color),
                    tint = if (selectedOpt.isRainbow) Color(0xFFFFD54F) else selectedOpt.primary,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Expandable Side Color Strip (All Colors Rainbow + 7 vibrant colors)
            AnimatedVisibility(
                visible = showColorPalette,
                enter = fadeIn() + scaleIn(initialScale = 0.9f),
                exit = fadeOut() + scaleOut(targetScale = 0.9f)
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                    modifier = Modifier.border(
                        1.dp,
                        MaterialTheme.colorScheme.outline,
                        RoundedCornerShape(24.dp)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        com.example.game.CarColorOption.ALL.forEach { opt ->
                            val isSelected = opt.id == hud.carColorId
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (opt.isRainbow) {
                                            Brush.sweepGradient(
                                                listOf(
                                                    Color(0xFFFF1744),
                                                    Color(0xFFFFC400),
                                                    Color(0xFF00E676),
                                                    Color(0xFF00E5FF),
                                                    Color(0xFFFF2E93),
                                                    Color(0xFFFF1744)
                                                )
                                            )
                                        } else {
                                            Brush.linearGradient(listOf(opt.primary, opt.accent))
                                        }
                                    )
                                    .border(
                                        width = if (isSelected) 2.5.dp else 1.dp,
                                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.3f),
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        onSelectCarColor(opt.id)
                                        showColorPalette = false
                                    }
                                    .testTag("car_color_${opt.id}")
                            )
                        }
                    }
                }
            }
        }

        // Bottom Non-Stop Quick Controls (Auto-Pilot & Speed Tier)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp)
                .align(Alignment.BottomCenter),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (hud.autoPilotActive) {
                Text(
                    text = stringResource(R.string.label_autopilot_active),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.65f))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Auto-Pilot Toggle Pill
                Button(
                    onClick = onToggleAutoPilot,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (hud.autoPilotActive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                        contentColor = if (hud.autoPilotActive) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurface
                    ),
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .border(
                            1.dp,
                            if (hud.autoPilotActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            RoundedCornerShape(50)
                        )
                        .testTag("hud_autopilot_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SmartToy,
                        contentDescription = stringResource(R.string.cd_toggle_autopilot),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (hud.autoPilotActive) stringResource(R.string.btn_autopilot_on)
                        else stringResource(R.string.btn_autopilot_off),
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                // Speed Tier Cycle Pill
                Button(
                    onClick = onCycleSpeed,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                        contentColor = MaterialTheme.colorScheme.secondary
                    ),
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(50))
                        .testTag("hud_speed_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Speed,
                        contentDescription = stringResource(R.string.cd_speed_mode),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = hud.speedTier.label,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun PowerUpBar(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    progress: Float,
    color: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = color)
        LinearProgressIndicator(
            progress = { progress },
            color = color,
            trackColor = color.copy(alpha = 0.2f),
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
        )
    }
}

@Composable
private fun TitleOverlay(
    progress: SavedProgress,
    onStart: () -> Unit,
    onOpenHangar: () -> Unit,
    onToggleSound: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background.copy(alpha = 0.55f),
                        MaterialTheme.colorScheme.background.copy(alpha = 0.35f),
                        MaterialTheme.colorScheme.background.copy(alpha = 0.85f)
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(20.dp)
    ) {
        // Top row: Offline Badge + Sound button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.82f))
                    .border(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f), RoundedCornerShape(50))
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.WifiOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.label_offline_badge),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }

            IconButton(
                onClick = onToggleSound,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.82f))
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    .testTag("title_sound_button")
            ) {
                Icon(
                    imageVector = if (progress.soundEnabled) Icons.AutoMirrored.Rounded.VolumeUp else Icons.AutoMirrored.Rounded.VolumeOff,
                    contentDescription = stringResource(R.string.cd_toggle_sound),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Center Title Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 460.dp)
                .align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Generated Hero Banner Artwork
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(22.dp))
                    .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f), RoundedCornerShape(22.dp))
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_anant_banner_1794978810788),
                    contentDescription = stringResource(R.string.cd_hero_banner),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, MaterialTheme.colorScheme.background.copy(alpha = 0.82f))
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.AllInclusive,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.tagline_nonstop),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = stringResource(R.string.app_name).uppercase(),
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Lifetime Offline Stats Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatChip(
                    label = stringResource(R.string.stat_best_run),
                    value = progress.bestScore.toString(),
                    accent = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                StatChip(
                    label = stringResource(R.string.label_stars),
                    value = progress.totalStars.toString(),
                    accent = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f)
                )
                StatChip(
                    label = stringResource(R.string.stat_total_distance),
                    value = "${progress.totalDistanceMeters}m",
                    accent = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(22.dp))

            // Primary CTA: Start Drift
            Button(
                onClick = onStart,
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .testTag("start_drift_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.btn_start_drift),
                    style = MaterialTheme.typography.titleLarge
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Secondary CTA: Hangar
            OutlinedButton(
                onClick = onOpenHangar,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("open_hangar_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.RocketLaunch,
                    contentDescription = stringResource(R.string.cd_open_hangar),
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.btn_hangar),
                    style = MaterialTheme.typography.labelLarge
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = stringResource(R.string.label_nonstop_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun StatChip(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
        modifier = modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = accent
            )
        }
    }
}

@Composable
private fun PauseOverlay(
    hud: HudSnapshot,
    onResume: () -> Unit,
    onOpenHangar: () -> Unit,
    onReturnTitle: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.78f))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 400.dp)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.title_paused),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.subtitle_paused),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatChip(
                        label = stringResource(R.string.label_score),
                        value = hud.score.toString(),
                        accent = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    StatChip(
                        label = stringResource(R.string.label_distance),
                        value = "${hud.distanceMeters}m",
                        accent = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onResume,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("resume_game_button")
                ) {
                    Icon(imageVector = Icons.Rounded.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.btn_resume),
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onOpenHangar,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("pause_hangar_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.RocketLaunch,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.btn_hangar),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }

                    OutlinedButton(
                        onClick = onReturnTitle,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("pause_title_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Home,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.btn_home),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}
