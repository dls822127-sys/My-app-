package com.example.game

import androidx.compose.ui.graphics.Color
import com.example.audio.SynthSoundEngine
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class AnantDriftEngine(
    private val soundEngine: SynthSoundEngine
) {
    // Player state
    var playerX: Float = 0f
        private set
    var targetX: Float = 0f
        private set
    var playerBank: Float = 0f // -1f..1f roll tilt
        private set
    var shieldReady: Boolean = true
        private set
    var shieldRechargeTimer: Float = 0f
        private set
    var invulnTimer: Float = 0f
        private set
    var shieldFlashTimer: Float = 0f
        private set
    var shakeTimer: Float = 0f
        private set

    // Power-up timers
    var magnetTimer: Float = 0f
        private set
    var magnetMaxDuration: Float = 8f
        private set
    var boostTimer: Float = 0f
        private set
    var boostMaxDuration: Float = 6f
        private set

    // World scrolling & biome progression
    var trackScroll: Float = 0f
        private set
    var worldTime: Float = 0f
        private set
    var distanceFloat: Float = 0f
        private set
    var currentBiome: BiomePalette = BiomePalette.ALL.first()
        private set
    private var lastBiomeIndex: Int = 0

    // Scoring & persistence deltas
    var score: Long = 0L
        private set
    var runStars: Int = 0
        private set
    var streak: Int = 0
        private set
    var pendingStarsDelta: Int = 0
        private set
    var pendingDistanceDelta: Long = 0L
        private set
    private var lastFlushedDistance: Long = 0L

    // Upgrade levels (injected from storage)
    var magnetLevel: Int = 1
    var boostLevel: Int = 1
    var multiplierLevel: Int = 1
    var speedTier: SpeedTier = SpeedTier.FLOW
    var autoPilotEnabled: Boolean = true
    var cameraView: CameraView = CameraView.CHASE
    var carColorId: String = "cyan"
    var vehicleType: VehicleType = VehicleType.PATROL

    // Smoothly interpolated camera parameters
    var camHorizonFrac: Float = CameraView.CHASE.horizonFrac
        private set
    var camCarZ: Float = CameraView.CHASE.carZ
        private set
    var camScaleMul: Float = CameraView.CHASE.carScaleMul
        private set
    var camRoadMul: Float = CameraView.CHASE.roadWidthMul
        private set

    private var manualOverrideTimer: Float = 0f

    // Entities
    private var nextEntityId: Long = 1L
    private var spawnTimer: Float = 0f
    private var patternCounter: Int = 0

    val objects = mutableListOf<TrackObject>()
    val particles = mutableListOf<Particle>()
    val trail = ArrayDeque<TrailSample>(32)
    val banners = mutableListOf<FloatingBanner>()

    // Fixed background stars (normalized x, y, size, twinklePhase)
    val bgStars: List<FloatArray> = List(55) {
        floatArrayOf(
            Random.nextFloat(),
            Random.nextFloat() * 0.44f,
            1.2f + Random.nextFloat() * 2.2f,
            Random.nextFloat() * 6.28f
        )
    }

    private val lanes = floatArrayOf(-0.68f, 0f, 0.68f)

    fun startFreshRun() {
        score = 0L
        runStars = 0
        streak = 0
        distanceFloat = 0f
        lastFlushedDistance = 0L
        shieldReady = true
        shieldRechargeTimer = 0f
        invulnTimer = 0f
        magnetTimer = 0f
        boostTimer = 0f
        banners.clear()
    }

    fun onManualDrag(deltaNormalizedX: Float) {
        targetX = (targetX + deltaNormalizedX * 2.15f).coerceIn(-0.88f, 0.88f)
        if (autoPilotEnabled) {
            manualOverrideTimer = 2.5f
        }
    }

    fun setCameraViewWithBanner(view: CameraView) {
        cameraView = view
        soundEngine.playButtonTap()
        pushBanner("CAMERA: ${view.label}", Color(0xFF00E5FF))
    }

    fun setCarColorWithBanner(colorId: String) {
        carColorId = colorId
        val opt = CarColorOption.byId(colorId)
        soundEngine.playButtonTap()
        pushBanner("CAR: ${opt.label}", opt.primary)
    }

    fun setVehicleTypeWithBanner(type: VehicleType) {
        vehicleType = type
        soundEngine.playPowerUp()
        pushBanner(type.displayName, Color(0xFFFFD54F))
    }

    fun onManualTapSide(isRight: Boolean) {
        val currentLaneIdx = lanes.indices.minByOrNull { abs(lanes[it] - targetX) } ?: 1
        val nextIdx = (currentLaneIdx + if (isRight) 1 else -1).coerceIn(0, 2)
        targetX = lanes[nextIdx]
        if (autoPilotEnabled) {
            manualOverrideTimer = 2.5f
        }
    }

    fun consumePersistenceDelta(): Triple<Long, Int, Long> {
        val currentDist = distanceFloat.toLong()
        val distDelta = (currentDist - lastFlushedDistance).coerceAtLeast(0L)
        lastFlushedDistance = currentDist
        val stars = pendingStarsDelta
        pendingStarsDelta = 0
        pendingDistanceDelta = 0L
        return Triple(score, stars, distDelta)
    }

    fun currentMultiplier(): Int {
        val streakBonus = when {
            streak >= 40 -> 5
            streak >= 25 -> 4
            streak >= 14 -> 3
            streak >= 6 -> 2
            else -> 1
        }
        return streakBonus + (multiplierLevel - 1)
    }

    fun update(dtRaw: Float, isLivePlaying: Boolean) {
        val dt = dtRaw.coerceIn(0.001f, 0.05f)
        worldTime += dt

        val isBoosting = boostTimer > 0f
        val baseSpeed = (0.64f + (distanceFloat / 6000f).coerceAtMost(0.32f)) * speedTier.multiplier
        val zSpeed = baseSpeed * (if (isBoosting) 1.55f else 1.0f)

        trackScroll = (trackScroll + zSpeed * dt * 3.8f) % 1f

        if (isLivePlaying) {
            val distGain = zSpeed * dt * 62f
            distanceFloat += distGain
            score += (distGain * currentMultiplier() * 0.4f).toLong().coerceAtLeast(if (distGain > 0f) 1L else 0L)
        } else {
            // Keep gentle endless cruise in background on Title screen
            distanceFloat += zSpeed * dt * 18f
        }

        // Biome smooth interpolation every 500 meters
        val biomeSpan = 500f
        val biomeFloat = distanceFloat / biomeSpan
        val idxA = biomeFloat.toInt() % BiomePalette.ALL.size
        val idxB = (idxA + 1) % BiomePalette.ALL.size
        val frac = biomeFloat - biomeFloat.toInt()
        val transitionT = if (frac > 0.85f) (frac - 0.85f) / 0.15f else 0f
        currentBiome = BiomePalette.interpolate(BiomePalette.ALL[idxA], BiomePalette.ALL[idxB], transitionT)

        if (isLivePlaying && idxA != lastBiomeIndex) {
            lastBiomeIndex = idxA
            soundEngine.playBiomeShift()
            pushBanner("ZONE: ${BiomePalette.ALL[idxA].name}", currentBiome.gridLine)
        }

        // Smooth camera transitions
        val camLerp = (dt * 6.5f).coerceAtMost(1f)
        camHorizonFrac += (cameraView.horizonFrac - camHorizonFrac) * camLerp
        camCarZ += (cameraView.carZ - camCarZ) * camLerp
        camScaleMul += (cameraView.carScaleMul - camScaleMul) * camLerp
        camRoadMul += (cameraView.roadWidthMul - camRoadMul) * camLerp

        // Timers
        if (manualOverrideTimer > 0f) manualOverrideTimer = (manualOverrideTimer - dt).coerceAtLeast(0f)
        if (invulnTimer > 0f) invulnTimer = (invulnTimer - dt).coerceAtLeast(0f)
        if (shieldFlashTimer > 0f) shieldFlashTimer = (shieldFlashTimer - dt).coerceAtLeast(0f)
        if (shakeTimer > 0f) shakeTimer = (shakeTimer - dt).coerceAtLeast(0f)
        if (magnetTimer > 0f) magnetTimer = (magnetTimer - dt).coerceAtLeast(0f)
        if (boostTimer > 0f) boostTimer = (boostTimer - dt).coerceAtLeast(0f)

        if (!shieldReady) {
            shieldRechargeTimer -= dt
            if (shieldRechargeTimer <= 0f) {
                shieldReady = true
            }
        }

        // Auto-pilot steering (always active on Title background, or when toggled ON during play)
        val shouldAutoSteer = !isLivePlaying || (autoPilotEnabled && manualOverrideTimer <= 0f)
        if (shouldAutoSteer) {
            computeAutoPilotTarget()
        }

        // Smooth player steering & banking
        val prevX = playerX
        val steerLerp = (dt * 12f).coerceAtMost(1f)
        playerX += (targetX - playerX) * steerLerp
        val velX = (playerX - prevX) / dt
        val desiredBank = (velX * 0.28f).coerceIn(-1f, 1f)
        playerBank += (desiredBank - playerBank) * (dt * 14f).coerceAtMost(1f)

        // Update trail
        for (i in trail.indices) {
            trail[i] = trail[i].copy(age = trail[i].age + dt * 3.4f)
        }
        while (trail.isNotEmpty() && trail.first().age >= 1f) {
            trail.removeFirst()
        }
        trail.addLast(TrailSample(playerX, 0f))
        if (trail.size > 26) trail.removeFirst()

        // Spawn waves
        spawnTimer -= dt
        if (spawnTimer <= 0f) {
            spawnWave()
            spawnTimer = (0.72f / speedTier.multiplier).coerceIn(0.42f, 0.95f)
        }

        // Update objects & collisions
        val magnetPullRange = 0.42f + (magnetLevel - 1) * 0.14f
        val iter = objects.iterator()
        while (iter.hasNext()) {
            val obj = iter.next()
            // Traffic cars cruise forward along the highway so you overtake them naturally
            val relativeSpeed = if (obj.type == TrackObjectType.BARRIER) zSpeed * 0.72f else zSpeed
            obj.z -= relativeSpeed * dt

            // Magnet attraction for stars
            if (isLivePlaying && obj.active && obj.type == TrackObjectType.STAR_ORB && magnetTimer > 0f) {
                if (obj.z in -0.02f..magnetPullRange) {
                    obj.laneX += (playerX - obj.laneX) * (dt * 9.5f).coerceAtMost(1f)
                }
            }

            // Collision check at player Z plane (~0.07f)
            if (isLivePlaying && obj.active && obj.z in 0.01f..0.13f) {
                val hitWidth = if (obj.type == TrackObjectType.BARRIER) 0.27f else 0.34f
                if (abs(obj.laneX - playerX) < hitWidth) {
                    handleCollision(obj)
                }
            }

            if (obj.z < -0.15f || !obj.active) {
                iter.remove()
            }
        }

        // Ambient boost speed sparks
        if (isBoosting && Random.nextFloat() < 0.65f) {
            particles.add(
                Particle(
                    x = 0.5f + playerX * 0.32f + (Random.nextFloat() - 0.5f) * 0.08f,
                    y = 0.80f,
                    vx = (Random.nextFloat() - 0.5f) * 0.18f,
                    vy = 0.45f + Random.nextFloat() * 0.3f,
                    life = 0.7f,
                    size = 4f + Random.nextFloat() * 4f,
                    color = Color(0xFFFF2E93)
                )
            )
        }

        // Update particles
        val pIter = particles.iterator()
        while (pIter.hasNext()) {
            val p = pIter.next()
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.life -= dt * 1.9f
            if (p.life <= 0f) pIter.remove()
        }

        // Update floating banners
        val bIter = banners.iterator()
        while (bIter.hasNext()) {
            val b = bIter.next()
            b.life -= dt * 0.9f
            b.yOffset += dt * 42f
            if (b.life <= 0f) bIter.remove()
        }
    }

    private fun computeAutoPilotTarget() {
        // Score each lane: heavily penalize upcoming barriers, reward upcoming stars/powerups
        var bestLane = lanes[1]
        var bestUtility = Float.NEGATIVE_INFINITY

        for (lane in lanes) {
            var utility = -abs(lane - playerX) * 0.25f // gentle preference to stay smooth
            for (obj in objects) {
                if (!obj.active || obj.z < 0.0f || obj.z > 0.72f) continue
                val sameLane = abs(obj.laneX - lane) < 0.30f
                if (!sameLane) continue
                val proximity = 1f - (obj.z / 0.72f)
                when (obj.type) {
                    TrackObjectType.BARRIER -> {
                        if (boostTimer > 0f) {
                            utility += 1.5f * proximity // Smash barriers during boost!
                        } else {
                            utility -= 14f * (0.4f + proximity * 1.6f)
                        }
                    }
                    TrackObjectType.STAR_ORB -> utility += 2.8f * proximity
                    TrackObjectType.MAGNET_POWERUP,
                    TrackObjectType.BOOST_POWERUP,
                    TrackObjectType.SHIELD_POWERUP -> utility += 5.5f * proximity
                }
            }
            if (utility > bestUtility) {
                bestUtility = utility
                bestLane = lane
            }
        }
        targetX = bestLane
    }

    private fun spawnWave() {
        patternCounter++
        val openLane = Random.nextInt(3)
        val barrierCount = if (Random.nextFloat() < 0.42f) 2 else 1

        val barrierLanes = mutableSetOf<Int>()
        if (barrierCount == 1) {
            val bLane = (openLane + 1 + Random.nextInt(2)) % 3
            barrierLanes.add(bLane)
        } else {
            for (i in 0..2) {
                if (i != openLane) barrierLanes.add(i)
            }
        }

        for (bLane in barrierLanes) {
            objects.add(
                TrackObject(
                    id = nextEntityId++,
                    type = TrackObjectType.BARRIER,
                    laneX = lanes[bLane],
                    z = 1.05f
                )
            )
        }

        // Spawn a rewarding line of 3 Star Orbs in the open lane
        for (step in 0..2) {
            objects.add(
                TrackObject(
                    id = nextEntityId++,
                    type = TrackObjectType.STAR_ORB,
                    laneX = lanes[openLane],
                    z = 1.02f + step * 0.085f,
                    spinOffset = step * 0.7f
                )
            )
        }

        // Periodically spawn a Power-Up
        if (patternCounter % 7 == 0) {
            val pType = when (Random.nextInt(3)) {
                0 -> TrackObjectType.MAGNET_POWERUP
                1 -> TrackObjectType.BOOST_POWERUP
                else -> TrackObjectType.SHIELD_POWERUP
            }
            val pLane = Random.nextInt(3)
            if (pLane !in barrierLanes) {
                objects.add(
                    TrackObject(
                        id = nextEntityId++,
                        type = pType,
                        laneX = lanes[pLane],
                        z = 1.28f
                    )
                )
            }
        }
    }

    private fun handleCollision(obj: TrackObject) {
        obj.active = false
        val screenX = 0.5f + playerX * 0.32f
        val screenY = 0.77f

        when (obj.type) {
            TrackObjectType.STAR_ORB -> {
                runStars += 1
                pendingStarsDelta += 1
                streak += 1
                val pts = 10L * currentMultiplier()
                score += pts
                soundEngine.playStarPickup(streak % 8)
                spawnBurst(screenX, screenY, Color(0xFFFFD54F), 8)
            }
            TrackObjectType.MAGNET_POWERUP -> {
                magnetMaxDuration = 7.5f + (magnetLevel - 1) * 2.0f
                magnetTimer = magnetMaxDuration
                score += 40L * currentMultiplier()
                soundEngine.playPowerUp()
                pushBanner("MAGNET PULSE!", Color(0xFF00E5FF))
                spawnBurst(screenX, screenY, Color(0xFF00E5FF), 14)
            }
            TrackObjectType.BOOST_POWERUP -> {
                boostMaxDuration = 5.0f + (boostLevel - 1) * 1.5f
                boostTimer = boostMaxDuration
                score += 60L * currentMultiplier()
                soundEngine.playPowerUp()
                pushBanner("HYPER BOOST!", Color(0xFFFF2E93))
                spawnBurst(screenX, screenY, Color(0xFFFF2E93), 18)
            }
            TrackObjectType.SHIELD_POWERUP -> {
                shieldReady = true
                shieldRechargeTimer = 0f
                score += 35L * currentMultiplier()
                soundEngine.playPowerUp()
                pushBanner("SHIELD CHARGED!", Color(0xFF00E676))
                spawnBurst(screenX, screenY, Color(0xFF00E676), 14)
            }
            TrackObjectType.BARRIER -> {
                if (boostTimer > 0f) {
                    // Hyper Boost smashes through obstacles!
                    score += 50L * currentMultiplier()
                    shakeTimer = 0.16f
                    soundEngine.playSmash()
                    pushBanner("SMASH +${50 * currentMultiplier()}", Color(0xFFFF2E93))
                    spawnBurst(screenX, screenY, currentBiome.barrierCore, 20)
                } else if (invulnTimer <= 0f) {
                    // NON-STOP FLOW: Shield absorbs impact, resets streak, never halts the game!
                    streak = 0
                    shieldReady = false
                    shieldRechargeTimer = 6.5f
                    invulnTimer = 1.6f
                    shieldFlashTimer = 0.45f
                    shakeTimer = 0.28f
                    soundEngine.playShieldHit()
                    pushBanner("SHIELD ABSORBED!", Color(0xFFFFAB40))
                    spawnBurst(screenX, screenY, Color(0xFFFFAB40), 22)
                }
            }
        }
    }

    private fun spawnBurst(x: Float, y: Float, color: Color, count: Int) {
        if (particles.size > 110) return
        repeat(count) {
            val angle = Random.nextFloat() * 6.283f
            val speed = 0.14f + Random.nextFloat() * 0.38f
            particles.add(
                Particle(
                    x = x,
                    y = y,
                    vx = cos(angle) * speed,
                    vy = sin(angle) * speed,
                    life = 0.85f + Random.nextFloat() * 0.25f,
                    size = 4f + Random.nextFloat() * 6f,
                    color = color
                )
            )
        }
    }

    private fun pushBanner(text: String, color: Color) {
        banners.add(FloatingBanner(text = text, color = color, life = 1.0f))
        if (banners.size > 3) banners.removeAt(0)
    }

    fun snapshot(): HudSnapshot = HudSnapshot(
        score = score,
        runStars = runStars,
        distanceMeters = distanceFloat.toLong(),
        streak = streak,
        multiplier = currentMultiplier(),
        biomeName = currentBiome.name,
        magnetRemainingFrac = if (magnetMaxDuration > 0f) (magnetTimer / magnetMaxDuration).coerceIn(0f, 1f) else 0f,
        boostRemainingFrac = if (boostMaxDuration > 0f) (boostTimer / boostMaxDuration).coerceIn(0f, 1f) else 0f,
        shieldReady = shieldReady,
        autoPilotActive = autoPilotEnabled,
        manualOverrideActive = autoPilotEnabled && manualOverrideTimer > 0f,
        speedTier = speedTier,
        cameraView = cameraView,
        carColorId = carColorId,
        vehicleType = vehicleType
    )
}
