package edu.ibu.termotabip.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import edu.ibu.termotabip.auth.AuthResult
import edu.ibu.termotabip.auth.LocalAuthManager
import edu.ibu.termotabip.auth.User
import edu.ibu.termotabip.ui.theme.*

@Composable
fun AuthScreen(
    onLoginSuccess: (User) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { LocalAuthManager.ensureDemoUser(context) }

    var selectedTab by remember { mutableIntStateOf(0) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NavyDeep)
            .drawBehind {
                // Grid arka plan
                val color = Color(0xFF1A3050).copy(alpha = 0.35f)
                var x = 0f
                while (x <= size.width) { drawLine(color, androidx.compose.ui.geometry.Offset(x,0f), androidx.compose.ui.geometry.Offset(x,size.height), 0.5f); x += 40f }
                var y = 0f
                while (y <= size.height) { drawLine(color, androidx.compose.ui.geometry.Offset(0f,y), androidx.compose.ui.geometry.Offset(size.width,y), 0.5f); y += 40f }
            }
    ) {
        // Turuncu parıltı
        Box(
            modifier = Modifier
                .size(400.dp)
                .offset(x = (-80).dp, y = (-80).dp)
                .background(
                    Brush.radialGradient(listOf(ThermalOrange.copy(0.08f), Color.Transparent)),
                    CircleShape
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(72.dp))

            // Logo
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(
                        Brush.radialGradient(listOf(ThermalOrange.copy(0.2f), NavySurface)),
                        CircleShape
                    )
                    .drawBehind {
                        drawCircle(ThermalOrange.copy(0.3f),
                            radius = size.minDimension/2,
                            style  = androidx.compose.ui.graphics.drawscope.Stroke(1f))
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("🌡", fontSize = 36.sp)
            }

            Spacer(Modifier.height(16.dp))

            Text("TermoInjury", fontSize = 28.sp, fontWeight = FontWeight.Bold,
                color = TextPrimary, letterSpacing = (-0.5).sp)
            Spacer(Modifier.height(4.dp))
            Text("TERMAL YARA ANALİZ SİSTEMİ", fontSize = 10.sp,
                color = ThermalOrange, letterSpacing = 2.sp, fontWeight = FontWeight.Medium)

            Spacer(Modifier.height(40.dp))

            // Kart
            Surface(
                modifier  = Modifier.fillMaxWidth(),
                color     = NavySurface,
                shape     = RoundedCornerShape(20.dp),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp
            ) {
                Column(Modifier.padding(24.dp)) {
                    // Tab
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(NavyBase)
                            .padding(3.dp)
                    ) {
                        listOf("Giriş Yap", "Kayıt Ol").forEachIndexed { idx, label ->
                            val selected = selectedTab == idx
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) ThermalOrange else Color.Transparent)
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                TextButton(onClick = { selectedTab = idx },
                                    modifier = Modifier.fillMaxWidth()) {
                                    Text(label,
                                        color = if (selected) NavyDeep else TextSecondary,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 14.sp)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    AnimatedContent(
                        targetState = selectedTab,
                        transitionSpec = {
                            slideInHorizontally { if (targetState > initialState) it else -it } +
                                    fadeIn() togetherWith
                                    slideOutHorizontally { if (targetState > initialState) -it else it } +
                                    fadeOut()
                        },
                        label = "auth_tab"
                    ) { tab ->
                        if (tab == 0) LoginForm(onSuccess = onLoginSuccess)
                        else RegisterForm(onSuccess = { selectedTab = 0 })
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Demo notu
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(NavySurface.copy(0.7f))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(Modifier.size(6.dp).background(ThermalAmber, CircleShape))
                Text("Demo: kullanıcı adı demo, şifre 1234",
                    color = TextSecondary, fontSize = 12.sp)
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

// ── Giriş formu ───────────────────────────────────────────────────────────
@Composable
fun LoginForm(onSuccess: (User) -> Unit) {
    val context = LocalContext.current
    val focus   = LocalFocusManager.current

    var username  by remember { mutableStateOf("") }
    var password  by remember { mutableStateOf("") }
    var showPass  by remember { mutableStateOf(false) }
    var errorMsg  by remember { mutableStateOf<String?>(null) }
    var loading   by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ClinicalTextField(value = username, onValueChange = { username = it; errorMsg = null },
            label = "Kullanıcı Adı", imeAction = ImeAction.Next,
            onImeAction = { focus.moveFocus(FocusDirection.Down) })

        ClinicalTextField(value = password, onValueChange = { password = it; errorMsg = null },
            label = "Şifre", isPassword = true, showPassword = showPass,
            onTogglePassword = { showPass = !showPass },
            imeAction = ImeAction.Done, onImeAction = { focus.clearFocus() })

        errorMsg?.let { Text(it, color = ClinicalRed, fontSize = 12.sp) }

        Button(
            onClick = {
                loading = true; errorMsg = null
                when (val r = LocalAuthManager.login(context, username, password)) {
                    is AuthResult.Success -> onSuccess(r.user)
                    is AuthResult.Error   -> { errorMsg = r.message; loading = false }
                }
            },
            enabled  = username.isNotBlank() && password.isNotBlank() && !loading,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = ThermalOrange,
                disabledContainerColor = NavyElevated),
            shape    = RoundedCornerShape(12.dp)
        ) {
            if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp,
                color = NavyDeep)
            else Text("Giriş Yap", fontWeight = FontWeight.Bold, color = NavyDeep)
        }
    }
}

// ── Kayıt formu ───────────────────────────────────────────────────────────
@Composable
fun RegisterForm(onSuccess: () -> Unit) {
    val context = LocalContext.current
    val focus   = LocalFocusManager.current

    var username  by remember { mutableStateOf("") }
    var fullName  by remember { mutableStateOf("") }
    var password  by remember { mutableStateOf("") }
    var password2 by remember { mutableStateOf("") }
    var role      by remember { mutableStateOf("Hemşire") }
    var showPass  by remember { mutableStateOf(false) }
    var errorMsg  by remember { mutableStateOf<String?>(null) }
    var successMsg by remember { mutableStateOf<String?>(null) }

    val roles = listOf("Hemşire", "Doktor", "Teknisyen")

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ClinicalTextField(value = fullName, onValueChange = { fullName = it; errorMsg = null },
            label = "Ad Soyad", imeAction = ImeAction.Next,
            onImeAction = { focus.moveFocus(FocusDirection.Down) })
        ClinicalTextField(value = username, onValueChange = { username = it; errorMsg = null },
            label = "Kullanıcı Adı", imeAction = ImeAction.Next,
            onImeAction = { focus.moveFocus(FocusDirection.Down) })

        // Rol
        Text("Rol", fontSize = 12.sp, color = TextSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            roles.forEach { r ->
                val selected = role == r
                FilterChip(
                    selected = selected, onClick = { role = r },
                    label    = { Text(r, fontSize = 12.sp) },
                    colors   = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ThermalOrange,
                        selectedLabelColor     = NavyDeep,
                        containerColor         = NavyBase,
                        labelColor             = TextSecondary
                    )
                )
            }
        }

        ClinicalTextField(value = password, onValueChange = { password = it; errorMsg = null },
            label = "Şifre", isPassword = true, showPassword = showPass,
            onTogglePassword = { showPass = !showPass },
            imeAction = ImeAction.Next, onImeAction = { focus.moveFocus(FocusDirection.Down) })
        ClinicalTextField(value = password2, onValueChange = { password2 = it; errorMsg = null },
            label = "Şifre Tekrar", isPassword = true, showPassword = showPass,
            imeAction = ImeAction.Done, onImeAction = { focus.clearFocus() })

        errorMsg?.let   { Text(it, color = ClinicalRed, fontSize = 12.sp) }
        successMsg?.let { Text(it, color = ClinicalTeal, fontSize = 12.sp) }

        Button(
            onClick = {
                errorMsg = null; successMsg = null
                if (password != password2) { errorMsg = "Şifreler eşleşmiyor"; return@Button }
                when (val r = LocalAuthManager.register(context, username, fullName, password, role)) {
                    is AuthResult.Success -> { successMsg = "Kayıt başarılı!"; onSuccess() }
                    is AuthResult.Error   -> errorMsg = r.message
                }
            },
            enabled  = username.isNotBlank() && fullName.isNotBlank() &&
                    password.isNotBlank() && password2.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = ThermalOrange,
                disabledContainerColor = NavyElevated),
            shape    = RoundedCornerShape(12.dp)
        ) {
            Text("Kayıt Ol", fontWeight = FontWeight.Bold, color = NavyDeep)
        }
    }
}

// ── Ortak text field ──────────────────────────────────────────────────────
@Composable
fun ClinicalTextField(
    value: String, onValueChange: (String) -> Unit,
    label: String, isPassword: Boolean = false,
    showPassword: Boolean = false, onTogglePassword: (() -> Unit)? = null,
    imeAction: ImeAction = ImeAction.Next, onImeAction: () -> Unit = {}
) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange,
        label = { Text(label, fontSize = 13.sp) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        visualTransformation = if (isPassword && !showPassword)
            PasswordVisualTransformation() else VisualTransformation.None,
        trailingIcon = if (isPassword && onTogglePassword != null) ({
            TextButton(onClick = onTogglePassword) {
                Text(if (showPassword) "Gizle" else "Göster",
                    fontSize = 11.sp, color = ThermalOrange)
            }
        }) else null,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (isPassword) KeyboardType.Password else KeyboardType.Text,
            imeAction = imeAction
        ),
        keyboardActions = KeyboardActions(
            onNext = { onImeAction() }, onDone = { onImeAction() }
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor   = ThermalOrange,
            unfocusedBorderColor = NavyElevated,
            focusedLabelColor    = ThermalOrange,
            unfocusedLabelColor  = TextSecondary,
            focusedTextColor     = TextPrimary,
            unfocusedTextColor   = TextPrimary,
            cursorColor          = ThermalOrange,
            focusedContainerColor   = NavyBase,
            unfocusedContainerColor = NavyBase,
        ),
        shape = RoundedCornerShape(10.dp)
    )
}