package com.example.game

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

enum class GameMode {
    TITLE,
    PLAYING,
    PAUSED,
    HANGAR
}

enum class SpeedTier(val label: String, val multiplier: Float) {
    CHILL("CHILL", 0.78f),
    FLOW("FLOW", 1.0f),
    HYPER("HYPER", 1.32f);

    fun next(): SpeedTier = entries[(ordinal + 1) % entries.size]
}

enum class CameraView(
    val label: String,
    val horizonFrac: Float,
    val carZ: Float,
    val carScaleMul: Float,
    val roadWidthMul: Float
) {
    CHASE("CHASE", 0.43f, 0.07f, 1.0f, 1.0f),
    CLOSE("TURBO", 0.36f, 0.045f, 1.26f, 1.16f),
    TOP("DRONE", 0.22f, 0.14f, 0.84f, 0.84f),
    HOOD("FRONT", 0.52f, 0.025f, 0.62f, 1.26f);

    fun next(): CameraView = entries[(ordinal + 1) % entries.size]

    companion object {
        fun byNameOrDefault(name: String?): CameraView =
            entries.find { it.name == name } ?: CHASE
    }
}

enum class VehicleType(
    val shortLabel: String,
    val displayName: String
) {
    PATROL("PATROL", "PATROL CAR 🚨"),
    THAR("THAR", "THAR 4x4 SUV"),
    LAMBO("LAMBO", "LAMBO SUPERCAR"),
    FORMULA("F1", "FORMULA RACER"),
    TRUCK("TRUCK", "MONSTER 4x4");

    fun next(): VehicleType = entries[(ordinal + 1) % entries.size]

    companion object {
        fun byNameOrDefault(name: String?): VehicleType =
            entries.find { it.name == name } ?: PATROL
    }
}

data class CarColorOption(
    val id: String,
    val label: String,
    val primary: Color,
    val accent: Color,
    val isRainbow: Boolean = false
) {
    companion object {
        val ALL = listOf(
            CarColorOption("rainbow", "ALL COLORS", Color(0xFFFF2E93), Color(0xFF00E5FF), isRainbow = true),
            CarColorOption("red", "FERRARI RED", Color(0xFFE01E28), Color(0xFF6E0A10)),
            CarColorOption("gold", "LAMBO YELLOW", Color(0xFFF5C400), Color(0xFF7D5900)),
            CarColorOption("black", "JET BLACK", Color(0xFF2D3442), Color(0xFF0E1117)),
            CarColorOption("white", "PEARL WHITE", Color(0xFFF1F5F9), Color(0xFF64748B)),
            CarColorOption("blue", "RACING BLUE", Color(0xFF1D63ED), Color(0xFF0B2A6B)),
            CarColorOption("green", "RACING GREEN", Color(0xFF16A34A), Color(0xFF064E23)),
            CarColorOption("orange", "SUNSET ORANGE", Color(0xFFF97316), Color(0xFF7C2D12))
        )

        fun byId(id: String): CarColorOption = ALL.find { it.id == id } ?: ALL[1]
    }
}

data class BiomePalette(
    val name: String,
    val skyTop: Color,
    val skyMid: Color,
    val skyHorizon: Color,
    val sunColor: Color,
    val mountainFar: Color,
    val mountainNear: Color,
    val trackBase: Color,     // Real asphalt road color
    val gridLine: Color,      // Roadside grass / terrain color
    val railGlow: Color,      // Shoulder / curb accent color
    val barrierCore: Color,
    val barrierEdge: Color,
    val isNight: Boolean = false
) {
    companion object {
        val ALL = listOf(
            BiomePalette(
                name = "SUNNY HIGHWAY",
                skyTop = Color(0xFF2471D6),
                skyMid = Color(0xFF66B2F5),
                skyHorizon = Color(0xFFD6ECFF),
                sunColor = Color(0xFFFFF1A8),
                mountainFar = Color(0xFF5D849E),
                mountainNear = Color(0xFF3A6B4C),
                trackBase = Color(0xFF2D3138),
                gridLine = Color(0xFF2F6B3B),   // Lush green grass
                railGlow = Color(0xFFD93829),   // Red-white highway curb
                barrierCore = Color(0xFFFF6D00),
                barrierEdge = Color(0xFFFFF8E1),
                isNight = false
            ),
            BiomePalette(
                name = "GOLDEN SUNSET",
                skyTop = Color(0xFF2C3F6B),
                skyMid = Color(0xFFC86440),
                skyHorizon = Color(0xFFF8BA6D),
                sunColor = Color(0xFFFFD56B),
                mountainFar = Color(0xFF6D454D),
                mountainNear = Color(0xFF3E3138),
                trackBase = Color(0xFF2A2B32),
                gridLine = Color(0xFF4F5432),   // Warm golden-olive grass
                railGlow = Color(0xFFE6A100),   // Yellow-black curb
                barrierCore = Color(0xFFFF5722),
                barrierEdge = Color(0xFFFFFFFF),
                isNight = false
            ),
            BiomePalette(
                name = "DESERT EXPRESSWAY",
                skyTop = Color(0xFF2166A8),
                skyMid = Color(0xFF6DAAD9),
                skyHorizon = Color(0xFFEDD6B4),
                sunColor = Color(0xFFFFF3C4),
                mountainFar = Color(0xFFB07B56),
                mountainNear = Color(0xFF8C5A38),
                trackBase = Color(0xFF303238),
                gridLine = Color(0xFFB88A58),   // Warm desert sand terrain
                railGlow = Color(0xFFD93829),
                barrierCore = Color(0xFFFF6D00),
                barrierEdge = Color(0xFFFFFFFF),
                isNight = false
            ),
            BiomePalette(
                name = "ALPINE MORNING",
                skyTop = Color(0xFF335B8C),
                skyMid = Color(0xFF7FA6CC),
                skyHorizon = Color(0xFFF0D9C0),
                sunColor = Color(0xFFFFEAB3),
                mountainFar = Color(0xFF536E8A),
                mountainNear = Color(0xFF2E5448),
                trackBase = Color(0xFF2B2F36),
                gridLine = Color(0xFF295C3A),   // Deep pine-green grass
                railGlow = Color(0xFFD93829),
                barrierCore = Color(0xFFFF6D00),
                barrierEdge = Color(0xFFFFFFFF),
                isNight = false
            ),
            BiomePalette(
                name = "NIGHT EXPRESSWAY",
                skyTop = Color(0xFF091226),
                skyMid = Color(0xFF152544),
                skyHorizon = Color(0xFF243B61),
                sunColor = Color(0xFFE8F0FF),   // Bright full moon
                mountainFar = Color(0xFF1B2B45),
                mountainNear = Color(0xFF111D30),
                trackBase = Color(0xFF1E222A),
                gridLine = Color(0xFF14291D),   // Dark night grass
                railGlow = Color(0xFFE6A100),
                barrierCore = Color(0xFFFF5252),
                barrierEdge = Color(0xFFFFFFFF),
                isNight = true
            )
        )

        fun interpolate(a: BiomePalette, b: BiomePalette, t: Float): BiomePalette {
            val f = t.coerceIn(0f, 1f)
            return BiomePalette(
                name = if (f < 0.5f) a.name else b.name,
                skyTop = lerp(a.skyTop, b.skyTop, f),
                skyMid = lerp(a.skyMid, b.skyMid, f),
                skyHorizon = lerp(a.skyHorizon, b.skyHorizon, f),
                sunColor = lerp(a.sunColor, b.sunColor, f),
                mountainFar = lerp(a.mountainFar, b.mountainFar, f),
                mountainNear = lerp(a.mountainNear, b.mountainNear, f),
                trackBase = lerp(a.trackBase, b.trackBase, f),
                gridLine = lerp(a.gridLine, b.gridLine, f),
                railGlow = lerp(a.railGlow, b.railGlow, f),
                barrierCore = lerp(a.barrierCore, b.barrierCore, f),
                barrierEdge = lerp(a.barrierEdge, b.barrierEdge, f),
                isNight = if (f < 0.5f) a.isNight else b.isNight
            )
        }
    }
}

data class GliderSkin(
    val id: String,
    val name: String,
    val subtitle: String,
    val cost: Int,
    val bodyPrimary: Color,
    val bodyAccent: Color,
    val cockpitColor: Color,
    val trailColor: Color,
    val shapeStyle: Int // 0 = Delta, 1 = Twin-Wing, 2 = Diamond Prism, 3 = Phoenix Swept
) {
    companion object {
        val ALL = listOf(
            GliderSkin(
                id = "nova_dart",
                name = "Turbo Patrol",
                subtitle = "Interceptor car with red-blue siren",
                cost = 0,
                bodyPrimary = Color(0xFF00E5FF),
                bodyAccent = Color(0xFF10255E),
                cockpitColor = Color(0xFFE0F7FA),
                trailColor = Color(0xFF00E5FF),
                shapeStyle = 0
            ),
            GliderSkin(
                id = "solar_wing",
                name = "Solar GT",
                subtitle = "Golden supercar with aero spoiler",
                cost = 60,
                bodyPrimary = Color(0xFFFFB300),
                bodyAccent = Color(0xFF7A2E00),
                cockpitColor = Color(0xFFFFF8E1),
                trailColor = Color(0xFFFFD54F),
                shapeStyle = 1
            ),
            GliderSkin(
                id = "aurora_prism",
                name = "Emerald Drift",
                subtitle = "Neon green street drift racer",
                cost = 140,
                bodyPrimary = Color(0xFF00E676),
                bodyAccent = Color(0xFF004D40),
                cockpitColor = Color(0xFFE8F5E9),
                trailColor = Color(0xFF69F0AE),
                shapeStyle = 2
            ),
            GliderSkin(
                id = "obsidian_phoenix",
                name = "Phoenix Hyper",
                subtitle = "Hyper-magenta apex supercar",
                cost = 260,
                bodyPrimary = Color(0xFFFF2E93),
                bodyAccent = Color(0xFF2B0A3D),
                cockpitColor = Color(0xFFFFD1E8),
                trailColor = Color(0xFFFF2E93),
                shapeStyle = 3
            )
        )

        fun byId(id: String): GliderSkin = ALL.find { it.id == id } ?: ALL.first()
    }
}

enum class TrackObjectType {
    STAR_ORB,
    BARRIER,
    MAGNET_POWERUP,
    BOOST_POWERUP,
    SHIELD_POWERUP
}

data class TrackObject(
    val id: Long,
    val type: TrackObjectType,
    var laneX: Float, // -0.68f, 0f, +0.68f (can drift toward player if magnetized)
    var z: Float,     // 1.0f at horizon -> 0.0f at player -> -0.15f behind camera
    var active: Boolean = true,
    val spinOffset: Float = 0f
)

data class Particle(
    var x: Float,      // Normalized screen X (0..1)
    var y: Float,      // Normalized screen Y (0..1)
    var vx: Float,
    var vy: Float,
    var life: Float,   // 1.0 -> 0.0
    var size: Float,
    val color: Color
)

data class TrailSample(
    val laneX: Float,
    val age: Float
)

data class FloatingBanner(
    val text: String,
    val color: Color,
    var life: Float,   // 1.0 -> 0.0
    var yOffset: Float = 0f
)

data class HudSnapshot(
    val score: Long = 0L,
    val runStars: Int = 0,
    val distanceMeters: Long = 0L,
    val streak: Int = 0,
    val multiplier: Int = 1,
    val biomeName: String = BiomePalette.ALL.first().name,
    val magnetRemainingFrac: Float = 0f,
    val boostRemainingFrac: Float = 0f,
    val shieldReady: Boolean = true,
    val autoPilotActive: Boolean = false,
    val manualOverrideActive: Boolean = false,
    val speedTier: SpeedTier = SpeedTier.FLOW,
    val cameraView: CameraView = CameraView.CHASE,
    val carColorId: String = "cyan",
    val vehicleType: VehicleType = VehicleType.PATROL
)
