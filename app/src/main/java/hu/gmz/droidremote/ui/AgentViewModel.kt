package hu.gmz.droidremote.ui

import android.app.Application
import android.graphics.BitmapFactory
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import hu.gmz.droidremote.act.Action
import hu.gmz.droidremote.act.ActionCodec
import hu.gmz.droidremote.observe.Observation
import hu.gmz.droidremote.observe.ScreenCapture.CaptureResult
import hu.gmz.droidremote.plan.AgentConfig
import hu.gmz.droidremote.plan.AgentLoop
import hu.gmz.droidremote.plan.ModelClient
import hu.gmz.droidremote.plan.PromptBuilder
import hu.gmz.droidremote.service.ObserverService
import hu.gmz.droidremote.trace.DebugExport
import hu.gmz.droidremote.trace.RunStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AgentViewModel(application: Application) : AndroidViewModel(application) {

    private val config = AgentConfig(application)
    private val prompts = PromptBuilder(application)
    private val client = ModelClient(config)
    private val store = RunStore(application)

    var accessibilityEnabled by mutableStateOf(ObserverService.enabled(application))
        private set
    var serviceConnected by mutableStateOf(ObserverService.instance != null)
        private set
    var treeText by mutableStateOf("")
        private set
    var nodeCount by mutableStateOf(0)
        private set
    var packageName by mutableStateOf("")
        private set
    var screenshot by mutableStateOf<ImageBitmap?>(null)
        private set
    var log by mutableStateOf("")
        private set
    var running by mutableStateOf(false)
        private set
    var models by mutableStateOf<List<String>>(emptyList())
        private set

    var goal by mutableStateOf("")
    var baseUrl by mutableStateOf(config.baseUrl)
    var apiKey by mutableStateOf(config.apiKey)
    var model by mutableStateOf(config.model)
    var maxSteps by mutableStateOf(config.maxSteps.toString())

    private var lastObservation: Observation? = null
    private var loop: AgentLoop? = null

    fun refreshStatus() {
        accessibilityEnabled = ObserverService.enabled(getApplication())
        serviceConnected = ObserverService.instance != null
    }

    fun refreshObservation() {
        viewModelScope.launch {
            val service = ObserverService.instance ?: run {
                logLine("nincs futó a11y szolgáltatás")
                refreshStatus()
                return@launch
            }
            val observation = withContext(Dispatchers.Default) { service.observe() }
            if (observation == null) {
                logLine("nem olvasható képernyő")
                return@launch
            }
            lastObservation = observation
            treeText = observation.treeText
            nodeCount = observation.nodeCount
            packageName = observation.packageName
            serviceConnected = true
            accessibilityEnabled = true
            DebugExport.text(getApplication(), "observe.txt", observation.treeText)
            DebugExport.text(
                getApplication(),
                "observe-meta.json",
                """{"pkg":"${observation.packageName}","nodes":${observation.nodeCount},"chars":${observation.treeText.length},"screen":"${observation.screen.width}x${observation.screen.height}"}""",
            )
            logLine("${observation.packageName} · ${observation.nodeCount} node · ${observation.treeText.length} byte")
        }
    }

    fun grabScreenshot() {
        viewModelScope.launch {
            val service = ObserverService.instance ?: return@launch
            logLine("képernyőkép…")
            when (val result = service.capture.capture()) {
                is CaptureResult.Success -> {
                    val bitmap = BitmapFactory.decodeByteArray(result.jpeg, 0, result.jpeg.size)
                    if (bitmap != null) screenshot = bitmap.asImageBitmap()
                    DebugExport.bytes(getApplication(), "last.jpg", result.jpeg)
                    logLine("kép: ${result.jpeg.size / 1024} KB")
                }
                is CaptureResult.Failure -> logLine("kép hiba: ${result.reason}")
            }
        }
    }

    fun tapNode(index: Int) {
        viewModelScope.launch {
            val service = ObserverService.instance ?: return@launch
            val observation = lastObservation ?: withContext(Dispatchers.Default) { service.observe() } ?: return@launch
            val result = service.act(Action.Tap(index), observation)
            DebugExport.text(getApplication(), "last-action.txt", "tap @$index -> ${result.detail}")
            logLine("tap @$index → ${result.detail}")
            delay(300)
            refreshObservation()
        }
    }

    fun runScript(raw: String) {
        val actions = ActionCodec.parseArray(raw).getOrElse { error ->
            logLine("script hiba: ${error.message}")
            return
        }
        viewModelScope.launch {
            val service = ObserverService.instance ?: return@launch
            logLine("script: ${actions.size} akció")
            for ((position, action) in actions.withIndex()) {
                if (action is Action.Screenshot) {
                    when (val shot = service.capture.capture()) {
                        is CaptureResult.Success -> {
                            DebugExport.bytes(getApplication(), "last.jpg", shot.jpeg)
                            logLine("${position + 1}. shot → ${shot.jpeg.size / 1024} KB")
                        }
                        is CaptureResult.Failure -> logLine("${position + 1}. shot hiba: ${shot.reason}")
                    }
                    delay(300)
                    continue
                }
                val observation = withContext(Dispatchers.Default) { service.observe() } ?: break
                val result = service.act(action, observation)
                logLine("${position + 1}. ${ActionCodec.describe(action)} → ${result.detail}")
                delay(450)
            }
            refreshObservation()
        }
    }

    fun saveConfig() {
        config.baseUrl = baseUrl
        config.apiKey = apiKey
        config.model = model
        config.maxSteps = maxSteps.toIntOrNull() ?: AgentConfig.DEFAULT_MAX_STEPS
        logLine("beállítás mentve")
    }

    fun loadModels() {
        saveConfig()
        viewModelScope.launch {
            logLine("modellek lekérése…")
            client.listModels()
                .onSuccess { list ->
                    models = list
                    logLine("${list.size} modell")
                }
                .onFailure { logLine("lista hiba: ${it.message}") }
        }
    }

    fun start() {
        if (running) return
        saveConfig()
        val service = ObserverService.instance ?: run {
            logLine("előbb engedélyezd az a11y szolgáltatást")
            return
        }
        if (config.model.isBlank()) {
            logLine("nincs modell beállítva")
            return
        }
        val agent = AgentLoop(service, config, prompts, client, store)
        loop = agent
        running = true
        viewModelScope.launch {
            val report = agent.run(goal.ifBlank { "Olvasd le, mi van a képernyőn" }) { line ->
                viewModelScope.launch { logLine(line) }
            }
            running = false
            logLine("── ${if (report.ok) "KÉSZ" else "VÉGE"}: ${report.summary} (${report.steps} lépés, ${report.runId})")
            refreshObservation()
        }
    }

    fun stop() {
        loop?.requestStop()
        logLine("stop kérve")
    }

    private fun logLine(line: String) {
        log = (log + line + "\n").takeLast(16_000)
        DebugExport.text(getApplication(), "log.txt", log)
    }
}
