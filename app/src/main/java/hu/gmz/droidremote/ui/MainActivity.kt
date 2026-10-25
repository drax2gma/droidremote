package hu.gmz.droidremote.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import hu.gmz.droidremote.service.ObserverService

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                DroidRemoteApp()
            }
        }
    }
}

@Composable
private fun DroidRemoteApp(agent: AgentViewModel = viewModel()) {
    var tab by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        agent.refreshStatus()
        agent.refreshObservation()
    }

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        TabRow(selectedTabIndex = tab) {
            listOf("Beállítás", "Megfigyelés", "Agent").forEachIndexed { index, title ->
                Tab(
                    selected = tab == index,
                    onClick = { tab = index },
                    text = { Text(title) },
                )
            }
        }
        when (tab) {
            0 -> SetupTab(agent)
            1 -> ObserveTab(agent)
            else -> AgentTab(agent)
        }
    }
}

@Composable
private fun SetupTab(agent: AgentViewModel) {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Állapot", style = MaterialTheme.typography.titleMedium)
        StatusRow("Akadálymentesítési engedély", agent.accessibilityEnabled)
        StatusRow("Szolgáltatás csatlakozva", agent.serviceConnected)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { ObserverService.openSettings(context) }) { Text("Engedélyezés") }
            OutlinedButton(onClick = {
                agent.refreshStatus()
                agent.refreshObservation()
            }) { Text("Újraellenőrzés") }
        }

        HorizontalDivider()

        Text("Modell", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = agent.baseUrl,
            onValueChange = { agent.baseUrl = it },
            label = { Text("OpenAI-kompatibilis base URL") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = agent.apiKey,
            onValueChange = { agent.apiKey = it },
            label = { Text("API kulcs") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = agent.model,
            onValueChange = { agent.model = it },
            label = { Text("Modell") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = agent.maxSteps,
            onValueChange = { agent.maxSteps = it.filter(Char::isDigit) },
            label = { Text("Lépés-plafon") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { agent.saveConfig() }) { Text("Mentés") }
            OutlinedButton(onClick = { agent.loadModels() }) { Text("Modellek") }
        }
        if (agent.models.isNotEmpty()) {
            Text(agent.models.joinToString("\n"), fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        }

        HorizontalDivider()
        Text(
            "A kulcs egyszerű SharedPreferences-ben van (app-private), nincs Android Keystore-ba zárva.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun ObserveTab(agent: AgentViewModel) {
    var tapIndex by remember { mutableStateOf("") }
    var script by remember {
        mutableStateOf(
            """[
  {"a":"key","k":"HOME"},
  {"a":"wait","ms":900},
  {"a":"shot"}
]"""
        )
    }

    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { agent.refreshObservation() }) { Text("Frissítés") }
            OutlinedButton(onClick = { agent.grabScreenshot() }) { Text("Képernyőkép") }
        }

        Text(
            "${agent.packageName} · ${agent.nodeCount} node · ${agent.treeText.length} byte",
            style = MaterialTheme.typography.bodySmall,
        )

        agent.screenshot?.let { bitmap ->
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp),
                contentScale = ContentScale.Fit,
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = tapIndex,
                onValueChange = { tapIndex = it.filter(Char::isDigit) },
                label = { Text("node #") },
                singleLine = true,
                modifier = Modifier.width(120.dp),
            )
            Button(onClick = { tapIndex.toIntOrNull()?.let(agent::tapNode) }) { Text("Koppintás") }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(10.dp)) {
                Text("Script (modell nélkül)", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = script,
                    onValueChange = { script = it },
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 200.dp),
                )
                TextButton(onClick = { agent.runScript(script) }) { Text("Futtatás") }
            }
        }

        HorizontalDivider()

        SelectionContainer {
            Text(
                text = agent.treeText.ifBlank { "(nincs megfigyelés)" },
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
            )
        }
    }
}

@Composable
private fun AgentTab(agent: AgentViewModel) {
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = agent.goal,
            onValueChange = { agent.goal = it },
            label = { Text("Feladat") },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { agent.start() }, enabled = !agent.running) { Text("Indítás") }
            OutlinedButton(onClick = { agent.stop() }, enabled = agent.running) { Text("Stop") }
            Text(
                if (agent.running) "fut…" else "áll",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 14.dp),
            )
        }

        HorizontalDivider()

        SelectionContainer {
            Text(
                text = agent.log.ifBlank { "(nincs napló)" },
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
            )
        }
    }
}

@Composable
private fun StatusRow(label: String, ok: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(if (ok) "✅" else "❌", modifier = Modifier.width(28.dp))
        Text(label)
    }
}
