package com.example.game

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.SynthSoundEngine
import com.example.data.GameStorage
import com.example.data.SavedProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val storage = GameStorage(application.applicationContext)
    val soundEngine = SynthSoundEngine()
    val engine = AnantDriftEngine(soundEngine)

    val savedProgress: StateFlow<SavedProgress> = storage.progressFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SavedProgress()
    )

    private val _gameMode = MutableStateFlow(GameMode.PLAYING)
    val gameMode: StateFlow<GameMode> = _gameMode.asStateFlow()

    private val _hud = MutableStateFlow(engine.snapshot())
    val hud: StateFlow<HudSnapshot> = _hud.asStateFlow()

    private val _frameTick = MutableStateFlow(0L)
    val frameTick: StateFlow<Long> = _frameTick.asStateFlow()

    private var returnModeFromHangar: GameMode = GameMode.TITLE
    private var hudAccumulator: Float = 0f
    private var saveAccumulator: Float = 0f

    private var isAppInForeground: Boolean = true

    init {
        viewModelScope.launch {
            storage.progressFlow.collect { progress ->
                soundEngine.enabled = progress.soundEnabled
                engine.magnetLevel = progress.magnetLevel
                engine.boostLevel = progress.boostLevel
                engine.multiplierLevel = progress.multiplierLevel
                engine.autoPilotEnabled = progress.autoPilotEnabled
                engine.carColorId = progress.carColorId
                engine.cameraView = CameraView.byNameOrDefault(progress.cameraViewName)
                engine.vehicleType = VehicleType.byNameOrDefault(progress.vehicleTypeName)
                _hud.value = engine.snapshot()
                syncMusicPlayback()
            }
        }
    }

    private fun syncMusicPlayback() {
        val shouldPlay = isAppInForeground && _gameMode.value == GameMode.PLAYING
        soundEngine.setMusicPlaying(shouldPlay)
    }

    fun onAppResumed() {
        isAppInForeground = true
        syncMusicPlayback()
    }

    fun onAppPaused() {
        isAppInForeground = false
        syncMusicPlayback()
        flushProgress()
    }

    fun stepFrame(dtSeconds: Float) {
        val mode = _gameMode.value
        if (mode == GameMode.PAUSED || mode == GameMode.HANGAR) return

        val isPlaying = mode == GameMode.PLAYING
        engine.update(dtSeconds, isLivePlaying = isPlaying)
        _frameTick.value = _frameTick.value + 1L

        hudAccumulator += dtSeconds
        if (hudAccumulator >= 0.06f) {
            hudAccumulator = 0f
            _hud.value = engine.snapshot()
        }

        if (isPlaying) {
            saveAccumulator += dtSeconds
            if (saveAccumulator >= 4.0f) {
                saveAccumulator = 0f
                flushProgress()
            }
        }
    }

    fun startDrift() {
        soundEngine.playButtonTap()
        engine.startFreshRun()
        _gameMode.value = GameMode.PLAYING
        _hud.value = engine.snapshot()
        syncMusicPlayback()
    }

    fun pauseGame() {
        if (_gameMode.value == GameMode.PLAYING) {
            soundEngine.playButtonTap()
            flushProgress()
            _gameMode.value = GameMode.PAUSED
            syncMusicPlayback()
        }
    }

    fun resumeGame() {
        soundEngine.playButtonTap()
        _gameMode.value = GameMode.PLAYING
        syncMusicPlayback()
    }

    fun returnToTitle() {
        soundEngine.playButtonTap()
        flushProgress()
        _gameMode.value = GameMode.TITLE
        syncMusicPlayback()
    }

    fun openHangar() {
        soundEngine.playButtonTap()
        flushProgress()
        returnModeFromHangar = if (_gameMode.value == GameMode.PLAYING) GameMode.PAUSED else _gameMode.value
        _gameMode.value = GameMode.HANGAR
        syncMusicPlayback()
    }

    fun closeHangar() {
        soundEngine.playButtonTap()
        _gameMode.value = returnModeFromHangar
        syncMusicPlayback()
    }

    fun toggleSound() {
        val next = !savedProgress.value.soundEnabled
        soundEngine.enabled = next
        if (next) soundEngine.playButtonTap()
        syncMusicPlayback()
        viewModelScope.launch {
            storage.setSoundEnabled(next)
        }
    }

    fun toggleAutoPilot() {
        soundEngine.playButtonTap()
        val next = !engine.autoPilotEnabled
        engine.autoPilotEnabled = next
        _hud.value = engine.snapshot()
        viewModelScope.launch {
            storage.setAutoPilotEnabled(next)
        }
    }

    fun cycleSpeedTier() {
        soundEngine.playButtonTap()
        engine.speedTier = engine.speedTier.next()
        _hud.value = engine.snapshot()
    }

    fun cycleCameraView() {
        val next = engine.cameraView.next()
        engine.setCameraViewWithBanner(next)
        _hud.value = engine.snapshot()
        viewModelScope.launch {
            storage.setCameraViewName(next.name)
        }
    }

    fun selectCarColor(colorId: String) {
        engine.setCarColorWithBanner(colorId)
        _hud.value = engine.snapshot()
        viewModelScope.launch {
            storage.setCarColorId(colorId)
        }
    }

    fun selectVehicleType(type: VehicleType) {
        engine.setVehicleTypeWithBanner(type)
        _hud.value = engine.snapshot()
        viewModelScope.launch {
            storage.setVehicleTypeName(type.name)
        }
    }

    fun onDragSteer(deltaNormalizedX: Float) {
        if (_gameMode.value == GameMode.PLAYING) {
            engine.onManualDrag(deltaNormalizedX)
        }
    }

    fun onTapSide(isRight: Boolean) {
        if (_gameMode.value == GameMode.PLAYING) {
            engine.onManualTapSide(isRight)
        }
    }

    fun selectOrUnlockSkin(skin: GliderSkin) {
        viewModelScope.launch {
            val ok = storage.unlockAndSelectSkin(skin.id, skin.cost)
            if (ok) soundEngine.playPowerUp() else soundEngine.playShieldHit()
        }
    }

    fun upgradeStat(statKey: String, cost: Int) {
        viewModelScope.launch {
            val ok = storage.upgradeStat(statKey, cost)
            if (ok) soundEngine.playPowerUp() else soundEngine.playShieldHit()
        }
    }

    fun flushProgress() {
        val (score, starsDelta, distDelta) = engine.consumePersistenceDelta()
        if (score > 0L || starsDelta > 0 || distDelta > 0L) {
            viewModelScope.launch {
                storage.saveRunDelta(score, starsDelta, distDelta)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        soundEngine.release()
    }
}
