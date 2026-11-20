package com.example.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.example.game.AnantDriftEngine
import com.example.game.BiomePalette
import com.example.game.GliderSkin
import com.example.game.TrackObject
import com.example.game.TrackObjectType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

@Composable
fun GameCanvas(
    engine: AnantDriftEngine,
    skin: GliderSkin,
    frameTick: Long,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        // Consume frameTick so Compose redraws every frame
        @Suppress("UNUSED_EXPRESSION")
        frameTick

        val w = size.width
        val h = size.height
        val biome = engine.currentBiome

        val shakeX = if (engine.shakeTimer > 0f) {
            sin(engine.worldTime * 75f) * 14f * (engine.shakeTimer / 0.28f)
        } else 0f
        val shakeY = if (engine.shakeTimer > 0f) {
            cos(engine.worldTime * 62f) * 9f * (engine.shakeTimer / 0.28f)
        } else 0f

        val horizonY = h * engine.camHorizonFrac + shakeY
        val centerX = w * 0.5f + shakeX
        val roadMul = engine.camRoadMul

        // Resolve active car color (supports Rainbow All-Colors mode + custom colors)
        val effectiveSkin = resolveCarColorSkin(skin, engine.carColorId, engine.worldTime)

        // 1. Sky Gradient
        drawRect(
            brush = Brush.verticalGradient(
                colorStops = arrayOf(
                    0.0f to biome.skyTop,
                    0.58f to biome.skyMid,
                    1.0f to biome.skyHorizon
                ),
                startY = 0f,
                endY = horizonY + h * 0.08f
            ),
            size = Size(w, horizonY + h * 0.08f)
        )

        // 2. Stars at night, soft realistic drifting clouds during daytime
        if (biome.isNight) {
            for (star in engine.bgStars) {
                val sx = star[0] * w
                val sy = star[1] * h
                val baseR = star[2]
                val twinkle = 0.45f + 0.55f * sin(engine.worldTime * 2.4f + star[3])
                drawCircle(
                    color = Color.White.copy(alpha = twinkle * 0.85f),
                    radius = baseR,
                    center = Offset(sx, sy)
                )
            }
        } else {
            // Soft realistic daytime clouds drifting gently across the sky
            for (c in 0..4) {
                val cx = ((c * 0.24f + engine.worldTime * (0.006f + c * 0.0015f)) % 1.2f - 0.1f) * w
                val cy = horizonY * (0.22f + (c % 3) * 0.19f)
                val cw = w * (0.14f + (c % 2) * 0.06f)
                val ch = cw * 0.28f
                drawOval(
                    color = Color.White.copy(alpha = 0.36f),
                    topLeft = Offset(cx - cw * 0.5f, cy - ch * 0.5f),
                    size = Size(cw, ch)
                )
                drawOval(
                    color = Color.White.copy(alpha = 0.45f),
                    topLeft = Offset(cx - cw * 0.3f, cy - ch * 0.82f),
                    size = Size(cw * 0.62f, ch * 0.9f)
                )
            }
        }

        // 3. Horizon Glowing Sun / Orb
        val sunRadius = (w.coerceAtMost(h)) * 0.16f
        val sunCenter = Offset(centerX, horizonY - sunRadius * 0.18f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    biome.sunColor.copy(alpha = 0.55f),
                    biome.sunColor.copy(alpha = 0.14f),
                    Color.Transparent
                ),
                center = sunCenter,
                radius = sunRadius * 2.4f
            ),
            radius = sunRadius * 2.4f,
            center = sunCenter
        )
        drawCircle(
            brush = Brush.verticalGradient(
                colors = listOf(Color.White.copy(alpha = 0.9f), biome.sunColor),
                startY = sunCenter.y - sunRadius,
                endY = sunCenter.y + sunRadius
            ),
            radius = sunRadius,
            center = sunCenter
        )

        // 4. Dense Residential City & Houses Skyline All Across the Horizon (New Look!)
        drawCitySkyline(
            w = w,
            horizonY = horizonY,
            maxHeight = h * 0.16f,
            phase = engine.distanceFloat * 0.0010f,
            buildingColor = biome.mountainFar,
            isNight = biome.isNight,
            buildings = 14
        )
        drawCitySkyline(
            w = w,
            horizonY = horizonY + h * 0.012f,
            maxHeight = h * 0.105f,
            phase = engine.distanceFloat * 0.0022f + 1.8f,
            buildingColor = biome.mountainNear,
            isNight = biome.isNight,
            buildings = 18
        )

        // 5. Endless 3D Perspective Real Highway (with houses, people, trees, streetlights)
        drawHighway(
            w = w,
            h = h,
            centerX = centerX,
            horizonY = horizonY,
            scroll = engine.trackScroll,
            biome = biome,
            isBoosting = engine.boostTimer > 0f,
            roadMul = roadMul,
            worldTime = engine.worldTime
        )

        // 6. Track Objects (sorted far -> near for proper depth layering)
        val sortedObjects = engine.objects.sortedByDescending { it.z }
        for (obj in sortedObjects) {
            if (!obj.active || obj.z > 1.02f || obj.z < -0.05f) continue
            drawTrackObject(
                obj = obj,
                w = w,
                h = h,
                centerX = centerX,
                horizonY = horizonY,
                worldTime = engine.worldTime,
                biome = biome,
                roadMul = roadMul
            )
        }

        // 7. Player Ribbon Trail & Car
        val carZ = engine.camCarZ
        val playerScreenY = projectY(carZ, horizonY, h)
        val playerScreenX = projectX(engine.playerX, carZ, centerX, w, roadMul)

        drawGliderTrail(
            engine = engine,
            skin = effectiveSkin,
            w = w,
            h = h,
            centerX = centerX,
            horizonY = horizonY,
            roadMul = roadMul,
            carZ = carZ
        )

        // Magnet field aura
        if (engine.magnetTimer > 0f) {
            val pulse = 1f + 0.12f * sin(engine.worldTime * 10f)
            drawCircle(
                color = Color(0xFF00E5FF).copy(alpha = 0.25f),
                radius = w * 0.15f * pulse,
                center = Offset(playerScreenX, playerScreenY),
                style = Stroke(width = 3.5f)
            )
        }

        // Shield aura or hit flash
        if (engine.shieldFlashTimer > 0f) {
            val alpha = (engine.shieldFlashTimer / 0.45f).coerceIn(0f, 1f)
            drawCircle(
                color = Color(0xFFFFAB40).copy(alpha = alpha * 0.65f),
                radius = w * 0.16f * (1.25f - alpha * 0.3f),
                center = Offset(playerScreenX, playerScreenY),
                style = Stroke(width = 7f)
            )
        } else if (engine.shieldReady) {
            drawCircle(
                color = Color(0xFF00E676).copy(alpha = 0.22f),
                radius = w * 0.105f,
                center = Offset(playerScreenX, playerScreenY),
                style = Stroke(width = 2.5f)
            )
        }

        // Draw Vehicle (blinks softly during invulnerability)
        val blinkVisible = engine.invulnTimer <= 0f || ((engine.worldTime * 18f).toInt() % 2 == 0)
        if (blinkVisible) {
            drawGlider(
                center = Offset(playerScreenX, playerScreenY),
                scale = (w.coerceAtMost(h) * 0.132f) * engine.camScaleMul,
                bank = engine.playerBank,
                skin = effectiveSkin,
                vehicleType = engine.vehicleType,
                isBoosting = engine.boostTimer > 0f,
                worldTime = engine.worldTime
            )
        }

        // 8. Burst Particles
        for (p in engine.particles) {
            drawCircle(
                color = p.color.copy(alpha = p.life.coerceIn(0f, 1f)),
                radius = p.size * p.life.coerceIn(0.2f, 1f),
                center = Offset(p.x * w, p.y * h)
            )
        }

        // 9. Hyper-Boost Edge Speed Lines
        if (engine.boostTimer > 0f) {
            val lineAlpha = 0.28f + 0.15f * sin(engine.worldTime * 22f)
            for (i in 0..7) {
                val frac = ((i * 0.17f + engine.worldTime * 2.5f) % 1f)
                val side = if (i % 2 == 0) -1f else 1f
                val start = Offset(centerX + side * w * (0.28f + frac * 0.22f), horizonY + h * frac * 0.35f)
                val end = Offset(centerX + side * w * (0.38f + frac * 0.32f), horizonY + h * (frac + 0.14f) * 0.45f)
                drawLine(
                    color = Color(0xFFFF2E93).copy(alpha = lineAlpha),
                    start = start,
                    end = end,
                    strokeWidth = 3f,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

private fun resolveCarColorSkin(base: GliderSkin, colorId: String, worldTime: Float): GliderSkin {
    val opt = com.example.game.CarColorOption.byId(colorId)
    return if (opt.isRainbow) {
        val hue = (worldTime * 95f) % 360f
        val primary = Color.hsv(hue, 0.92f, 1.0f)
        val accent = Color.hsv((hue + 40f) % 360f, 0.88f, 0.42f)
        base.copy(
            bodyPrimary = primary,
            bodyAccent = accent,
            trailColor = primary
        )
    } else {
        base.copy(
            bodyPrimary = opt.primary,
            bodyAccent = opt.accent,
            trailColor = opt.primary
        )
    }
}

private fun perspectiveFactor(z: Float): Float {
    // z = 1.0 at horizon -> 0.0 at bottom
    val clamped = (1f - z).coerceIn(0.01f, 1.25f)
    return clamped.pow(1.85f)
}

private fun projectY(z: Float, horizonY: Float, totalH: Float): Float {
    val p = perspectiveFactor(z)
    return horizonY + p * (totalH - horizonY)
}

private fun projectX(laneX: Float, z: Float, centerX: Float, totalW: Float, roadMul: Float = 1f): Float {
    val p = perspectiveFactor(z)
    val roadHalfWidth = totalW * (0.045f + p * 0.47f) * roadMul
    return centerX + laneX * roadHalfWidth
}

private fun DrawScope.drawCitySkyline(
    w: Float,
    horizonY: Float,
    maxHeight: Float,
    phase: Float,
    buildingColor: Color,
    isNight: Boolean,
    buildings: Int
) {
    val slotW = w / buildings
    val windowColor = if (isNight) Color(0xFFFFDF7E).copy(alpha = 0.85f) else Color(0xFFD6ECFF).copy(alpha = 0.42f)
    for (i in 0 until buildings) {
        val seed = sin(i * 2.13f + phase) * 0.5f + 0.5f
        val bh = maxHeight * (0.38f + seed * 0.62f)
        val bw = slotW * 0.94f
        val left = i * slotW + slotW * 0.03f
        val top = horizonY - bh

        // Building silhouette
        drawRect(
            color = buildingColor,
            topLeft = Offset(left, top),
            size = Size(bw, bh + 10f)
        )
        // Pitched roof on every 3rd horizon house
        if (i % 3 == 0) {
            val roof = Path().apply {
                moveTo(left - 2f, top + 1f)
                lineTo(left + bw * 0.5f, top - bh * 0.22f)
                lineTo(left + bw + 2f, top + 1f)
                close()
            }
            drawPath(path = roof, color = lerpColor(buildingColor, Color(0xFF9E3A2E), 0.35f))
        }
        // Subtle sunlit/moonlit top edge
        drawLine(
            color = Color.White.copy(alpha = if (isNight) 0.12f else 0.22f),
            start = Offset(left, top),
            end = Offset(left + bw, top),
            strokeWidth = 1.5f
        )
        // Tiny glowing residential windows grid
        val cols = 3
        val rows = ((bh / (maxHeight * 0.18f)).toInt()).coerceIn(2, 5)
        val winW = (bw / (cols * 2.2f)).coerceAtLeast(2f)
        val winH = (bh / (rows * 2.6f)).coerceAtLeast(2f)
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                if ((i + r * 3 + c) % 4 == 0) continue
                val wx = left + bw * (0.16f + c * 0.28f)
                val wy = top + bh * (0.14f + r * (0.76f / rows))
                drawRect(
                    color = windowColor,
                    topLeft = Offset(wx, wy),
                    size = Size(winW, winH)
                )
            }
        }
    }
}

private fun DrawScope.drawHighway(
    w: Float,
    h: Float,
    centerX: Float,
    horizonY: Float,
    scroll: Float,
    biome: BiomePalette,
    isBoosting: Boolean,
    roadMul: Float,
    worldTime: Float
) {
    // 1. Modern Paved Neighborhood Ground & City Sidewalk Backdrop
    val groundNear = if (biome.isNight) Color(0xFF1A202C) else Color(0xFF8B8E94)
    val groundFar = if (biome.isNight) Color(0xFF111622) else Color(0xFF6B7280)
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(groundFar, groundNear),
            startY = horizonY,
            endY = h
        ),
        topLeft = Offset(0f, horizonY),
        size = Size(w, h - horizonY)
    )

    // Sidewalk concrete slab bands scrolling past for crisp urban speed perception
    val numBands = 12
    for (i in 0 until numBands) {
        val zFar = ((i.toFloat() + (1f - scroll)) / numBands).coerceIn(0.0f, 1.0f)
        val zNear = ((i.toFloat() - 0.45f + (1f - scroll)) / numBands).coerceIn(0.0f, 1.0f)
        if (zFar > zNear) {
            val yTop = projectY(zFar, horizonY, h)
            val yBot = projectY(zNear, horizonY, h)
            drawRect(
                color = Color.Black.copy(alpha = 0.09f),
                topLeft = Offset(0f, yTop),
                size = Size(w, (yBot - yTop).coerceAtLeast(1f))
            )
        }
    }

    // 2. Raised City Sidewalk / Footpath along both edges of the road
    val shoulderTopL = projectX(-1.36f, 0.99f, centerX, w, roadMul)
    val shoulderTopR = projectX(1.36f, 0.99f, centerX, w, roadMul)
    val shoulderBotL = projectX(-1.36f, 0.0f, centerX, w, roadMul)
    val shoulderBotR = projectX(1.36f, 0.0f, centerX, w, roadMul)
    val shoulderPath = Path().apply {
        moveTo(shoulderTopL, horizonY)
        lineTo(shoulderTopR, horizonY)
        lineTo(shoulderBotR, h)
        lineTo(shoulderBotL, h)
        close()
    }
    drawPath(
        path = shoulderPath,
        color = if (biome.isNight) Color(0xFF283040) else Color(0xFFB8BCC4)
    )

    // 3. Real Asphalt Highway Trapezoid
    val topLeftX = projectX(-1.05f, 0.99f, centerX, w, roadMul)
    val topRightX = projectX(1.05f, 0.99f, centerX, w, roadMul)
    val botLeftX = projectX(-1.05f, 0.0f, centerX, w, roadMul)
    val botRightX = projectX(1.05f, 0.0f, centerX, w, roadMul)

    val roadPath = Path().apply {
        moveTo(topLeftX, horizonY)
        lineTo(topRightX, horizonY)
        lineTo(botRightX, h)
        lineTo(botLeftX, h)
        close()
    }
    drawPath(
        path = roadPath,
        brush = Brush.verticalGradient(
            colors = listOf(
                lerpColor(biome.trackBase, Color(0xFF4B525E), 0.28f),
                biome.trackBase,
                lerpColor(biome.trackBase, Color.Black, 0.25f)
            ),
            startY = horizonY,
            endY = h
        )
    )

    // Subtle darker tire-wear tracks in the 3 lanes for authentic road realism
    for (laneCenter in floatArrayOf(-0.68f, 0f, 0.68f)) {
        val lanePath = Path().apply {
            moveTo(projectX(laneCenter - 0.14f, 0.99f, centerX, w, roadMul), horizonY)
            lineTo(projectX(laneCenter + 0.14f, 0.99f, centerX, w, roadMul), horizonY)
            lineTo(projectX(laneCenter + 0.14f, 0.0f, centerX, w, roadMul), h)
            lineTo(projectX(laneCenter - 0.14f, 0.0f, centerX, w, roadMul), h)
            close()
        }
        drawPath(path = lanePath, color = Color.Black.copy(alpha = 0.14f))
    }

    // 4. Real Dashed White Highway Lane Markings (between the 3 lanes)
    val dashCount = 12
    for (i in 0 until dashCount) {
        val zStart = ((i.toFloat() + (1f - scroll)) / dashCount).coerceIn(0.01f, 0.99f)
        val zEnd = ((i.toFloat() + 0.48f + (1f - scroll)) / dashCount).coerceIn(0.01f, 0.99f)
        if (zEnd <= zStart) continue
        val y1 = projectY(zStart, horizonY, h)
        val y2 = projectY(zEnd, horizonY, h)
        val dashWidth = (2.2f + (1f - zStart) * 6.5f)
        val dashColor = if (isBoosting) Color(0xFFFFE082) else Color(0xFFF1F5F9).copy(alpha = 0.88f)
        for (laneDivider in floatArrayOf(-0.34f, 0.34f)) {
            drawLine(
                color = dashColor,
                start = Offset(projectX(laneDivider, zStart, centerX, w, roadMul), y1),
                end = Offset(projectX(laneDivider, zEnd, centerX, w, roadMul), y2),
                strokeWidth = dashWidth,
                cap = StrokeCap.Butt
            )
        }
    }

    // 5. Alternating Red/Yellow & White Painted Highway Curbs along both edges
    val curbSegments = 14
    for (i in 0 until curbSegments) {
        val zNear = ((i.toFloat() + (1f - scroll)) / curbSegments).coerceIn(0.0f, 0.99f)
        val zFar = ((i.toFloat() + 0.98f + (1f - scroll)) / curbSegments).coerceIn(0.0f, 0.99f)
        if (zFar <= zNear) continue
        val yNear = projectY(zNear, horizonY, h)
        val yFar = projectY(zFar, horizonY, h)
        val curbColor = if (i % 2 == 0) biome.railGlow else Color(0xFFF1F5F9)
        val curbW = 3.5f + (1f - zNear) * 9f

        // Left curb
        drawLine(
            color = curbColor,
            start = Offset(projectX(-1.06f, zNear, centerX, w, roadMul), yNear),
            end = Offset(projectX(-1.06f, zFar, centerX, w, roadMul), yFar),
            strokeWidth = curbW
        )
        // Right curb
        drawLine(
            color = curbColor,
            start = Offset(projectX(1.06f, zNear, centerX, w, roadMul), yNear),
            end = Offset(projectX(1.06f, zFar, centerX, w, roadMul), yFar),
            strokeWidth = curbW
        )
    }

    // Crisp solid white inner road edge lines
    drawLine(
        color = Color.White.copy(alpha = 0.78f),
        start = Offset(projectX(-0.98f, 0.99f, centerX, w, roadMul), horizonY),
        end = Offset(projectX(-0.98f, 0.0f, centerX, w, roadMul), h),
        strokeWidth = 3.5f
    )
    drawLine(
        color = Color.White.copy(alpha = 0.78f),
        start = Offset(projectX(0.98f, 0.99f, centerX, w, roadMul), horizonY),
        end = Offset(projectX(0.98f, 0.0f, centerX, w, roadMul), h),
        strokeWidth = 3.5f
    )

    // 6. Dense Multi-Row Houses Everywhere (Back Apartments + Front Homes + Awnings + People + Streetlights)
    val propCount = 10
    for (i in propCount - 1 downTo 0) {
        val z = ((i.toFloat() + (1f - scroll)) / propCount).coerceIn(0.02f, 0.96f)
        val p = perspectiveFactor(z)
        val py = projectY(z, horizonY, h)
        val propScale = (w.coerceAtMost(h)) * (0.015f + p * 0.105f)

        for (side in floatArrayOf(-1f, 1f)) {
            val backHouseX = projectX(side * 2.48f, z, centerX, w, roadMul)
            val frontHouseX = projectX(side * 1.66f, z, centerX, w, roadMul)
            val personX = projectX(side * 1.28f, z, centerX, w, roadMul)
            val poleX = projectX(side * 1.12f, z, centerX, w, roadMul)

            // Outer Row: Tall Apartment / Villa behind front houses so houses fill the entire screen!
            drawRoadsideHouse(
                cx = backHouseX,
                groundY = py,
                scale = propScale * 1.18f,
                variant = (i * 2 + (if (side > 0f) 1 else 3)) % 6,
                isTwoStory = true,
                hasAwning = false,
                isNight = biome.isNight
            )

            // Inner Row: Front Roadside House / Cozy Bungalow / Modern Shop-Home
            val isTwoStory = (i + (if (side > 0f) 1 else 0)) % 2 == 0
            drawRoadsideHouse(
                cx = frontHouseX,
                groundY = py,
                scale = propScale,
                variant = (i + (if (side > 0f) 2 else 0)) % 6,
                isTwoStory = isTwoStory,
                hasAwning = (i % 3 == 0),
                isNight = biome.isNight
            )

            // Person standing / waving on the sidewalk outside the houses
            if (i % 2 == 0) {
                drawRoadsidePerson(
                    cx = personX,
                    groundY = py + propScale * 0.04f,
                    scale = propScale,
                    variant = (i * 3 + (if (side > 0f) 1 else 0)) % 5,
                    worldTime = worldTime,
                    isNight = biome.isNight
                )
            }

            // Modern Streetlight Pole every alternate slot
            if (i % 2 == 1) {
                val poleH = propScale * 1.90f
                drawLine(
                    color = Color(0xFF475569),
                    start = Offset(poleX, py),
                    end = Offset(poleX, py - poleH),
                    strokeWidth = (propScale * 0.07f).coerceAtLeast(1.5f)
                )
                val armEnd = Offset(poleX - side * propScale * 0.38f, py - poleH)
                drawLine(
                    color = Color(0xFF64748B),
                    start = Offset(poleX, py - poleH),
                    end = armEnd,
                    strokeWidth = (propScale * 0.06f).coerceAtLeast(1.5f)
                )
                drawCircle(
                    color = if (biome.isNight) Color(0xFFFFE599) else Color(0xFFE2E8F0),
                    radius = (propScale * 0.09f).coerceAtLeast(2f),
                    center = armEnd
                )
                if (biome.isNight) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(Color(0x66FFE082), Color.Transparent),
                            center = armEnd,
                            radius = propScale * 0.88f
                        ),
                        radius = propScale * 0.88f,
                        center = armEnd
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawRoadsideHouse(
    cx: Float,
    groundY: Float,
    scale: Float,
    variant: Int,
    isTwoStory: Boolean,
    hasAwning: Boolean,
    isNight: Boolean
) {
    val hw = scale * (if (isTwoStory) 1.62f else 1.72f)
    val hh = scale * (if (isTwoStory) 2.05f else 1.32f)
    val left = cx - hw * 0.5f
    val top = groundY - hh

    // Fresh vibrant new-look architectural colors
    val dayWall = when (variant) {
        0 -> Color(0xFFFDF0DC) // Warm Cream
        1 -> Color(0xFFF3C5A8) // Terracotta Peach
        2 -> Color(0xFFD5E9F6) // Pastel Sky Blue
        3 -> Color(0xFFD8F0DF) // Fresh Mint Cream
        4 -> Color(0xFFF7E4A8) // Sunlit Ochre
        else -> Color(0xFFEBD9F2) // Soft Lavender White
    }
    val wallColor = if (isNight) lerpColor(dayWall, Color(0xFF182235), 0.72f) else dayWall
    val sideWallShade = lerpColor(wallColor, Color.Black, 0.16f)

    // Ground contact shadow
    drawRect(
        color = Color.Black.copy(alpha = 0.28f),
        topLeft = Offset(left - hw * 0.06f, groundY - scale * 0.07f),
        size = Size(hw * 1.12f, scale * 0.13f)
    )

    // Main House Wall
    drawRect(
        brush = Brush.horizontalGradient(
            colors = listOf(wallColor, sideWallShade),
            startX = left,
            endX = left + hw
        ),
        topLeft = Offset(left, top),
        size = Size(hw, hh)
    )
    drawRect(
        color = Color.Black.copy(alpha = 0.20f),
        topLeft = Offset(left, top),
        size = Size(hw, hh),
        style = Stroke(width = 1.5f)
    )

    // Roof (Colorful tiled gable roof for bungalow, sleek parapet terrace for 2-story home)
    if (!isTwoStory) {
        val roofH = scale * 0.70f
        val roofDay = when (variant % 3) {
            0 -> Color(0xFFC0392B) // Terracotta Red
            1 -> Color(0xFF2B6CB0) // Modern Slate Blue
            else -> Color(0xFFD97706) // Warm Clay Amber
        }
        val roofColor = if (isNight) lerpColor(roofDay, Color(0xFF141824), 0.65f) else roofDay
        val roofPath = Path().apply {
            moveTo(left - hw * 0.12f, top + 2f)
            lineTo(cx, top - roofH)
            lineTo(left + hw * 1.12f, top + 2f)
            close()
        }
        drawPath(path = roofPath, color = roofColor)
        drawPath(path = roofPath, color = Color.Black.copy(alpha = 0.25f), style = Stroke(width = 1.5f))
    } else {
        // Modern flat roof parapet & balcony railing
        val trimColor = if (isNight) Color(0xFF2B3850) else Color(0xFF64748B)
        drawRect(
            color = trimColor,
            topLeft = Offset(left - hw * 0.05f, top - scale * 0.12f),
            size = Size(hw * 1.10f, scale * 0.15f)
        )
        drawRect(
            color = trimColor,
            topLeft = Offset(left - hw * 0.04f, top + hh * 0.48f),
            size = Size(hw * 1.08f, scale * 0.10f)
        )
    }

    // Optional Colorful Striped Porch/Shop Awning for a lively New Look!
    if (hasAwning) {
        val awnY = groundY - hh * (if (isTwoStory) 0.44f else 0.48f)
        val awnH = scale * 0.20f
        val awnColor = if (variant % 2 == 0) Color(0xFFE53935) else Color(0xFF1E88E5)
        drawRect(
            color = if (isNight) lerpColor(awnColor, Color.Black, 0.5f) else awnColor,
            topLeft = Offset(left - hw * 0.03f, awnY),
            size = Size(hw * 1.06f, awnH)
        )
        // White awning stripes
        for (st in 0..2) {
            drawRect(
                color = Color.White.copy(alpha = if (isNight) 0.5f else 0.85f),
                topLeft = Offset(left + hw * (0.14f + st * 0.30f), awnY),
                size = Size(hw * 0.12f, awnH)
            )
        }
    }

    // Wooden Front Door
    val doorW = hw * 0.22f
    val doorH = hh * (if (isTwoStory) 0.25f else 0.40f)
    drawRect(
        color = if (isNight) Color(0xFF2B1B12) else Color(0xFF6B3E26),
        topLeft = Offset(cx - doorW * 0.5f, groundY - doorH),
        size = Size(doorW, doorH)
    )

    // Windows (Cool sky glass by day, warm golden-yellow indoor light at night!)
    val winColor = if (isNight) Color(0xFFFFDF7E) else Color(0xFF5D92B8)
    val winW = hw * 0.20f
    val winH = scale * 0.30f

    // Ground floor left & right windows
    val lowerWinY = groundY - hh * (if (isTwoStory) 0.32f else 0.62f)
    for (wSide in floatArrayOf(-0.30f, 0.30f)) {
        val wx = cx + hw * wSide - winW * 0.5f
        drawRect(
            color = winColor,
            topLeft = Offset(wx, lowerWinY),
            size = Size(winW, winH)
        )
        drawRect(
            color = Color(0xFF334155),
            topLeft = Offset(wx, lowerWinY),
            size = Size(winW, winH),
            style = Stroke(width = 1.5f)
        )
    }

    // Upper floor windows for 2-story home
    if (isTwoStory) {
        val upperWinY = top + hh * 0.15f
        for (wSide in floatArrayOf(-0.28f, 0.28f)) {
            val wx = cx + hw * wSide - winW * 0.5f
            drawRect(
                color = winColor,
                topLeft = Offset(wx, upperWinY),
                size = Size(winW, winH)
            )
            drawRect(
                color = Color(0xFF334155),
                topLeft = Offset(wx, upperWinY),
                size = Size(winW, winH),
                style = Stroke(width = 1.5f)
            )
        }
    }
}

private fun DrawScope.drawRoadsidePerson(
    cx: Float,
    groundY: Float,
    scale: Float,
    variant: Int,
    worldTime: Float,
    isNight: Boolean
) {
    val personH = scale * 0.72f
    if (personH < 5f) return

    val shirtDay = when (variant) {
        0 -> Color(0xFFE53935) // Red shirt
        1 -> Color(0xFF1E88E5) // Blue shirt
        2 -> Color(0xFFFDD835) // Yellow shirt
        3 -> Color(0xFF43A047) // Green shirt
        else -> Color(0xFFFB8C00) // Orange shirt
    }
    val shirtColor = if (isNight) lerpColor(shirtDay, Color(0xFF1A2233), 0.55f) else shirtDay
    val skinColor = if (isNight) Color(0xFF8C664C) else Color(0xFFD9A37C)
    val pantsColor = Color(0xFF1E293B)

    val headR = personH * 0.14f
    val bodyW = personH * 0.24f
    val bodyH = personH * 0.36f
    val legH = personH * 0.34f
    val headY = groundY - legH - bodyH - headR * 0.8f

    // Ground shadow
    drawOval(
        color = Color.Black.copy(alpha = 0.32f),
        topLeft = Offset(cx - bodyW * 0.75f, groundY - personH * 0.05f),
        size = Size(bodyW * 1.5f, personH * 0.10f)
    )

    // Legs (subtle walking/standing stance)
    val legStroke = (bodyW * 0.34f).coerceAtLeast(1.8f)
    drawLine(
        color = pantsColor,
        start = Offset(cx - bodyW * 0.22f, groundY - legH),
        end = Offset(cx - bodyW * 0.26f, groundY),
        strokeWidth = legStroke,
        cap = StrokeCap.Round
    )
    drawLine(
        color = pantsColor,
        start = Offset(cx + bodyW * 0.22f, groundY - legH),
        end = Offset(cx + bodyW * 0.26f, groundY),
        strokeWidth = legStroke,
        cap = StrokeCap.Round
    )

    // Torso / Shirt
    drawRoundRect(
        color = shirtColor,
        topLeft = Offset(cx - bodyW * 0.5f, groundY - legH - bodyH),
        size = Size(bodyW, bodyH),
        cornerRadius = CornerRadius(bodyW * 0.3f)
    )

    // Arms (one arm waves gently at passing cars!)
    val armStroke = (bodyW * 0.28f).coerceAtLeast(1.5f)
    val shoulderY = groundY - legH - bodyH * 0.84f
    val wave = sin(worldTime * 5.5f + variant * 1.3f) * personH * 0.10f
    // Left arm resting
    drawLine(
        color = shirtColor,
        start = Offset(cx - bodyW * 0.45f, shoulderY),
        end = Offset(cx - bodyW * 0.82f, shoulderY + bodyH * 0.65f),
        strokeWidth = armStroke,
        cap = StrokeCap.Round
    )
    // Right arm waving
    drawLine(
        color = shirtColor,
        start = Offset(cx + bodyW * 0.45f, shoulderY),
        end = Offset(cx + bodyW * 0.92f, shoulderY - personH * 0.14f + wave),
        strokeWidth = armStroke,
        cap = StrokeCap.Round
    )

    // Head
    drawCircle(
        color = skinColor,
        radius = headR,
        center = Offset(cx, headY)
    )
    // Hair cap
    drawArc(
        color = Color(0xFF1A1614),
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = true,
        topLeft = Offset(cx - headR, headY - headR),
        size = Size(headR * 2f, headR * 1.4f)
    )
}

private fun lerpColor(a: Color, b: Color, t: Float): Color =
    androidx.compose.ui.graphics.lerp(a, b, t.coerceIn(0f, 1f))

private fun DrawScope.drawTrackObject(
    obj: TrackObject,
    w: Float,
    h: Float,
    centerX: Float,
    horizonY: Float,
    worldTime: Float,
    biome: BiomePalette,
    roadMul: Float
) {
    val p = perspectiveFactor(obj.z)
    val sx = projectX(obj.laneX, obj.z, centerX, w, roadMul)
    val sy = projectY(obj.z, horizonY, h)
    val baseSize = (w.coerceAtMost(h)) * (0.015f + p * 0.085f)

    when (obj.type) {
        TrackObjectType.STAR_ORB -> {
            val bob = sin(worldTime * 6f + obj.spinOffset) * baseSize * 0.16f
            val cy = sy - baseSize * 0.45f + bob
            // Outer glow
            drawCircle(
                color = Color(0xFFFFB300).copy(alpha = 0.32f),
                radius = baseSize * 0.85f,
                center = Offset(sx, cy)
            )
            // Rotating diamond star
            val diamond = Path().apply {
                moveTo(sx, cy - baseSize * 0.55f)
                lineTo(sx + baseSize * 0.42f, cy)
                lineTo(sx, cy + baseSize * 0.55f)
                lineTo(sx - baseSize * 0.42f, cy)
                close()
            }
            drawPath(path = diamond, color = Color(0xFFFFD54F))
            drawCircle(
                color = Color.White.copy(alpha = 0.9f),
                radius = baseSize * 0.16f,
                center = Offset(sx, cy)
            )
        }

        TrackObjectType.BARRIER -> {
            // Real Moving Highway Traffic Car (Sedans, Hatchbacks & SUVs driving in other lanes!)
            val cw = baseSize * 1.72f
            val ch = baseSize * 1.45f
            val left = sx - cw * 0.5f
            val top = sy - ch

            val trafficBody = when ((obj.id % 6L).toInt()) {
                0 -> Color(0xFFE2E8F0) // Silver Sedan
                1 -> Color(0xFFD32F2F) // Red Hatchback
                2 -> Color(0xFF1976D2) // Blue Sedan
                3 -> Color(0xFFFBC02D) // Yellow Taxi / Cab
                4 -> Color(0xFF388E3C) // Forest SUV
                else -> Color(0xFF455A64) // Dark Gray SUV
            }
            val trafficShade = lerpColor(trafficBody, Color.Black, 0.28f)

            // Contact shadow on asphalt
            drawRoundRect(
                color = Color.Black.copy(alpha = 0.55f),
                topLeft = Offset(left - cw * 0.08f, sy - ch * 0.22f),
                size = Size(cw * 1.16f, ch * 0.28f),
                cornerRadius = CornerRadius(baseSize * 0.15f)
            )

            // Rear Tires
            val tw = cw * 0.18f
            val th = ch * 0.24f
            drawRoundRect(
                color = Color(0xFF0E1118),
                topLeft = Offset(left + cw * 0.04f, sy - th),
                size = Size(tw, th),
                cornerRadius = CornerRadius(4f)
            )
            drawRoundRect(
                color = Color(0xFF0E1118),
                topLeft = Offset(left + cw - cw * 0.04f - tw, sy - th),
                size = Size(tw, th),
                cornerRadius = CornerRadius(4f)
            )

            // Lower Car Body (Trunk & Rear Bumper)
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(trafficBody, trafficShade),
                    startY = top + ch * 0.34f,
                    endY = sy - th * 0.35f
                ),
                topLeft = Offset(left, top + ch * 0.34f),
                size = Size(cw, ch * 0.56f),
                cornerRadius = CornerRadius(baseSize * 0.18f)
            )

            // Upper Cabin & Roof
            val cabW = cw * 0.76f
            val cabLeft = sx - cabW * 0.5f
            drawRoundRect(
                color = trafficBody,
                topLeft = Offset(cabLeft, top + ch * 0.04f),
                size = Size(cabW, ch * 0.38f),
                cornerRadius = CornerRadius(baseSize * 0.20f)
            )

            // Rear Windshield Glass
            drawRoundRect(
                color = Color(0xFF101C30),
                topLeft = Offset(cabLeft + cabW * 0.10f, top + ch * 0.11f),
                size = Size(cabW * 0.80f, ch * 0.25f),
                cornerRadius = CornerRadius(baseSize * 0.10f)
            )

            // Glowing Red Rear Tail Lights
            val tlY = top + ch * 0.52f
            val tlW = cw * 0.22f
            val tlH = (ch * 0.13f).coerceAtLeast(3f)
            drawRoundRect(
                color = Color(0xFFFF1744),
                topLeft = Offset(left + cw * 0.06f, tlY),
                size = Size(tlW, tlH),
                cornerRadius = CornerRadius(3f)
            )
            drawRoundRect(
                color = Color(0xFFFF1744),
                topLeft = Offset(left + cw - cw * 0.06f - tlW, tlY),
                size = Size(tlW, tlH),
                cornerRadius = CornerRadius(3f)
            )

            // License Plate on Rear Trunk
            drawRoundRect(
                color = Color.White.copy(alpha = 0.85f),
                topLeft = Offset(sx - cw * 0.13f, tlY + tlH * 0.3f),
                size = Size(cw * 0.26f, (ch * 0.10f).coerceAtLeast(2.5f)),
                cornerRadius = CornerRadius(2f)
            )
        }

        TrackObjectType.MAGNET_POWERUP,
        TrackObjectType.BOOST_POWERUP,
        TrackObjectType.SHIELD_POWERUP -> {
            val color = when (obj.type) {
                TrackObjectType.MAGNET_POWERUP -> Color(0xFF00E5FF)
                TrackObjectType.BOOST_POWERUP -> Color(0xFFFF2E93)
                else -> Color(0xFF00E676)
            }
            val cy = sy - baseSize * 0.55f
            val pulse = 1f + 0.15f * sin(worldTime * 8f)
            drawCircle(
                color = color.copy(alpha = 0.28f),
                radius = baseSize * 0.95f * pulse,
                center = Offset(sx, cy)
            )
            drawCircle(
                color = color,
                radius = baseSize * 0.55f,
                center = Offset(sx, cy),
                style = Stroke(width = (baseSize * 0.14f).coerceAtLeast(3f))
            )
            drawCircle(
                color = Color.White,
                radius = baseSize * 0.22f,
                center = Offset(sx, cy)
            )
        }
    }
}

private fun DrawScope.drawGliderTrail(
    engine: AnantDriftEngine,
    skin: GliderSkin,
    w: Float,
    h: Float,
    centerX: Float,
    horizonY: Float,
    roadMul: Float,
    carZ: Float
) {
    if (engine.trail.size < 2) return
    val trailColor = if (engine.boostTimer > 0f) Color(0xFFFF2E93) else skin.trailColor
    val list = engine.trail.toList()
    val wheelSpread = 0.13f
    for (i in 1 until list.size) {
        val prev = list[i - 1]
        val curr = list[i]
        val frac = i.toFloat() / list.size
        val z1 = carZ - (1f - frac) * 0.06f
        val z2 = carZ - (1f - (i - 1f) / list.size) * 0.06f
        val y1 = projectY(z1, horizonY, h) + (1f - frac) * h * 0.11f
        val y2 = projectY(z2, horizonY, h) + (1f - (i - 1f) / list.size) * h * 0.11f

        // Left & Right tire neon tracks
        for (side in floatArrayOf(-wheelSpread, wheelSpread)) {
            val p1 = Offset(projectX(curr.laneX + side, z1, centerX, w, roadMul), y1)
            val p2 = Offset(projectX(prev.laneX + side, z2, centerX, w, roadMul), y2)
            drawLine(
                color = trailColor.copy(alpha = frac * 0.55f),
                start = p2,
                end = p1,
                strokeWidth = frac * 13f,
                cap = StrokeCap.Round
            )
        }
    }
}

private fun DrawScope.drawGlider(
    center: Offset,
    scale: Float,
    bank: Float,
    skin: GliderSkin,
    vehicleType: com.example.game.VehicleType,
    isBoosting: Boolean,
    worldTime: Float
) {
    when (vehicleType) {
        com.example.game.VehicleType.THAR -> drawTharSuv(center, scale, bank, skin, isBoosting, worldTime)
        com.example.game.VehicleType.LAMBO -> drawLamboSupercar(center, scale, bank, skin, isBoosting, worldTime)
        com.example.game.VehicleType.FORMULA -> drawFormulaRacer(center, scale, bank, skin, isBoosting, worldTime)
        com.example.game.VehicleType.TRUCK -> drawMonsterTruck(center, scale, bank, skin, isBoosting, worldTime)
        com.example.game.VehicleType.PATROL -> drawPatrolCar(center, scale, bank, skin, isBoosting, worldTime)
    }
}

private fun DrawScope.drawPatrolCar(
    center: Offset,
    scale: Float,
    bank: Float,
    skin: GliderSkin,
    isBoosting: Boolean,
    worldTime: Float
) {
    val tiltDegrees = bank * 10f
    val carW = scale * 1.32f
    val carH = scale * 2.15f
    val left = center.x - carW * 0.5f
    val top = center.y - carH * 0.54f

    rotate(degrees = tiltDegrees, pivot = center) {
        // 1. Wide Realistic Forward Headlight Beams on Highway
        val beamLen = carH * 1.45f
        for (side in floatArrayOf(-0.32f, 0.32f)) {
            val bx = center.x + carW * side
            val beamPath = Path().apply {
                moveTo(bx - carW * 0.11f, top + carH * 0.04f)
                lineTo(bx + carW * 0.11f, top + carH * 0.04f)
                lineTo(bx + carW * 0.42f, top - beamLen)
                lineTo(bx - carW * 0.42f, top - beamLen)
                close()
            }
            drawPath(
                path = beamPath,
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color(0x77E8FCFF)),
                    startY = top - beamLen,
                    endY = top + carH * 0.04f
                )
            )
        }

        // 2. Deep Realistic Contact Shadow + Neon Underglow
        drawRoundRect(
            color = Color.Black.copy(alpha = 0.68f),
            topLeft = Offset(left - carW * 0.14f, top + carH * 0.05f),
            size = Size(carW * 1.28f, carH * 1.03f),
            cornerRadius = CornerRadius(carW * 0.34f)
        )
        drawRoundRect(
            color = (if (isBoosting) Color(0xFFFF2E93) else skin.trailColor).copy(alpha = 0.34f),
            topLeft = Offset(left - carW * 0.09f, top + carH * 0.02f),
            size = Size(carW * 1.18f, carH * 1.01f),
            cornerRadius = CornerRadius(carW * 0.30f)
        )

        // 3. Four Wide Rubber Tires with Metallic Rim Edge
        val wheelW = carW * 0.19f
        val wheelH = carH * 0.24f
        val wheelPositions = listOf(
            Offset(left - wheelW * 0.48f, top + carH * 0.13f),
            Offset(left + carW - wheelW * 0.52f, top + carH * 0.13f),
            Offset(left - wheelW * 0.54f, top + carH * 0.65f),
            Offset(left + carW - wheelW * 0.46f, top + carH * 0.65f)
        )
        for (wp in wheelPositions) {
            drawRoundRect(
                color = Color(0xFF0E111A),
                topLeft = wp,
                size = Size(wheelW, wheelH),
                cornerRadius = CornerRadius(wheelW * 0.32f)
            )
            drawRoundRect(
                color = Color(0xFF3A445C),
                topLeft = Offset(wp.x + wheelW * 0.22f, wp.y + wheelH * 0.14f),
                size = Size(wheelW * 0.56f, wheelH * 0.72f),
                cornerRadius = CornerRadius(wheelW * 0.2f),
                style = Stroke(width = 2f)
            )
        }

        // 4. Side Mirrors (Left & Right)
        val mirrorY = top + carH * 0.31f
        val mirrorW = carW * 0.13f
        val mirrorH = carH * 0.048f
        drawRoundRect(
            color = skin.bodyPrimary,
            topLeft = Offset(left - mirrorW * 0.78f, mirrorY),
            size = Size(mirrorW, mirrorH),
            cornerRadius = CornerRadius(mirrorH * 0.5f)
        )
        drawRoundRect(
            color = skin.bodyPrimary,
            topLeft = Offset(left + carW - mirrorW * 0.22f, mirrorY),
            size = Size(mirrorW, mirrorH),
            cornerRadius = CornerRadius(mirrorH * 0.5f)
        )

        // 5. Dual Chrome Exhaust Pipes & Layered Nitro Flames
        val flameLen = scale * (if (isBoosting) 1.15f else 0.44f) + sin(worldTime * 38f) * scale * 0.10f
        val flameOuter = if (isBoosting) Color(0xFFFF2E93) else Color(0xFFFF8F00)
        for (exSide in floatArrayOf(-0.24f, 0.24f)) {
            val exX = center.x + carW * exSide
            val exY = top + carH * 0.98f
            // Chrome tip
            drawRoundRect(
                color = Color(0xFF94A3B8),
                topLeft = Offset(exX - carW * 0.065f, exY - 6f),
                size = Size(carW * 0.13f, 10f),
                cornerRadius = CornerRadius(3f)
            )
            // Outer flame
            val flame = Path().apply {
                moveTo(exX - carW * 0.085f, exY)
                lineTo(exX, exY + flameLen)
                lineTo(exX + carW * 0.085f, exY)
                close()
            }
            drawPath(path = flame, color = flameOuter.copy(alpha = 0.88f))
            // Hot white-cyan core flame
            val coreFlame = Path().apply {
                moveTo(exX - carW * 0.04f, exY)
                lineTo(exX, exY + flameLen * 0.62f)
                lineTo(exX + carW * 0.04f, exY)
                close()
            }
            drawPath(path = coreFlame, color = Color.White.copy(alpha = 0.92f))
        }

        // 6. Sculpted 3D Metallic Car Body Chassis
        drawRoundRect(
            brush = Brush.verticalGradient(
                colorStops = arrayOf(
                    0.0f to skin.bodyPrimary,
                    0.35f to skin.bodyPrimary.copy(alpha = 0.95f),
                    0.75f to skin.bodyAccent,
                    1.0f to Color(0xFF0A1020)
                ),
                startY = top,
                endY = top + carH
            ),
            topLeft = Offset(left, top),
            size = Size(carW, carH),
            cornerRadius = CornerRadius(carW * 0.30f, carW * 0.36f)
        )

        // Metallic Side Contour Highlights (Left & Right Fenders)
        drawRoundRect(
            brush = Brush.horizontalGradient(
                colorStops = arrayOf(
                    0.0f to Color.White.copy(alpha = 0.32f),
                    0.16f to Color.Transparent,
                    0.84f to Color.Transparent,
                    1.0f to Color.White.copy(alpha = 0.28f)
                ),
                startX = left,
                endX = left + carW
            ),
            topLeft = Offset(left, top),
            size = Size(carW, carH),
            cornerRadius = CornerRadius(carW * 0.30f, carW * 0.36f)
        )

        // Crisp Body Rim Outline
        drawRoundRect(
            color = skin.bodyPrimary,
            topLeft = Offset(left, top),
            size = Size(carW, carH),
            cornerRadius = CornerRadius(carW * 0.30f, carW * 0.36f),
            style = Stroke(width = 3.2f)
        )

        // Front Headlight LED Lenses on Hood Corners
        for (hSide in floatArrayOf(-0.31f, 0.31f)) {
            val hx = center.x + carW * hSide
            drawRoundRect(
                color = Color(0xFFE0F7FA),
                topLeft = Offset(hx - carW * 0.11f, top + carH * 0.025f),
                size = Size(carW * 0.22f, carH * 0.035f),
                cornerRadius = CornerRadius(6f)
            )
        }

        // Sculpted Hood Vent & Twin Racing Stripes
        for (stripeSide in floatArrayOf(-0.07f, 0.07f)) {
            drawRoundRect(
                color = Color.White.copy(alpha = 0.24f),
                topLeft = Offset(center.x + carW * stripeSide - carW * 0.03f, top + carH * 0.05f),
                size = Size(carW * 0.06f, carH * 0.22f),
                cornerRadius = CornerRadius(3f)
            )
        }

        // 7. Front Windshield, Glossy Roof Cabin & Rear Glass
        val cabinLeft = left + carW * 0.12f
        val cabinW = carW * 0.76f

        // Front Windshield (Tinted Automotive Glass)
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF1A2B4C), Color(0xFF070D1C)),
                startY = top + carH * 0.25f,
                endY = top + carH * 0.45f
            ),
            topLeft = Offset(cabinLeft, top + carH * 0.25f),
            size = Size(cabinW, carH * 0.22f),
            cornerRadius = CornerRadius(carW * 0.18f)
        )
        // Diagonal glass reflection streaks
        drawLine(
            color = Color.White.copy(alpha = 0.42f),
            start = Offset(cabinLeft + cabinW * 0.14f, top + carH * 0.30f),
            end = Offset(cabinLeft + cabinW * 0.44f, top + carH * 0.27f),
            strokeWidth = 4f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color.White.copy(alpha = 0.25f),
            start = Offset(cabinLeft + cabinW * 0.20f, top + carH * 0.36f),
            end = Offset(cabinLeft + cabinW * 0.52f, top + carH * 0.33f),
            strokeWidth = 2.5f,
            cap = StrokeCap.Round
        )

        // Roof Panel with Glossy Highlight
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(skin.bodyAccent.copy(alpha = 0.96f), skin.bodyPrimary.copy(alpha = 0.55f)),
                startY = top + carH * 0.43f,
                endY = top + carH * 0.69f
            ),
            topLeft = Offset(cabinLeft + carW * 0.04f, top + carH * 0.43f),
            size = Size(cabinW - carW * 0.08f, carH * 0.25f),
            cornerRadius = CornerRadius(carW * 0.12f)
        )

        // Rear Windshield
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF080F20), Color(0xFF1A2B4C)),
                startY = top + carH * 0.68f,
                endY = top + carH * 0.83f
            ),
            topLeft = Offset(cabinLeft + carW * 0.06f, top + carH * 0.68f),
            size = Size(cabinW - carW * 0.12f, carH * 0.145f),
            cornerRadius = CornerRadius(carW * 0.12f)
        )

        // 8. Carbon-Style Rear Aero Spoiler Wing
        drawRoundRect(
            color = Color(0xFF101628),
            topLeft = Offset(left - carW * 0.05f, top + carH * 0.855f),
            size = Size(carW * 1.10f, carH * 0.068f),
            cornerRadius = CornerRadius(7f)
        )
        drawRoundRect(
            color = skin.bodyPrimary,
            topLeft = Offset(left - carW * 0.05f, top + carH * 0.855f),
            size = Size(carW * 1.10f, carH * 0.068f),
            cornerRadius = CornerRadius(7f),
            style = Stroke(width = 2.5f)
        )

        // 9. Realistic Rear Bumper, License Plate Glow & Full LED Tail Lights
        val tailY = top + carH * 0.945f
        // Center rear bumper recess / plate glow
        drawRoundRect(
            color = Color(0xFFE2E8F0).copy(alpha = 0.78f),
            topLeft = Offset(center.x - carW * 0.10f, tailY - 4f),
            size = Size(carW * 0.20f, 8f),
            cornerRadius = CornerRadius(2.5f)
        )
        // Left & Right LED Tail Light Clusters
        for (tSide in floatArrayOf(-0.33f, 0.33f)) {
            val tx = center.x + carW * tSide
            drawRoundRect(
                color = Color(0xFFFF1744),
                topLeft = Offset(tx - carW * 0.14f, tailY - 5.5f),
                size = Size(carW * 0.28f, 11f),
                cornerRadius = CornerRadius(4.5f)
            )
            // Bright inner LED strip
            drawLine(
                color = Color(0xFFFFB3C1),
                start = Offset(tx - carW * 0.10f, tailY),
                end = Offset(tx + carW * 0.10f, tailY),
                strokeWidth = 3f,
                cap = StrokeCap.Round
            )
            drawCircle(
                color = Color(0xFFFF1744).copy(alpha = 0.42f),
                radius = carW * 0.22f,
                center = Offset(tx, tailY)
            )
        }

        // 10. Realistic Flashing Emergency Roof Siren Lightbar (Red & Blue 🚨)
        val sirenY = top + carH * 0.54f
        val barW = cabinW * 0.82f
        val barH = carH * 0.072f
        val redActive = sin(worldTime * 16f) >= 0f
        val redAlpha = if (redActive) 1.0f else 0.30f
        val blueAlpha = if (!redActive) 1.0f else 0.30f

        // Lightbar metallic housing
        drawRoundRect(
            color = Color(0xFF151C2E),
            topLeft = Offset(center.x - barW * 0.5f, sirenY - barH * 0.5f),
            size = Size(barW, barH),
            cornerRadius = CornerRadius(barH * 0.45f)
        )

        // Left Red Siren Lens + Wide Glow Spill
        val redCenter = Offset(center.x - barW * 0.26f, sirenY)
        if (redActive) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xDDFF1744), Color(0x44FF1744), Color.Transparent),
                    center = redCenter,
                    radius = carW * 0.72f
                ),
                radius = carW * 0.72f,
                center = redCenter
            )
        }
        drawRoundRect(
            color = Color(0xFFFF1744).copy(alpha = redAlpha),
            topLeft = Offset(center.x - barW * 0.47f, sirenY - barH * 0.38f),
            size = Size(barW * 0.40f, barH * 0.76f),
            cornerRadius = CornerRadius(barH * 0.35f)
        )

        // Right Blue Siren Lens + Wide Glow Spill
        val blueCenter = Offset(center.x + barW * 0.26f, sirenY)
        if (!redActive) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xDD00B0FF), Color(0x4400B0FF), Color.Transparent),
                    center = blueCenter,
                    radius = carW * 0.72f
                ),
                radius = carW * 0.72f,
                center = blueCenter
            )
        }
        drawRoundRect(
            color = Color(0xFF00B0FF).copy(alpha = blueAlpha),
            topLeft = Offset(center.x + barW * 0.07f, sirenY - barH * 0.38f),
            size = Size(barW * 0.40f, barH * 0.76f),
            cornerRadius = CornerRadius(barH * 0.35f)
        )

        // Center white strobe divider
        drawRoundRect(
            color = Color.White.copy(alpha = 0.85f),
            topLeft = Offset(center.x - barW * 0.04f, sirenY - barH * 0.34f),
            size = Size(barW * 0.08f, barH * 0.68f),
            cornerRadius = CornerRadius(2f)
        )
    }
}

private fun DrawScope.drawTharSuv(
    center: Offset,
    scale: Float,
    bank: Float,
    skin: GliderSkin,
    isBoosting: Boolean,
    worldTime: Float
) {
    val tiltDegrees = bank * 8f
    val carW = scale * 1.40f
    val carH = scale * 2.08f
    val left = center.x - carW * 0.5f
    val top = center.y - carH * 0.54f

    rotate(degrees = tiltDegrees, pivot = center) {
        // Headlight beams
        val beamLen = carH * 1.4f
        for (side in floatArrayOf(-0.33f, 0.33f)) {
            val bx = center.x + carW * side
            val beam = Path().apply {
                moveTo(bx - carW * 0.12f, top + carH * 0.03f)
                lineTo(bx + carW * 0.12f, top + carH * 0.03f)
                lineTo(bx + carW * 0.44f, top - beamLen)
                lineTo(bx - carW * 0.44f, top - beamLen)
                close()
            }
            drawPath(
                path = beam,
                brush = Brush.verticalGradient(
                    listOf(Color.Transparent, Color(0x80FFF3C4)),
                    startY = top - beamLen,
                    endY = top + carH * 0.03f
                )
            )
        }

        // Ground shadow
        drawRoundRect(
            color = Color.Black.copy(alpha = 0.72f),
            topLeft = Offset(left - carW * 0.20f, top + carH * 0.04f),
            size = Size(carW * 1.40f, carH * 1.06f),
            cornerRadius = CornerRadius(carW * 0.18f)
        )

        // Chunky Off-Road Mud Tires protruding strongly outside fenders
        val wheelW = carW * 0.26f
        val wheelH = carH * 0.27f
        val wheels = listOf(
            Offset(left - wheelW * 0.62f, top + carH * 0.09f),
            Offset(left + carW - wheelW * 0.38f, top + carH * 0.09f),
            Offset(left - wheelW * 0.62f, top + carH * 0.64f),
            Offset(left + carW - wheelW * 0.38f, top + carH * 0.64f)
        )
        for (wp in wheels) {
            drawRoundRect(
                color = Color(0xFF0C0F16),
                topLeft = wp,
                size = Size(wheelW, wheelH),
                cornerRadius = CornerRadius(wheelW * 0.24f)
            )
            // Tire tread notches
            for (n in 1..3) {
                val ny = wp.y + wheelH * (n * 0.25f)
                drawLine(
                    color = Color(0xFF2B3242),
                    start = Offset(wp.x + 3f, ny),
                    end = Offset(wp.x + wheelW - 3f, ny),
                    strokeWidth = 3f
                )
            }
        }

        // Matte black wide wheel arch flares
        for (wp in wheels) {
            drawRoundRect(
                color = Color(0xFF171B26),
                topLeft = Offset(wp.x + wheelW * 0.14f, wp.y - 4f),
                size = Size(wheelW * 0.72f, wheelH + 8f),
                cornerRadius = CornerRadius(6f)
            )
        }

        // Dual exhaust nitro flames
        val flameLen = scale * (if (isBoosting) 1.1f else 0.38f) + sin(worldTime * 36f) * scale * 0.09f
        val flameColor = if (isBoosting) Color(0xFFFF2E93) else Color(0xFFFF8F00)
        val exX = center.x + carW * 0.28f
        val exY = top + carH * 0.99f
        val flame = Path().apply {
            moveTo(exX - carW * 0.08f, exY)
            lineTo(exX, exY + flameLen)
            lineTo(exX + carW * 0.08f, exY)
            close()
        }
        drawPath(path = flame, color = flameColor.copy(alpha = 0.85f))

        // Boxy Rugged Thar Body Chassis
        drawRoundRect(
            brush = Brush.verticalGradient(
                listOf(skin.bodyPrimary, skin.bodyAccent),
                startY = top,
                endY = top + carH
            ),
            topLeft = Offset(left, top),
            size = Size(carW, carH * 0.96f),
            cornerRadius = CornerRadius(carW * 0.14f)
        )
        drawRoundRect(
            color = skin.bodyPrimary,
            topLeft = Offset(left, top),
            size = Size(carW, carH * 0.96f),
            cornerRadius = CornerRadius(carW * 0.14f),
            style = Stroke(width = 3.2f)
        )

        // Iconic Square Hood with Hood Latches & Round Headlight Bezels
        for (hSide in floatArrayOf(-0.33f, 0.33f)) {
            drawCircle(
                color = Color(0xFFFFF8E1),
                radius = carW * 0.095f,
                center = Offset(center.x + carW * hSide, top + carH * 0.045f)
            )
        }
        drawRoundRect(
            color = Color.Black.copy(alpha = 0.25f),
            topLeft = Offset(left + carW * 0.18f, top + carH * 0.07f),
            size = Size(carW * 0.64f, carH * 0.22f),
            cornerRadius = CornerRadius(6f)
        )

        // Side Mirrors (chunky SUV mirrors)
        drawRoundRect(
            color = Color(0xFF141824),
            topLeft = Offset(left - carW * 0.12f, top + carH * 0.32f),
            size = Size(carW * 0.14f, carH * 0.055f),
            cornerRadius = CornerRadius(4f)
        )
        drawRoundRect(
            color = Color(0xFF141824),
            topLeft = Offset(left + carW - carW * 0.02f, top + carH * 0.32f),
            size = Size(carW * 0.14f, carH * 0.055f),
            cornerRadius = CornerRadius(4f)
        )

        // Upright Front Windshield
        drawRoundRect(
            color = Color(0xFF0A1428),
            topLeft = Offset(left + carW * 0.10f, top + carH * 0.31f),
            size = Size(carW * 0.80f, carH * 0.15f),
            cornerRadius = CornerRadius(8f)
        )
        // Off-Road Roof LED Lightbar above windshield
        drawRoundRect(
            color = Color(0xFFFFF9C4),
            topLeft = Offset(left + carW * 0.18f, top + carH * 0.44f),
            size = Size(carW * 0.64f, carH * 0.032f),
            cornerRadius = CornerRadius(4f)
        )

        // Iconic Matte-Black Hardtop Boxy Roof
        drawRoundRect(
            color = Color(0xFF151924),
            topLeft = Offset(left + carW * 0.07f, top + carH * 0.47f),
            size = Size(carW * 0.86f, carH * 0.44f),
            cornerRadius = CornerRadius(10f)
        )
        // Roof ribs
        for (rib in floatArrayOf(-0.22f, 0f, 0.22f)) {
            drawLine(
                color = Color(0xFF283044),
                start = Offset(center.x + carW * rib, top + carH * 0.51f),
                end = Offset(center.x + carW * rib, top + carH * 0.87f),
                strokeWidth = 4f,
                cap = StrokeCap.Round
            )
        }

        // Iconic Rear Tailgate-Mounted Spare Tire!
        drawRoundRect(
            color = Color(0xFF0B0E16),
            topLeft = Offset(center.x - carW * 0.24f, top + carH * 0.90f),
            size = Size(carW * 0.48f, carH * 0.13f),
            cornerRadius = CornerRadius(10f)
        )
        drawRoundRect(
            color = skin.bodyPrimary.copy(alpha = 0.7f),
            topLeft = Offset(center.x - carW * 0.18f, top + carH * 0.92f),
            size = Size(carW * 0.36f, carH * 0.085f),
            cornerRadius = CornerRadius(6f),
            style = Stroke(width = 2.5f)
        )

        // Square Red LED Tail Lights
        for (tSide in floatArrayOf(-0.38f, 0.38f)) {
            val tx = center.x + carW * tSide
            drawRoundRect(
                color = Color(0xFFFF1744),
                topLeft = Offset(tx - carW * 0.09f, top + carH * 0.89f),
                size = Size(carW * 0.18f, carH * 0.075f),
                cornerRadius = CornerRadius(4f)
            )
        }
    }
}

private fun DrawScope.drawLamboSupercar(
    center: Offset,
    scale: Float,
    bank: Float,
    skin: GliderSkin,
    isBoosting: Boolean,
    worldTime: Float
) {
    val tiltDegrees = bank * 12f
    val carW = scale * 1.44f
    val carH = scale * 2.25f
    val left = center.x - carW * 0.5f
    val top = center.y - carH * 0.54f

    rotate(degrees = tiltDegrees, pivot = center) {
        // Sharp Laser Headlight Beams
        val beamLen = carH * 1.55f
        for (side in floatArrayOf(-0.28f, 0.28f)) {
            val bx = center.x + carW * side
            val beam = Path().apply {
                moveTo(bx - carW * 0.08f, top + carH * 0.06f)
                lineTo(bx + carW * 0.08f, top + carH * 0.06f)
                lineTo(bx + carW * 0.38f, top - beamLen)
                lineTo(bx - carW * 0.38f, top - beamLen)
                close()
            }
            drawPath(
                path = beam,
                brush = Brush.verticalGradient(
                    listOf(Color.Transparent, Color(0x88E0F7FA)),
                    startY = top - beamLen,
                    endY = top + carH * 0.06f
                )
            )
        }

        // Low-Slung Wide Shadow + Underglow
        drawRoundRect(
            color = Color.Black.copy(alpha = 0.72f),
            topLeft = Offset(left - carW * 0.12f, top + carH * 0.05f),
            size = Size(carW * 1.24f, carH * 1.01f),
            cornerRadius = CornerRadius(carW * 0.26f)
        )
        drawRoundRect(
            color = (if (isBoosting) Color(0xFFFF2E93) else skin.trailColor).copy(alpha = 0.38f),
            topLeft = Offset(left - carW * 0.07f, top + carH * 0.02f),
            size = Size(carW * 1.14f, carH * 0.99f),
            cornerRadius = CornerRadius(carW * 0.24f)
        )

        // Wide Supercar Slick Tires
        val wheelW = carW * 0.18f
        val wheelH = carH * 0.23f
        for (wp in listOf(
            Offset(left - wheelW * 0.35f, top + carH * 0.16f),
            Offset(left + carW - wheelW * 0.65f, top + carH * 0.16f),
            Offset(left - wheelW * 0.45f, top + carH * 0.64f),
            Offset(left + carW - wheelW * 0.55f, top + carH * 0.64f)
        )) {
            drawRoundRect(
                color = Color(0xFF0D101A),
                topLeft = wp,
                size = Size(wheelW, wheelH),
                cornerRadius = CornerRadius(wheelW * 0.3f)
            )
        }

        // Quad Central Supercar Exhausts + Twin Blue/Orange Afterburner Flames
        val flameLen = scale * (if (isBoosting) 1.35f else 0.58f) + sin(worldTime * 42f) * scale * 0.12f
        val flameColor = if (isBoosting) Color(0xFFFF2E93) else Color(0xFF00E5FF)
        for (exSide in floatArrayOf(-0.11f, 0.11f)) {
            val exX = center.x + carW * exSide
            val exY = top + carH * 0.98f
            val flame = Path().apply {
                moveTo(exX - carW * 0.075f, exY)
                lineTo(exX, exY + flameLen)
                lineTo(exX + carW * 0.075f, exY)
                close()
            }
            drawPath(path = flame, color = flameColor.copy(alpha = 0.92f))
            val inner = Path().apply {
                moveTo(exX - carW * 0.035f, exY)
                lineTo(exX, exY + flameLen * 0.62f)
                lineTo(exX + carW * 0.035f, exY)
                close()
            }
            drawPath(path = inner, color = Color.White)
        }

        // Aggressive Angular Lamborghini Wedge Body Path
        val wedge = Path().apply {
            moveTo(center.x - carW * 0.30f, top)
            lineTo(center.x + carW * 0.30f, top)
            lineTo(left + carW * 0.96f, top + carH * 0.18f)
            lineTo(left + carW, top + carH * 0.58f)
            lineTo(left + carW * 0.96f, top + carH * 0.96f)
            lineTo(left + carW * 0.04f, top + carH * 0.96f)
            lineTo(left, top + carH * 0.58f)
            lineTo(left + carW * 0.04f, top + carH * 0.18f)
            close()
        }
        drawPath(
            path = wedge,
            brush = Brush.verticalGradient(
                colorStops = arrayOf(
                    0.0f to skin.bodyPrimary,
                    0.45f to skin.bodyPrimary.copy(alpha = 0.94f),
                    0.85f to skin.bodyAccent,
                    1.0f to Color(0xFF0A0F1D)
                ),
                startY = top,
                endY = top + carH
            )
        )
        drawPath(path = wedge, color = skin.bodyPrimary, style = Stroke(width = 3.2f))

        // Sharp Side Air Intakes (Dark Carbon Scoops)
        for (s in floatArrayOf(-1f, 1f)) {
            val sx = if (s < 0) left + carW * 0.05f else left + carW * 0.85f
            drawRoundRect(
                color = Color(0xFF0A0E18),
                topLeft = Offset(sx, top + carH * 0.48f),
                size = Size(carW * 0.10f, carH * 0.24f),
                cornerRadius = CornerRadius(4f)
            )
        }

        // Sharp Angular Cabin & Steep Windshield
        val cockpit = Path().apply {
            moveTo(center.x - carW * 0.26f, top + carH * 0.22f)
            lineTo(center.x + carW * 0.26f, top + carH * 0.22f)
            lineTo(center.x + carW * 0.36f, top + carH * 0.54f)
            lineTo(center.x - carW * 0.36f, top + carH * 0.54f)
            close()
        }
        drawPath(path = cockpit, color = Color(0xFF091224))
        // Windshield reflection
        drawLine(
            color = Color.White.copy(alpha = 0.45f),
            start = Offset(center.x - carW * 0.18f, top + carH * 0.28f),
            end = Offset(center.x + carW * 0.12f, top + carH * 0.26f),
            strokeWidth = 3.5f,
            cap = StrokeCap.Round
        )

        // Iconic Mid-Engine Louvered Glass V12 Rear Deck!
        val deckLeft = center.x - carW * 0.27f
        val deckW = carW * 0.54f
        drawRoundRect(
            color = Color(0xFF101626),
            topLeft = Offset(deckLeft, top + carH * 0.56f),
            size = Size(deckW, carH * 0.28f),
            cornerRadius = CornerRadius(6f)
        )
        for (louver in 0..3) {
            val ly = top + carH * (0.60f + louver * 0.058f)
            drawLine(
                color = skin.bodyPrimary.copy(alpha = 0.65f),
                start = Offset(deckLeft + 6f, ly),
                end = Offset(deckLeft + deckW - 6f, ly),
                strokeWidth = 3.5f,
                cap = StrokeCap.Round
            )
        }

        // Huge Carbon GT Rear Wing
        drawRoundRect(
            color = Color(0xFF0D1220),
            topLeft = Offset(left - carW * 0.08f, top + carH * 0.85f),
            size = Size(carW * 1.16f, carH * 0.07f),
            cornerRadius = CornerRadius(5f)
        )
        drawRoundRect(
            color = skin.bodyPrimary,
            topLeft = Offset(left - carW * 0.08f, top + carH * 0.85f),
            size = Size(carW * 1.16f, carH * 0.07f),
            cornerRadius = CornerRadius(5f),
            style = Stroke(width = 2.5f)
        )

        // Sharp Y-Shaped Red LED Tail Lights
        val tailY = top + carH * 0.94f
        for (tSide in floatArrayOf(-0.32f, 0.32f)) {
            val tx = center.x + carW * tSide
            drawLine(
                color = Color(0xFFFF1744),
                start = Offset(tx - carW * 0.14f, tailY),
                end = Offset(tx + carW * 0.14f, tailY),
                strokeWidth = 7f,
                cap = StrokeCap.Round
            )
            drawCircle(
                color = Color(0xFFFF1744).copy(alpha = 0.45f),
                radius = carW * 0.20f,
                center = Offset(tx, tailY)
            )
        }
    }
}

private fun DrawScope.drawFormulaRacer(
    center: Offset,
    scale: Float,
    bank: Float,
    skin: GliderSkin,
    isBoosting: Boolean,
    worldTime: Float
) {
    val tiltDegrees = bank * 13f
    val carW = scale * 1.48f
    val carH = scale * 2.30f
    val left = center.x - carW * 0.5f
    val top = center.y - carH * 0.54f

    rotate(degrees = tiltDegrees, pivot = center) {
        // Wide Front Aero Wing
        drawRoundRect(
            color = skin.bodyPrimary,
            topLeft = Offset(left + carW * 0.06f, top + carH * 0.02f),
            size = Size(carW * 0.88f, carH * 0.09f),
            cornerRadius = CornerRadius(5f)
        )

        // Suspension Arms connecting body to 4 exposed slick wheels
        drawLine(
            color = Color(0xFF334155),
            start = Offset(left + carW * 0.08f, top + carH * 0.24f),
            end = Offset(left + carW * 0.92f, top + carH * 0.24f),
            strokeWidth = 6f
        )
        drawLine(
            color = Color(0xFF334155),
            start = Offset(left + carW * 0.06f, top + carH * 0.78f),
            end = Offset(left + carW * 0.94f, top + carH * 0.78f),
            strokeWidth = 8f
        )

        // 4 Exposed Wide F1 Racing Slicks
        val fWheelW = carW * 0.21f
        val fWheelH = carH * 0.21f
        val rWheelW = carW * 0.25f
        val rWheelH = carH * 0.25f
        for (wp in listOf(
            Offset(left, top + carH * 0.14f) to Size(fWheelW, fWheelH),
            Offset(left + carW - fWheelW, top + carH * 0.14f) to Size(fWheelW, fWheelH),
            Offset(left - carW * 0.02f, top + carH * 0.66f) to Size(rWheelW, rWheelH),
            Offset(left + carW - rWheelW + carW * 0.02f, top + carH * 0.66f) to Size(rWheelW, rWheelH)
        )) {
            drawRoundRect(
                color = Color(0xFF0C0F16),
                topLeft = wp.first,
                size = wp.second,
                cornerRadius = CornerRadius(8f)
            )
            drawRoundRect(
                color = skin.bodyPrimary.copy(alpha = 0.65f),
                topLeft = Offset(wp.first.x + 4f, wp.first.y + 4f),
                size = Size(wp.second.width - 8f, wp.second.height - 8f),
                cornerRadius = CornerRadius(6f),
                style = Stroke(width = 2f)
            )
        }

        // Single Central Exhaust Roar Flame
        val flameLen = scale * (if (isBoosting) 1.45f else 0.65f) + sin(worldTime * 45f) * scale * 0.14f
        val flameColor = if (isBoosting) Color(0xFFFF2E93) else Color(0xFFFFB300)
        val flame = Path().apply {
            moveTo(center.x - carW * 0.09f, top + carH * 0.96f)
            lineTo(center.x, top + carH * 0.96f + flameLen)
            lineTo(center.x + carW * 0.09f, top + carH * 0.96f)
            close()
        }
        drawPath(path = flame, color = flameColor)

        // Aerodynamic F1 Nosecone + Sidepods Body
        val body = Path().apply {
            moveTo(center.x - carW * 0.08f, top)
            lineTo(center.x + carW * 0.08f, top)
            lineTo(center.x + carW * 0.16f, top + carH * 0.34f)
            lineTo(center.x + carW * 0.34f, top + carH * 0.44f)
            lineTo(center.x + carW * 0.30f, top + carH * 0.78f)
            lineTo(center.x + carW * 0.14f, top + carH * 0.94f)
            lineTo(center.x - carW * 0.14f, top + carH * 0.94f)
            lineTo(center.x - carW * 0.30f, top + carH * 0.78f)
            lineTo(center.x - carW * 0.34f, top + carH * 0.44f)
            lineTo(center.x - carW * 0.16f, top + carH * 0.34f)
            close()
        }
        drawPath(
            path = body,
            brush = Brush.verticalGradient(
                listOf(skin.bodyPrimary, skin.bodyAccent),
                startY = top,
                endY = top + carH
            )
        )
        drawPath(path = body, color = skin.bodyPrimary, style = Stroke(width = 2.8f))

        // Open Cockpit & Driver Helmet
        drawOval(
            color = Color(0xFF0A1020),
            topLeft = Offset(center.x - carW * 0.12f, top + carH * 0.40f),
            size = Size(carW * 0.24f, carH * 0.22f)
        )
        drawCircle(
            color = Color.White,
            radius = carW * 0.085f,
            center = Offset(center.x, top + carH * 0.51f)
        )

        // Wide Rear Wing + Blinking Rain Light
        drawRoundRect(
            color = Color(0xFF0E1424),
            topLeft = Offset(center.x - carW * 0.38f, top + carH * 0.88f),
            size = Size(carW * 0.76f, carH * 0.08f),
            cornerRadius = CornerRadius(4f)
        )
        drawRoundRect(
            color = skin.bodyPrimary,
            topLeft = Offset(center.x - carW * 0.38f, top + carH * 0.88f),
            size = Size(carW * 0.76f, carH * 0.08f),
            cornerRadius = CornerRadius(4f),
            style = Stroke(width = 2.5f)
        )
        if ((worldTime * 8f).toInt() % 2 == 0) {
            drawCircle(
                color = Color(0xFFFF1744),
                radius = carW * 0.055f,
                center = Offset(center.x, top + carH * 0.96f)
            )
        }
    }
}

private fun DrawScope.drawMonsterTruck(
    center: Offset,
    scale: Float,
    bank: Float,
    skin: GliderSkin,
    isBoosting: Boolean,
    worldTime: Float
) {
    val tiltDegrees = bank * 7f
    val carW = scale * 1.46f
    val carH = scale * 2.22f
    val left = center.x - carW * 0.5f
    val top = center.y - carH * 0.54f

    rotate(degrees = tiltDegrees, pivot = center) {
        // Wide Heavy Headlight Beams
        val beamLen = carH * 1.4f
        for (side in floatArrayOf(-0.34f, 0.34f)) {
            val bx = center.x + carW * side
            val beam = Path().apply {
                moveTo(bx - carW * 0.12f, top + carH * 0.03f)
                lineTo(bx + carW * 0.12f, top + carH * 0.03f)
                lineTo(bx + carW * 0.44f, top - beamLen)
                lineTo(bx - carW * 0.44f, top - beamLen)
                close()
            }
            drawPath(
                path = beam,
                brush = Brush.verticalGradient(
                    listOf(Color.Transparent, Color(0x80E0F7FA)),
                    startY = top - beamLen,
                    endY = top + carH * 0.03f
                )
            )
        }

        // Massive Monster Tires
        val wheelW = carW * 0.30f
        val wheelH = carH * 0.29f
        for (wp in listOf(
            Offset(left - wheelW * 0.62f, top + carH * 0.08f),
            Offset(left + carW - wheelW * 0.38f, top + carH * 0.08f),
            Offset(left - wheelW * 0.62f, top + carH * 0.64f),
            Offset(left + carW - wheelW * 0.38f, top + carH * 0.64f)
        )) {
            drawRoundRect(
                color = Color(0xFF0A0D14),
                topLeft = wp,
                size = Size(wheelW, wheelH),
                cornerRadius = CornerRadius(10f)
            )
            for (t in 1..4) {
                val ty = wp.y + wheelH * (t * 0.20f)
                drawLine(
                    color = Color(0xFF334155),
                    start = Offset(wp.x + 4f, ty),
                    end = Offset(wp.x + wheelW - 4f, ty),
                    strokeWidth = 3.5f
                )
            }
        }

        // Dual Heavy Exhaust Flames
        val flameLen = scale * (if (isBoosting) 1.25f else 0.48f) + sin(worldTime * 34f) * scale * 0.10f
        val flameColor = if (isBoosting) Color(0xFFFF2E93) else Color(0xFFFF8F00)
        for (exSide in floatArrayOf(-0.28f, 0.28f)) {
            val exX = center.x + carW * exSide
            val exY = top + carH * 0.98f
            val flame = Path().apply {
                moveTo(exX - carW * 0.09f, exY)
                lineTo(exX, exY + flameLen)
                lineTo(exX + carW * 0.09f, exY)
                close()
            }
            drawPath(path = flame, color = flameColor.copy(alpha = 0.88f))
        }

        // Heavy Pickup Truck Body
        drawRoundRect(
            brush = Brush.verticalGradient(
                listOf(skin.bodyPrimary, skin.bodyAccent),
                startY = top,
                endY = top + carH
            ),
            topLeft = Offset(left, top),
            size = Size(carW, carH * 0.98f),
            cornerRadius = CornerRadius(carW * 0.15f)
        )
        drawRoundRect(
            color = skin.bodyPrimary,
            topLeft = Offset(left, top),
            size = Size(carW, carH * 0.98f),
            cornerRadius = CornerRadius(carW * 0.15f),
            style = Stroke(width = 3.2f)
        )

        // Front Cab Windshield + Roof with Amber Clearance Lights
        drawRoundRect(
            color = Color(0xFF0A1428),
            topLeft = Offset(left + carW * 0.10f, top + carH * 0.26f),
            size = Size(carW * 0.80f, carH * 0.16f),
            cornerRadius = CornerRadius(8f)
        )
        drawRoundRect(
            color = skin.bodyAccent,
            topLeft = Offset(left + carW * 0.09f, top + carH * 0.40f),
            size = Size(carW * 0.82f, carH * 0.20f),
            cornerRadius = CornerRadius(8f)
        )
        // 3 Amber roof clearance lights
        for (cl in floatArrayOf(-0.20f, 0f, 0.20f)) {
            drawCircle(
                color = Color(0xFFFFC400),
                radius = carW * 0.038f,
                center = Offset(center.x + carW * cl, top + carH * 0.43f)
            )
        }

        // Open Pickup Cargo Bed with Metallic Rollbar
        drawRoundRect(
            color = Color(0xFF0F1524),
            topLeft = Offset(left + carW * 0.10f, top + carH * 0.62f),
            size = Size(carW * 0.80f, carH * 0.30f),
            cornerRadius = CornerRadius(6f)
        )
        drawLine(
            color = Color(0xFF94A3B8),
            start = Offset(left + carW * 0.10f, top + carH * 0.64f),
            end = Offset(left + carW * 0.90f, top + carH * 0.64f),
            strokeWidth = 6f,
            cap = StrokeCap.Round
        )

        // Bold Rear Tail Lamps
        for (tSide in floatArrayOf(-0.36f, 0.36f)) {
            val tx = center.x + carW * tSide
            drawRoundRect(
                color = Color(0xFFFF1744),
                topLeft = Offset(tx - carW * 0.11f, top + carH * 0.92f),
                size = Size(carW * 0.22f, 10f),
                cornerRadius = CornerRadius(4f)
            )
        }
    }
}
