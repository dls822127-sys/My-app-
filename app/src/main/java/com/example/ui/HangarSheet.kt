package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ElectricBolt
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.SavedProgress
import com.example.game.GliderSkin

@Composable
fun HangarOverlay(
    progress: SavedProgress,
    onSelectSkin: (GliderSkin) -> Unit,
    onUpgradeStat: (String, Int) -> Unit,
    onClose: () -> Unit
) {
    BackHandler(onBack = onClose)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.88f))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp))
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.RocketLaunch,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.title_hangar),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Star Balance Pill
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(MaterialTheme.colorScheme.secondaryContainer)
                                .padding(horizontal = 12.dp, vertical = 6.dp),
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
                                text = progress.totalStars.toString(),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("close_hangar_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.cd_close),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.section_gliders),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )

                    GliderSkin.ALL.forEach { skin ->
                        val unlocked = skin.id in progress.unlockedSkins
                        val equipped = progress.selectedSkinId == skin.id
                        val canAfford = progress.totalStars >= skin.cost

                        SkinCard(
                            skin = skin,
                            unlocked = unlocked,
                            equipped = equipped,
                            canAfford = canAfford,
                            onAction = { onSelectSkin(skin) }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = stringResource(R.string.section_upgrades),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )

                    UpgradeCard(
                        title = "Magnet Pulse",
                        subtitle = "Longer duration & wider star pull",
                        icon = Icons.Rounded.ElectricBolt,
                        accent = MaterialTheme.colorScheme.primary,
                        level = progress.magnetLevel,
                        cost = progress.magnetLevel * 45,
                        canAfford = progress.totalStars >= progress.magnetLevel * 45,
                        tag = "upgrade_magnet_button",
                        onUpgrade = { onUpgradeStat("magnet", progress.magnetLevel * 45) }
                    )

                    UpgradeCard(
                        title = "Hyper Boost",
                        subtitle = "Extended smash speed surge",
                        icon = Icons.Rounded.Bolt,
                        accent = Color(0xFFFF2E93),
                        level = progress.boostLevel,
                        cost = progress.boostLevel * 50,
                        canAfford = progress.totalStars >= progress.boostLevel * 50,
                        tag = "upgrade_boost_button",
                        onUpgrade = { onUpgradeStat("boost", progress.boostLevel * 50) }
                    )

                    UpgradeCard(
                        title = "Score Multiplier",
                        subtitle = "Permanent +1x base flow bonus",
                        icon = Icons.Rounded.AutoAwesome,
                        accent = MaterialTheme.colorScheme.secondary,
                        level = progress.multiplierLevel,
                        cost = progress.multiplierLevel * 65,
                        canAfford = progress.totalStars >= progress.multiplierLevel * 65,
                        tag = "upgrade_multiplier_button",
                        onUpgrade = { onUpgradeStat("multiplier", progress.multiplierLevel * 65) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SkinCard(
    skin: GliderSkin,
    unlocked: Boolean,
    equipped: Boolean,
    canAfford: Boolean,
    onAction: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (equipped) 1.5.dp else 1.dp,
                color = if (equipped) skin.bodyPrimary else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(16.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(skin.bodyPrimary, skin.bodyAccent)
                            )
                        )
                        .border(2.dp, skin.cockpitColor, CircleShape)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = skin.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = skin.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            when {
                equipped -> {
                    OutlinedButton(
                        onClick = {},
                        enabled = false,
                        modifier = Modifier.testTag("skin_btn_${skin.id}")
                    ) {
                        Text(
                            text = stringResource(R.string.btn_equipped),
                            style = MaterialTheme.typography.labelSmall,
                            color = skin.bodyPrimary
                        )
                    }
                }
                unlocked -> {
                    Button(
                        onClick = onAction,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.testTag("skin_btn_${skin.id}")
                    ) {
                        Text(
                            text = stringResource(R.string.btn_equip),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                else -> {
                    Button(
                        onClick = onAction,
                        enabled = canAfford,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        ),
                        modifier = Modifier.testTag("skin_btn_${skin.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Star,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = skin.cost.toString(),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UpgradeCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    level: Int,
    cost: Int,
    canAfford: Boolean,
    tag: String,
    onUpgrade: () -> Unit
) {
    val isMax = level >= 5
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(accent.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (pip in 1..5) {
                            Box(
                                modifier = Modifier
                                    .width(20.dp)
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(
                                        if (pip <= level) accent else MaterialTheme.colorScheme.outline
                                    )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Button(
                onClick = onUpgrade,
                enabled = !isMax && canAfford,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                ),
                modifier = Modifier.testTag(tag)
            ) {
                if (isMax) {
                    Text(
                        text = stringResource(R.string.btn_maxed),
                        style = MaterialTheme.typography.labelSmall
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Star,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = cost.toString(),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}
