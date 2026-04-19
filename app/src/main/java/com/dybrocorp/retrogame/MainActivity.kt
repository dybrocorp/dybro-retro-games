package com.dybrocorp.retrogame

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.Surface
import android.view.SurfaceView
import android.view.SurfaceHolder
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.border
import kotlin.math.roundToInt
import kotlin.math.abs
import kotlin.math.sqrt
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.RoundedCornerShape

class MainActivity : ComponentActivity() {
    // Colores Premium
    private val PremiumDark = Color(0xFF0F0F0F)
    private val PremiumBlue = Color(0xFF2196F3)

    // Retro Joypad IDs
    private val RETRO_DEVICE_ID_JOYPAD_B = 0
    private val RETRO_DEVICE_ID_JOYPAD_Y = 1
    private val RETRO_DEVICE_ID_JOYPAD_SELECT = 2
    private val RETRO_DEVICE_ID_JOYPAD_START = 3
    private val RETRO_DEVICE_ID_JOYPAD_UP = 4
    private val RETRO_DEVICE_ID_JOYPAD_DOWN = 5
    private val RETRO_DEVICE_ID_JOYPAD_LEFT = 6
    private val RETRO_DEVICE_ID_JOYPAD_RIGHT = 7
    private val RETRO_DEVICE_ID_JOYPAD_A = 8
    private val RETRO_DEVICE_ID_JOYPAD_X = 9
    private val RETRO_DEVICE_ID_JOYPAD_L = 10
    private val RETRO_DEVICE_ID_JOYPAD_R = 11

    data class GamepadSkinColors(
        val a: Color, val b: Color, val dpad: Color, val triggers: Color, val startSelect: Color
    )

    private fun getSkinColors(skinName: String): GamepadSkinColors {
        return when (skinName) {
            "Oscuro" -> GamepadSkinColors(Color(0xFF424242), Color(0xFF424242), Color(0xFF212121), Color(0xFF424242), Color(0xFF424242))
            "Neón" -> GamepadSkinColors(Color(0xFF00E676), Color(0xFFD500F9), Color(0xFF00B0FF), Color(0xFF651FFF), Color(0xFFF50057))
            "Transparente" -> GamepadSkinColors(Color.Transparent, Color.Transparent, Color.Transparent, Color.Transparent, Color.Transparent)
            else -> GamepadSkinColors(Color(0xFF388E3C), Color(0xFFD32F2F), Color(0xFF212121), Color(0xFF757575), Color(0xFF616161)) // Clásico
        }
    }

    private val libraryManager = LibraryManager()
    private var gamesList = mutableStateListOf<Game>()
    private var inGame = mutableStateOf(false)
    private var currentGame = mutableStateOf<Game?>(null)
    private var selectedCorePath = mutableStateOf<String?>(null)

    private val corePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { 
            copyFileToInternalStorage(it, "core.so")?.let { path ->
                selectedCorePath.value = path
                loadCore(path)
            }
        }
    }

    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        uri?.let { 
            // En una app real, usaríamos DocumentFile para escanear el árbol de URIs
            // Por simplicidad en este prototipo, pedimos al usuario que elija archivos individuales 
            // o implementamos un escaneo básico si tenemos permisos de archivos.
            // Para este demo, permitiremos elegir múltiples archivos.
        }
    }

    private val multiGamePicker = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris: List<Uri> ->
        uris.forEach { uri ->
            val fileName = getFileName(uri)
            val path = if (fileName?.endsWith(".zip", ignoreCase = true) == true) {
                extractZip(uri)
            } else {
                copyFileToInternalStorage(uri, fileName ?: "game.rom")
            }
            
            path?.let { p ->
                val ext = File(p).extension.lowercase()
                val system = GameSystem.fromExtension(ext)
                
                // Solo añadir si el sistema es reconocido (evitar .txt, .rar no extraídos, etc)
                if (system != GameSystem.NES || ext == "nes") { // Fallback de NES es por defecto, pero validamos ext
                    if (system == GameSystem.NES && ext != "nes" && ext != "zip") {
                         // Es un archivo desconocido detectado como NES por el fallback
                         return@let
                    }
                    val installedPath = installGame(p)
                    val newGame = Game(File(installedPath).nameWithoutExtension, installedPath, system)
                    gamesList.add(newGame)
                    libraryManager.saveLibrary(this, gamesList.toList())

                    lifecycleScope.launch {
                        val path = CoverArtManager.downloadAndSaveCover(this@MainActivity, newGame)
                        if (path != null) {
                            val index = gamesList.indexOf(newGame)
                            if (index != -1) {
                                gamesList[index] = newGame.copy(coverPath = path)
                                libraryManager.saveLibrary(this@MainActivity, gamesList.toList())
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        android.util.Log.i("MainActivity", "APP STARTED - VERSION: 16:45")
        
        // Cargar biblioteca persistente
        val savedGames = libraryManager.loadLibrary(this)
        gamesList.clear()
        gamesList.addAll(savedGames)

        setContent {
            val themeManager = remember { ThemeManager(this@MainActivity) }
            val settingsManager = remember { SettingsManager(this@MainActivity) }
            val authManager = remember { AuthManager(this@MainActivity) }
            val statsManager = remember { StatsManager(this@MainActivity) }
            val theme by themeManager.currentTheme.collectAsState()
            val settings by settingsManager.currentSettings.collectAsState()

            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = theme.background
                ) {
                    MainApp(themeManager, settingsManager, authManager, statsManager, theme, settings)
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (inGame.value) {
            setPaused(true)
        }
    }

    override fun onResume() {
        super.onResume()
        if (inGame.value) {
            setPaused(false)
        }
    }

    @Composable
    fun MainApp(themeManager: ThemeManager, settingsManager: SettingsManager, authManager: AuthManager, statsManager: StatsManager, theme: AppTheme, settings: AppSettings) {
        var showSplash by remember { mutableStateOf(true) }
        var inMenuOverlay by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            if (gamesList.isNotEmpty()) {
                delay(2000) // Simular tiempo de carga de juegos y carátulas
            } else {
                delay(800) // Intro rápida sin juegos
            }
            showSplash = false
        }

        if (showSplash) {
            SplashScreen(theme, hasGames = gamesList.isNotEmpty())
            return
        }

        if (inGame.value) {
            androidx.activity.compose.BackHandler {
                android.util.Log.i("MainActivity", "Back pressed - Saving game state...")
                currentGame.value?.let { game ->
                    val sramSaved = saveSRAM("${game.path}.srm")
                    val stateSaved = saveState("${game.path}.state")
                    android.util.Log.i("MainActivity", "Save results - SRAM: $sramSaved, State: $stateSaved")
                }
                stopLoop()
                requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                inGame.value = false
            }

            Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                // Ocultar barras de sistema de forma agresiva para pantalla completa total
                val activity = androidx.compose.ui.platform.LocalContext.current as? androidx.activity.ComponentActivity
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    activity?.window?.let { window ->
                        val controller = androidx.core.view.WindowInsetsControllerCompat(window, window.decorView)
                        controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                        controller.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                        
                        // Forzar pantalla completa en el Activity
                        window.setFlags(
                            android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN,
                            android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN
                        )
                    }
                }

                AndroidView(
                    factory = { context ->
                        SurfaceView(context).apply {
                            holder.addCallback(object : SurfaceHolder.Callback {
                                override fun surfaceCreated(holder: SurfaceHolder) {
                                    android.util.Log.i("MainActivity", "Surface created: ${holder.surface}")
                                    setSurface(holder.surface)
                                }
                                override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                                    android.util.Log.i("MainActivity", "Surface changed: $width x $height")
                                }
                                override fun surfaceDestroyed(holder: SurfaceHolder) {
                                    android.util.Log.i("MainActivity", "Surface destroyed")
                                    setSurface(null)
                                }
                            })
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
                
                Surface(
                    color = theme.surface,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .clickable { inMenuOverlay = true }
                        .padding(8.dp)
                ) {
                    Text("Menú", color = theme.textPrimary, fontSize = 12.sp)
                }

                // Overlay de controles
                GamepadOverlay(settings)

                // Overlay de menú in-game desplegable
                if (inMenuOverlay) {
                    InGameMenuOverlay(
                        onClose = { inMenuOverlay = false },
                        onSave = { 
                            currentGame.value?.let { saveState("${it.path}.state") }
                            inMenuOverlay = false
                        },
                        onLoad = {
                            currentGame.value?.let { loadState("${it.path}.state") }
                            inMenuOverlay = false
                        },
                        onExit = {
                            statsManager.stopGame()
                            stopLoop()
                            inGame.value = false
                            inMenuOverlay = false
                        }
                    )
                }
            }
        } else {
            DashboardScreen(
                games = gamesList,
                theme = theme,
                settings = settings,
                authManager = authManager,
                statsManager = statsManager,
                onGameClick = { game ->
                    prepareCoreForSystem(game.system).let { corePath ->
                        if (corePath == null) {
                            android.widget.Toast.makeText(this@MainActivity, "Núcleo no disponible para ${game.system.displayName}", android.widget.Toast.LENGTH_SHORT).show()
                            return@let
                        }

                        selectedCorePath.value = corePath
                        if (!loadCore(corePath)) {
                            android.widget.Toast.makeText(this@MainActivity, "Error al cargar núcleo", android.widget.Toast.LENGTH_SHORT).show()
                            return@let
                        }
                        
                        if (!loadGame(game.path)) {
                            android.widget.Toast.makeText(this@MainActivity, "Error al cargar juego - ¿Archivo movido?", android.widget.Toast.LENGTH_SHORT).show()
                            return@let
                        }
                        
                        currentGame.value = game
                        statsManager.startGame(game.title)
                        inGame.value = true
                        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                        startLoop()

                        // Auto-guardado periódico cada 60 segundos
                        lifecycleScope.launch {
                            while (inGame.value) {
                                delay(60000)
                                if (inGame.value) {
                                    currentGame.value?.let { g ->
                                        android.util.Log.i("MainActivity", "Auto-saving SRAM...")
                                        saveSRAM("${g.path}.srm")
                                    }
                                }
                            }
                        }

                        // Cargar SRAM con un pequeño retardo para asegurar que el núcleo esté listo
                        lifecycleScope.launch {
                            delay(1000)
                            // val statePath = "${game.path}.state"
                            // if (File(statePath).exists()) loadState(statePath) // Eliminado para iniciar desde cero
                            val sramPath = "${game.path}.srm"
                            if (File(sramPath).exists()) {
                                android.util.Log.i("MainActivity", "Loading SRAM from $sramPath")
                                loadSRAM(sramPath)
                            }
                        }
                    }
                },
                onAddClick = { multiGamePicker.launch("*/*") },
                onCoreClick = { corePicker.launch("*/*") },
                onThemeSelected = { newTheme -> themeManager.setTheme(newTheme) },
                onSettingsChanged = { newSettings -> settingsManager.updateSettings(newSettings) },
                onDeleteClick = { game ->
                    libraryManager.removeGame(this@MainActivity, game)
                    gamesList.remove(game)
                }
            )
        }
    }

    @Composable
    fun GamepadOverlay(settings: AppSettings) {
        val gameName = currentGame.value?.title ?: ""
        val isBomberman = gameName.contains("Bomberman", ignoreCase = true)
        val useJoystick = isBomberman || settings.useJoystick
        val skinColors = getSkinColors(settings.gamepadSkin)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .graphicsLayer(alpha = settings.buttonOpacity)
        ) {
            // Gatillos L y R en las esquinas superiores - Estilo Metálico
            Row(modifier = Modifier.align(Alignment.TopStart)) {
                InputButton("L", RETRO_DEVICE_ID_JOYPAD_L, baseColor = skinColors.triggers, width = 80, height = 40)
            }
            Row(modifier = Modifier.align(Alignment.TopEnd)) {
                InputButton("R", RETRO_DEVICE_ID_JOYPAD_R, baseColor = skinColors.triggers, width = 80, height = 40)
            }

            // Control de dirección a la izquierda: D-Pad o Joystick
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(bottom = 16.dp)
            ) {
                if (useJoystick) {
                    VirtualJoystick()
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        InputButton("↑", RETRO_DEVICE_ID_JOYPAD_UP, baseColor = skinColors.dpad)
                        Row {
                            InputButton("←", RETRO_DEVICE_ID_JOYPAD_LEFT, baseColor = skinColors.dpad)
                            Spacer(Modifier.width(48.dp))
                            InputButton("→", RETRO_DEVICE_ID_JOYPAD_RIGHT, baseColor = skinColors.dpad)
                        }
                        InputButton("↓", RETRO_DEVICE_ID_JOYPAD_DOWN, baseColor = skinColors.dpad)
                    }
                }
            }

            // Botones Start y Select en el centro inferior - Estilo Píldora
            Row(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                InputButton("SEL", RETRO_DEVICE_ID_JOYPAD_SELECT, baseColor = skinColors.startSelect, width = 50, height = 25)
                InputButton("STA", RETRO_DEVICE_ID_JOYPAD_START, baseColor = skinColors.startSelect, width = 50, height = 25)
            }

            // Botones de acción a la derecha - Colores N64/GBA
            Box(modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 16.dp, end = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Botón B (Rojo/Clásico)
                    InputButton("B", RETRO_DEVICE_ID_JOYPAD_B, baseColor = skinColors.b, size = 70)
                    Spacer(Modifier.width(20.dp))
                    // Botón A (Verde/Clásico)
                    InputButton("A", RETRO_DEVICE_ID_JOYPAD_A, baseColor = skinColors.a, size = 75)
                }
            }
        }
    }

    @Composable
    fun InGameMenuOverlay() {
        var menuOpen by remember { mutableStateOf(false) }

        Box(modifier = Modifier.fillMaxSize()) {
            if (!menuOpen) {
                // Botón discreto en la parte superior central para abrir el menú
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                        .width(40.dp)
                        .height(16.dp)
                        .background(Color.White.copy(alpha = 0.3f), shape = RoundedCornerShape(8.dp))
                        .clickable { menuOpen = true }
                )
            } else {
                // Menú desplegable
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth(0.5f)
                        .background(PremiumDark.copy(alpha = 0.9f), shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
                        .border(1.dp, PremiumBlue.copy(alpha = 0.5f), shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Opciones de Juego", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(16.dp))

                        Button(
                            onClick = {
                                currentGame.value?.let { game -> saveState("${game.path}.state") }
                                menuOpen = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PremiumBlue),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Guardar Progreso")
                        }
                        
                        Spacer(Modifier.height(8.dp))

                        Button(
                            onClick = {
                                currentGame.value?.let { game -> loadState("${game.path}.state") }
                                menuOpen = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Cargar Progreso")
                        }

                        Spacer(Modifier.height(16.dp))

                        IconButton(onClick = { menuOpen = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun SplashScreen(theme: AppTheme, hasGames: Boolean) {
        Column(
            modifier = Modifier.fillMaxSize().background(theme.background),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(id = R.mipmap.ic_launcher),
                contentDescription = "Logo",
                modifier = Modifier.size(120.dp).clip(RoundedCornerShape(24.dp))
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text("Dybro Retro Games", color = theme.textPrimary, fontSize = 32.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold, letterSpacing = 2.sp)
            Spacer(modifier = Modifier.height(16.dp))
            CircularProgressIndicator(color = theme.accent)
            Spacer(modifier = Modifier.height(24.dp))
            
            if (hasGames) {
                Text("Cargando los juegos y las carátulas...", color = theme.textSecondary, fontSize = 14.sp)
            } else {
                Text("Iniciando sistema...", color = theme.textSecondary, fontSize = 14.sp)
            }
        }
    }

    @Composable
    fun VirtualJoystick() {
        val maxRadius = 80.dp
        val thumbRadius = 35.dp
        var offsetX by remember { mutableStateOf(0f) }
        var offsetY by remember { mutableStateOf(0f) }
        val density = androidx.compose.ui.platform.LocalDensity.current
        val maxPx = with(density) { maxRadius.toPx() }

        var currentDir by remember { mutableStateOf(-1) }

        Box(
            modifier = Modifier
                .size(maxRadius * 2)
                .background(
                    brush = androidx.compose.ui.graphics.Brush.radialGradient(
                        colors = listOf(Color.DarkGray.copy(alpha = 0.3f), Color.Black.copy(alpha = 0.6f))
                    ),
                    shape = CircleShape
                )
                .border(2.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = {
                            offsetX = 0f; offsetY = 0f
                            listOf(RETRO_DEVICE_ID_JOYPAD_UP, RETRO_DEVICE_ID_JOYPAD_DOWN, RETRO_DEVICE_ID_JOYPAD_LEFT, RETRO_DEVICE_ID_JOYPAD_RIGHT).forEach { updateInput(it, false) }
                            currentDir = -1
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val newX = offsetX + dragAmount.x
                            val newY = offsetY + dragAmount.y
                            val dist = sqrt((newX * newX + newY * newY).toDouble()).toFloat()
                            if (dist <= maxPx) { offsetX = newX; offsetY = newY } else { offsetX = newX * (maxPx / dist); offsetY = newY * (maxPx / dist) }
                            val threshold = maxPx * 0.3f
                            val newDir = if (dist > threshold) {
                                if (abs(offsetX) > abs(offsetY)) { if (offsetX > 0) RETRO_DEVICE_ID_JOYPAD_RIGHT else RETRO_DEVICE_ID_JOYPAD_LEFT } else { if (offsetY > 0) RETRO_DEVICE_ID_JOYPAD_DOWN else RETRO_DEVICE_ID_JOYPAD_UP }
                            } else -1
                            if (newDir != currentDir) { if (currentDir != -1) updateInput(currentDir, false); if (newDir != -1) updateInput(newDir, true); currentDir = newDir }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            // Stick con gradiente metálico
            Box(
                modifier = Modifier
                    .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                    .size(thumbRadius * 2)
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.linearGradient(
                            colors = listOf(Color(0xFF90CAF9), Color(0xFF1565C0))
                        ),
                        shape = CircleShape
                    )
                    .border(2.dp, Color.White.copy(alpha = 0.5f), CircleShape)
            )
        }
    }

    @Composable
    fun InputButton(text: String, id: Int, baseColor: Color, size: Int = 64, width: Int? = null, height: Int? = null) {
        val interactionSource = remember { MutableInteractionSource() }
        val isPressed = interactionSource.collectIsPressedAsState()
        val btnWidth = (width ?: size).dp
        val btnHeight = (height ?: size).dp

        LaunchedEffect(isPressed.value) {
            updateInput(id, isPressed.value)
        }

        val scale = if (isPressed.value) 0.92f else 1.0f
        val elevation = if (isPressed.value) 0.dp else 4.dp
        
        Box(
            modifier = Modifier
                .size(width = btnWidth, height = btnHeight)
                .graphicsLayer(scaleX = scale, scaleY = scale)
                .background(
                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = if (isPressed.value) 
                            listOf(baseColor.copy(alpha = 0.9f), baseColor) 
                        else 
                            listOf(baseColor.copy(alpha = 0.7f), baseColor.copy(alpha = 0.9f))
                    ),
                    shape = if (width != null && height != null) androidx.compose.foundation.shape.RoundedCornerShape(8.dp) else CircleShape
                )
                .border(
                    width = 2.dp,
                    brush = androidx.compose.ui.graphics.Brush.linearGradient(
                        colors = listOf(Color.White.copy(alpha = 0.3f), Color.Black.copy(alpha = 0.3f))
                    ),
                    shape = if (width != null && height != null) androidx.compose.foundation.shape.RoundedCornerShape(8.dp) else CircleShape
                )
                .clickable(interactionSource = interactionSource, indication = null) {},
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                color = Color.White,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.graphicsLayer(alpha = if (isPressed.value) 0.7f else 1.0f)
            )
        }
    }

    private fun extractZip(uri: Uri): String? {
        val inputStream = contentResolver.openInputStream(uri) ?: return null
        val zipInputStream = ZipInputStream(inputStream)
        var entry = zipInputStream.nextEntry
        
        while (entry != null) {
            val name = entry.name.lowercase()
            // Buscamos extensiones de juego conocidas
            if (!entry.isDirectory && (name.endsWith(".gba") || name.endsWith(".nes") || 
                name.endsWith(".sfc") || name.endsWith(".smc") || name.endsWith(".n64") || 
                name.endsWith(".psp") || name.endsWith(".iso"))) {
                
                val file = File(filesDir, entry.name)
                file.parentFile?.mkdirs()
                val outputStream = FileOutputStream(file)
                zipInputStream.copyTo(outputStream)
                outputStream.close()
                zipInputStream.closeEntry()
                zipInputStream.close()
                inputStream.close()
                return file.absolutePath
            }
            entry = zipInputStream.nextEntry
        }
        zipInputStream.close()
        inputStream.close()
        return null
    }

    private fun getFileName(uri: Uri): String? {
        var name: String? = null
        val cursor = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) name = it.getString(index)
            }
        }
        return name
    }

    private fun prepareCoreForSystem(system: GameSystem): String? {
        val coreFileName = when (system) {
            GameSystem.GBA -> "mgba_libretro_android.so"
            GameSystem.SNES -> "snes9x_libretro_android.so"
            GameSystem.N64 -> "mupen64plus_next_libretro_android.so"
            GameSystem.PSP -> "ppsspp_libretro_android.so"
            else -> "nestopia_libretro_android.so" // NES default or fallback
        }

        val destFile = File(filesDir, coreFileName)
        if (!destFile.exists()) {
            try {
                assets.open("cores/$coreFileName").use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                return null
            }
        }
        return destFile.absolutePath
    }

    private fun installGame(tempPath: String): String {
        val gamesDir = File(filesDir, "games")
        if (!gamesDir.exists()) gamesDir.mkdirs()
        
        val file = File(tempPath)
        val destFile = File(gamesDir, file.name)
        
        // Si ya está en la carpeta de juegos, no hacemos nada
        if (file.absolutePath == destFile.absolutePath) return tempPath
        
        file.copyTo(destFile, overwrite = true)
        return destFile.absolutePath
    }

    private fun copyFileToInternalStorage(uri: Uri, newName: String): String? {
        val inputStream = contentResolver.openInputStream(uri) ?: return null
        val file = File(filesDir, newName)
        val outputStream = FileOutputStream(file)
        inputStream.copyTo(outputStream)
        inputStream.close()
        outputStream.close()
        return file.absolutePath
    }

    external fun loadCore(path: String): Boolean
    external fun loadGame(path: String): Boolean
    external fun startLoop()
    external fun stopLoop()
    external fun setSurface(surface: Surface?)
    external fun updateInput(id: Int, pressed: Boolean)
    external fun saveState(path: String): Boolean
    external fun loadState(path: String): Boolean
    external fun saveSRAM(path: String): Boolean
    external fun loadSRAM(path: String): Boolean
    external fun setPaused(paused: Boolean)

    companion object {
        init {
            System.loadLibrary("retro")
        }
    }
}
