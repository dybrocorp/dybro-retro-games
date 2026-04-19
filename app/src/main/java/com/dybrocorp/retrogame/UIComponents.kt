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
    onSave: () -> Unit,
    onLoad: () -> Unit,
    onExit: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .background(Color(0xFF1A1A2E), RoundedCornerShape(16.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Menú de Juego", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Continuar") }
            Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) { Text("Guardar Partida") }
            Button(onClick = onLoad, modifier = Modifier.fillMaxWidth()) { Text("Cargar Partida") }
            Button(onClick = onExit, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB00020))) { Text("Salir del Juego") }
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
    onGameClick: (Game) -> Unit,
    onDeleteClick: (Game) -> Unit,
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
                0 -> GameGrid(filteredGames, theme, onGameClick, onDeleteClick)
                1 -> SystemCategories(filteredGames, theme, onGameClick, onDeleteClick)
                3 -> ThemesScreen(theme, onThemeSelected)
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
                GameCard(game, theme, onGameClick, onDeleteClick)
            }
        }
    }
}

@Composable
fun GameCard(game: Game, theme: AppTheme, onClick: (Game) -> Unit, onDeleteClick: (Game) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(theme.surface)
            .clickable { onClick(game) }
    ) {
        // Fallback textual detrás de la imagen por si no carga
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(game.title.take(3).uppercase(), color = theme.textSecondary.copy(alpha=0.3f), fontSize = 48.sp, fontWeight = FontWeight.Bold)
        }
        
        // Carátula Dinámica con Coil
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(CoverArtManager.getCoverUrl(game))
                .crossfade(true)
                .build(),
            contentDescription = game.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Overlay con Gradiente
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))
                    )
                )
        )
        
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp)
        ) {
            Surface(
                color = theme.accent.copy(alpha = 0.2f),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = game.system.name,
                    color = theme.accent,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = game.title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
        
        // Botón de eliminar
        IconButton(
            onClick = { onDeleteClick(game) },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                .size(24.dp)
        ) {
            Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = Color.White, modifier = Modifier.size(16.dp))
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
fun SystemCategories(games: List<Game>, theme: AppTheme, onGameClick: (Game) -> Unit, onDeleteClick: (Game) -> Unit) {
    val grouped = games.groupBy { it.system }
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        grouped.forEach { (system, systemGames) ->
            Text(system.displayName, color = theme.textPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                systemGames.forEach { game ->
                    Box(modifier = Modifier.width(160.dp).padding(end = 12.dp)) {
                        GameCard(game, theme, onGameClick, onDeleteClick)
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
fun ThemesScreen(currentTheme: AppTheme, onThemeSelected: (AppTheme) -> Unit) {
    Column {
        Text("Selecciona un Tema", color = currentTheme.textPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Spacer(modifier = Modifier.height(16.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(ThemeConfigs.allThemes) { themeOption ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(themeOption.surface)
                        .clickable { onThemeSelected(themeOption) }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = themeOption.name,
                            color = themeOption.textPrimary,
                            fontWeight = FontWeight.Bold
                        )
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
    Column(modifier = Modifier.fillMaxSize()) {
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
            Text("Opacidad de los controles", color = theme.textPrimary, fontWeight = FontWeight.Medium)
            Text("${(settings.buttonOpacity * 100).toInt()}%", color = theme.accent, fontSize = 12.sp)
            Slider(
                value = settings.buttonOpacity,
                onValueChange = { onSettingsChanged(settings.copy(buttonOpacity = it)) },
                valueRange = 0.1f..1.0f,
                colors = SliderDefaults.colors(
                    thumbColor = theme.accent,
                    activeTrackColor = theme.accent
                )
            )
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
                    Surface(
                        color = if (isSelected) theme.accent else theme.background,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.clickable { onSettingsChanged(settings.copy(gamepadSkin = skin)) }
                    ) {
                        Text(
                            text = skin,
                            color = if (isSelected) theme.background else theme.textPrimary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
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
