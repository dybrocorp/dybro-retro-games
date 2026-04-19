package com.dybrocorp.retrogame

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.CircularProgressIndicator
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.launch

@Composable
fun InGameMenuOverlay(
    onClose: () -> Unit,
    onSave: (Int) -> Unit,
    onLoad: (Int) -> Unit,
    onExit: () -> Unit,
    onScreenshot: () -> Unit,
    slots: List<SaveSlot> = emptyList(),
    speedMultiplier: Float = 1.0f,
    onSpeedChange: (Float) -> Unit = {}
) {
    var activeTab by remember { mutableStateOf(0) } // 0=Menu, 1=Save, 2=Load

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.75f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .background(Color(0xFF1A1A2E), RoundedCornerShape(20.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Menú de Juego", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)

            // Speed Slider
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("⚡", fontSize = 18.sp)
                Slider(
                    value = speedMultiplier,
                    onValueChange = onSpeedChange,
                    valueRange = 0.5f..2.0f,
                    steps = 5,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                )
                Text("${"%.0f".format(speedMultiplier * 100)}%", color = Color.White, fontSize = 13.sp)
            }

            Divider(color = Color.White.copy(alpha = 0.1f))

            if (activeTab == 0) {
                Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("▶  Continuar") }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { activeTab = 1 }, modifier = Modifier.weight(1f)) { Text("💾 Guardar") }
                    Button(onClick = { activeTab = 2 }, modifier = Modifier.weight(1f)) { Text("📂 Cargar") }
                }
                Button(onClick = onScreenshot, modifier = Modifier.fillMaxWidth()) { Text("📸 Captura de Pantalla") }
                Button(onClick = onExit, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB00020))) { Text("⏹  Salir del Juego") }
            } else {
                val title = if (activeTab == 1) "Guardar en Slot" else "Cargar desde Slot"
                Text(title, color = Color.White, fontWeight = FontWeight.Bold)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.height(220.dp)
                ) {
                    items(slots) { slot ->
                        Surface(
                            color = if (slot.exists) Color(0xFF2A2A4A) else Color(0xFF111122),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.clickable {
                                if (activeTab == 1) onSave(slot.index)
                                else if (slot.exists) onLoad(slot.index)
                                activeTab = 0
                            }
                        ) {
                            Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Slot ${slot.index + 1}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(slot.timestamp, color = Color(0xFFAAAAAA), fontSize = 10.sp)
                            }
                        }
                    }
                }
                Button(onClick = { activeTab = 0 }, modifier = Modifier.fillMaxWidth()) { Text("← Volver") }
            }
        }
    }
}

@Composable
fun DashboardScreen(
    games: List<Game>,
    theme: AppTheme,
    settings: AppSettings,
    authManager: AuthManager,
    statsManager: StatsManager,
    libraryManager: LibraryManager,
    onGameClick: (Game) -> Unit,
    onDeleteClick: (Game) -> Unit,
    onFavoriteClick: (Game) -> Unit,
    favorites: Set<String>,
    recents: List<Game>,
    onAddClick: () -> Unit,
    onCoreClick: () -> Unit,
    onThemeSelected: (AppTheme) -> Unit,
    onSettingsChanged: (AppSettings) -> Unit
) {
    var currentTab by remember { mutableStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredGames = if (searchQuery.isNotEmpty()) {
        games.filter { it.title.contains(searchQuery, ignoreCase = true) }
    } else {
        games
    }

    Scaffold(
        containerColor = theme.background,
        bottomBar = {
            BottomNavigationBar(currentTab, theme) { currentTab = it }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddClick,
                containerColor = theme.accent,
                contentColor = theme.background,
                shape = RoundedCornerShape(24.dp)
            ) {
                Icon(
                    Icons.Default.Add, 
                    contentDescription = "Añadir",
                    modifier = Modifier.size(32.dp)
                )
            }
        },
        floatingActionButtonPosition = FabPosition.Center
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            HeaderSection(theme)
            Spacer(modifier = Modifier.height(24.dp))
            
            if (currentTab == 0 || currentTab == 1) {
                SearchBar(theme, searchQuery, { query -> searchQuery = query })
                Spacer(modifier = Modifier.height(24.dp))
            }

            when (currentTab) {
                0 -> HomeTab(filteredGames, recents, favorites, theme, onGameClick, onDeleteClick, onFavoriteClick)
                1 -> SystemCategories(filteredGames, favorites, theme, onGameClick, onDeleteClick, onFavoriteClick)
                3 -> ThemesScreen(theme, authManager, onThemeSelected)
                4 -> SettingsScreen(theme, settings, authManager, statsManager, onSettingsChanged)
                else -> PlaceholderText("Próximamente", theme)
            }
        }
    }
}

@Composable
fun HeaderSection(theme: AppTheme) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("Dybro Retro", color = theme.textPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("Tu biblioteca de juegos", color = theme.textSecondary, fontSize = 14.sp)
        }
    }
}

@Composable
fun SearchBar(theme: AppTheme, query: String, onQueryChange: (String) -> Unit) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text("Buscar en la biblioteca", color = theme.textSecondary) },
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)),
        colors = TextFieldDefaults.colors(
            unfocusedContainerColor = theme.surface,
            focusedContainerColor = theme.surface,
            unfocusedIndicatorColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            focusedTextColor = theme.textPrimary,
            unfocusedTextColor = theme.textPrimary
        ),
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = theme.textSecondary) }
    )
}

@Composable
fun HomeTab(
    allGames: List<Game>,
    recents: List<Game>,
    favorites: Set<String>,
    theme: AppTheme,
    onGameClick: (Game) -> Unit,
    onDeleteClick: (Game) -> Unit,
    onFavoriteClick: (Game) -> Unit
) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        // Continuar Jugando
        if (recents.isNotEmpty()) {
            Text("▶️ Continuar Jugando", color = theme.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                recents.forEach { game ->
                    Box(modifier = Modifier.width(140.dp)) {
                        GameCard(game, theme, favorites.contains(game.path), onGameClick, onDeleteClick, onFavoriteClick)
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
        // Favoritos
        val favGames = allGames.filter { favorites.contains(it.path) }
        if (favGames.isNotEmpty()) {
            Text("⭐ Favoritos", color = theme.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                favGames.forEach { game ->
                    Box(modifier = Modifier.width(140.dp)) {
                        GameCard(game, theme, true, onGameClick, onDeleteClick, onFavoriteClick)
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
        // Todos los juegos
        Text("🎮 Todos los juegos (${allGames.size})", color = theme.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(modifier = Modifier.height(8.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.height(600.dp)
        ) {
            items(allGames) { game ->
                GameCard(game, theme, favorites.contains(game.path), onGameClick, onDeleteClick, onFavoriteClick)
            }
        }
    }
}

@Composable
fun GameGrid(games: List<Game>, theme: AppTheme, onGameClick: (Game) -> Unit, onDeleteClick: (Game) -> Unit) {
    Column {
        Text("Todos los juegos (${games.size})", color = theme.textPrimary, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(games) { game ->
                GameCard(game, theme, false, onGameClick, onDeleteClick, {})
            }
        }
    }
}

@Composable
fun GameCard(game: Game, theme: AppTheme, isFavorite: Boolean, onClick: (Game) -> Unit, onDeleteClick: (Game) -> Unit, onFavoriteClick: (Game) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(theme.surface)
            .clickable { onClick(game) }
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(game.title.take(3).uppercase(), color = theme.textSecondary.copy(alpha=0.3f), fontSize = 48.sp, fontWeight = FontWeight.Bold)
        }
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(CoverArtManager.getCoverUrl(game))
                .crossfade(true)
                .build(),
            contentDescription = game.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))))
        )
        Column(
            modifier = Modifier.align(Alignment.BottomStart).padding(12.dp)
        ) {
            Surface(color = theme.accent.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                Text(game.system.name, color = theme.accent, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(game.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
        // Botones de acción
        Row(
            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            IconButton(
                onClick = { onFavoriteClick(game) },
                modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(12.dp)).size(28.dp)
            ) {
                Icon(if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder, contentDescription = "Favorito",
                    tint = if (isFavorite) Color(0xFFFFD700) else Color.White, modifier = Modifier.size(16.dp))
            }
            IconButton(
                onClick = { onDeleteClick(game) },
                modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(12.dp)).size(28.dp)
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
fun BottomNavigationBar(selectedTab: Int, theme: AppTheme, onTabSelected: (Int) -> Unit) {
    NavigationBar(
        containerColor = theme.background,
        tonalElevation = 8.dp
    ) {
        val items = listOf(
            Triple(0, "Inicio", Icons.Default.Home),
            Triple(1, "Consolas", Icons.Default.List),
            Triple(-1, "", Icons.Default.Add), // Espacio para el FAB
            Triple(3, "Temas", Icons.Default.Edit),
            Triple(4, "Ajustes", Icons.Default.Settings)
        )
        
        items.forEach { (index, label, icon) ->
            if (index == -1) {
                Spacer(modifier = Modifier.weight(1f))
                return@forEach
            }
            NavigationBarItem(
                selected = selectedTab == index,
                onClick = { onTabSelected(index) },
                icon = { Icon(icon, contentDescription = label) },
                label = { Text(label, fontSize = 10.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = theme.accent,
                    unselectedIconColor = theme.textSecondary,
                    selectedTextColor = theme.accent,
                    unselectedTextColor = theme.textSecondary,
                    indicatorColor = Color.Transparent
                )
            )
        }
    }
}

@Composable
fun SystemCategories(games: List<Game>, favorites: Set<String>, theme: AppTheme, onGameClick: (Game) -> Unit, onDeleteClick: (Game) -> Unit, onFavoriteClick: (Game) -> Unit) {
    val grouped = games.groupBy { it.system }
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        grouped.forEach { (system, systemGames) ->
            Text(system.displayName, color = theme.textPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                systemGames.forEach { game ->
                    Box(modifier = Modifier.width(160.dp).padding(end = 12.dp)) {
                        GameCard(game, theme, favorites.contains(game.path), onGameClick, onDeleteClick, onFavoriteClick)
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun PlaceholderText(text: String, theme: AppTheme) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = theme.textSecondary)
    }
}

@Composable
fun ThemesScreen(currentTheme: AppTheme, authManager: AuthManager, onThemeSelected: (AppTheme) -> Unit) {
    val isPremium = authManager.isPremium()
    Column {
        Text("Selecciona un Tema", color = currentTheme.textPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Spacer(modifier = Modifier.height(16.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(ThemeConfigs.allThemes) { themeOption ->
                val isPremiumTheme = themeOption.name == "Cyber Neon" || themeOption.name == "Clean Light"
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(themeOption.surface)
                        .clickable(enabled = !isPremiumTheme || isPremium) { onThemeSelected(themeOption) }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isPremiumTheme && !isPremium) {
                                Text("🔒 ", fontSize = 12.sp)
                            }
                            Text(
                                text = themeOption.name,
                                color = if (!isPremiumTheme || isPremium) themeOption.textPrimary else themeOption.textSecondary.copy(alpha=0.5f),
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(modifier = Modifier.size(24.dp).background(themeOption.background, RoundedCornerShape(4.dp)))
                            Box(modifier = Modifier.size(24.dp).background(themeOption.accent, RoundedCornerShape(4.dp)))
                        }
                    }
                    if (currentTheme.name == themeOption.name) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Seleccionado",
                            tint = themeOption.accent,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun UserProfileSection(theme: AppTheme, authManager: AuthManager, statsManager: StatsManager) {
    var user by remember { mutableStateOf(authManager.getCurrentUser()) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            account?.idToken?.let { token ->
                val credential = GoogleAuthProvider.getCredential(token, null)
                authManager.auth.signInWithCredential(credential).addOnCompleteListener {
                    user = authManager.getCurrentUser()
                }
            }
        } catch (e: ApiException) {
            // Manejar error silenciosamente
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(theme.surface, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (user != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(user?.photoUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Avatar",
                    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(32.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(theme.accent.copy(alpha = 0.2f), RoundedCornerShape(32.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Person, contentDescription = "Perfil", tint = theme.accent, modifier = Modifier.size(32.dp))
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(user?.displayName ?: "Jugador Anónimo", color = theme.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                if (authManager.isPremium()) {
                    Text("💎 Dybro Pass: Premium", color = theme.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                } else {
                    Text("Plan Estándar · Actualiza a Dybro Pass", color = theme.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
            
            if (user == null) {
                Surface(
                    color = theme.accent,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.clickable { launcher.launch(authManager.googleSignInClient.signInIntent) }
                ) {
                    Text("Entrar", color = theme.background, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            } else {
                Surface(
                    color = theme.background,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.clickable { 
                        authManager.signOut { user = null }
                    }
                ) {
                    Text("Salir", color = theme.textSecondary, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("Horas Jugadas", color = theme.textSecondary, fontSize = 12.sp)
                Text(statsManager.getGlobalPlaytimeFormatted(), color = theme.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Juego Favorito", color = theme.textSecondary, fontSize = 12.sp)
                Text(statsManager.getMostPlayedGame().take(15), color = theme.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun SettingsScreen(theme: AppTheme, settings: AppSettings, authManager: AuthManager, statsManager: StatsManager, onSettingsChanged: (AppSettings) -> Unit) {
    val isPremium = authManager.isPremium()
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("Ajustes del Emulador", color = theme.textPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Spacer(modifier = Modifier.height(16.dp))
        
        UserProfileSection(theme, authManager, statsManager)
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Joystick toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(theme.surface, RoundedCornerShape(12.dp))
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Estilo de Control", color = theme.textPrimary, fontWeight = FontWeight.Medium)
                Text("Usar Pad analógico o botones clásicos", color = theme.textSecondary, fontSize = 12.sp)
            }
            Switch(
                checked = settings.useJoystick,
                onCheckedChange = { onSettingsChanged(settings.copy(useJoystick = it)) },
                colors = SwitchDefaults.colors(checkedThumbColor = theme.accent, checkedTrackColor = theme.accent.copy(alpha=0.5f))
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Opacity Slider
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(theme.surface, RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            Text("Opacidad de Botones", color = theme.textPrimary, fontWeight = FontWeight.Medium)
            Slider(
                value = settings.buttonOpacity,
                onValueChange = { onSettingsChanged(settings.copy(buttonOpacity = it)) },
                valueRange = 0.1f..1.0f,
                colors = SliderDefaults.colors(thumbColor = theme.accent, activeTrackColor = theme.accent)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        
        // Auto-save Picker
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(theme.surface, RoundedCornerShape(12.dp))
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Autoguardado", color = theme.textPrimary, fontWeight = FontWeight.Medium)
                Text("Tiempo entre guardados", color = theme.textSecondary, fontSize = 12.sp)
            }
            val minText = if (settings.autoSaveMinutes == 0) "Desactivado" else "${settings.autoSaveMinutes} min"
            Text(minText, color = theme.accent, fontWeight = FontWeight.Bold, modifier = Modifier.clickable {
                val nextVal = when(settings.autoSaveMinutes) { 0 -> 1; 1 -> 3; 3 -> 5; 5 -> 10; else -> 0 }
                onSettingsChanged(settings.copy(autoSaveMinutes = nextVal))
            }.padding(8.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Filtro Visual Picker
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(theme.surface, RoundedCornerShape(12.dp))
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Filtro Visual", color = theme.textPrimary, fontWeight = FontWeight.Medium)
                Text("Efectos gráficos de pantalla", color = theme.textSecondary, fontSize = 12.sp)
            }
            Text(settings.visualFilter, color = theme.accent, fontWeight = FontWeight.Bold, modifier = Modifier.clickable {
                val nextVal = when(settings.visualFilter) { "Normal" -> "CRT"; "CRT" -> "Suavizado"; else -> "Normal" }
                onSettingsChanged(settings.copy(visualFilter = nextVal))
            }.padding(8.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Skin Selector
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(theme.surface, RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            Text("Diseño del Gamepad", color = theme.textPrimary, fontWeight = FontWeight.Medium)
            Text("Selecciona una skin para los botones", color = theme.textSecondary, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(12.dp))

            val skins = listOf("Clásico", "Oscuro", "Neón", "Transparente")
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                skins.forEach { skin ->
                    val isSelected = settings.gamepadSkin == skin
                    val isPremiumSkin = skin != "Clásico" // "Clásico" es la única gratis

                    Surface(
                        color = if (isSelected) theme.accent else theme.background,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.clickable(enabled = !isPremiumSkin || isPremium) { onSettingsChanged(settings.copy(gamepadSkin = skin)) }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            if (isPremiumSkin && !isPremium) {
                                Text("🔒 ", fontSize = 10.sp)
                            }
                            Text(
                                text = skin,
                                color = if (!isPremiumSkin || isPremium) (if (isSelected) theme.background else theme.textPrimary) else theme.textSecondary.copy(alpha=0.5f),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Dybro Pass Section
        DybroPassSection(theme, authManager)
    }
}

@Composable
fun DybroPassSection(theme: AppTheme, authManager: AuthManager) {
    val isPremium = authManager.isPremium()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isPremium) theme.accent.copy(alpha = 0.12f) else theme.surface,
                RoundedCornerShape(16.dp)
            )
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("\uD83D\uDC8E", fontSize = 28.sp)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text("Dybro Pass", color = theme.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    if (isPremium) "Tu suscripción está activa" else "Mejora tu experiencia de juego",
                    color = theme.textSecondary, fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        val benefits = listOf(
            "\uD83D\uDEAB Sin anuncios" to "Experiencia completamente limpia",
            "\uD83D\uDCC1 Biblioteca ilimitada" to "Añade todos los juegos que quieras",
            "\uD83C\uDFA8 Temas exclusivos" to "Skins y colores premium",
            "\u26A1 Acceso prioritario" to "Primero en recibir nuevas funciones"
        )
        benefits.forEach { (title, desc) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, color = theme.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(170.dp))
                Text(desc, color = theme.textSecondary, fontSize = 11.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (!isPremium) {
            Button(
                onClick = { /* TODO: Google Play Billing */ },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = theme.accent)
            ) {
                Text(
                    "Obtener Dybro Pass — COP $12.900/mes",
                    color = theme.background,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Cancela cuando quieras. Sin compromisos.",
                color = theme.textSecondary,
                fontSize = 11.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(theme.accent.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "\u2705 Suscripción Activa — ¡Gracias por tu apoyo!",
                    color = theme.accent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }
}

fun Modifier.crtFilter() = this.drawWithContent {
    drawContent()
    val scanlineWidth = 3f
    val alpha = 0.15f
    val scanlineColor = Color.Black.copy(alpha = alpha)
    
    for (i in 0..size.height.toInt() step (scanlineWidth * 2).toInt()) {
        drawLine(
            color = scanlineColor,
            start = Offset(0f, i.toFloat()),
            end = Offset(size.width, i.toFloat()),
            strokeWidth = scanlineWidth
        )
    }
}
