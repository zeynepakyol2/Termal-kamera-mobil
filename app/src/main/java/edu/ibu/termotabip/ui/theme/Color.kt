package edu.ibu.termotabip.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── Renk Paleti: Clinical Precision ─────────────────────────────────────
// Koyu lacivert zemin + termal turuncu/amber aksanlar

// Ana renkler
val NavyDeep      = Color(0xFF08121F)   // En koyu arka plan
val NavyBase      = Color(0xFF0C1B2E)   // Ana arka plan
val NavySurface   = Color(0xFF112240)   // Kart yüzeyi
val NavyElevated  = Color(0xFF163050)   // Yükseltilmiş kart

// Aksan — Termal turuncu
val ThermalOrange = Color(0xFFFF6B35)   // Ana aksan
val ThermalAmber  = Color(0xFFFFAB40)   // İkincil aksan
val ThermalWarm   = Color(0xFFFF8C42)   // Hover/active

// Klinik renkler
val ClinicalTeal  = Color(0xFF00BFA5)   // Başarı / yara yok
val ClinicalRed   = Color(0xFFFF3D3D)   // Hata / kritik
val ClinicalBlue  = Color(0xFF40C4FF)   // Bilgi / vurgu

// Metin renkleri
val TextPrimary   = Color(0xFFE8F4FC)   // Ana metin
val TextSecondary = Color(0xFF7BA3C4)   // İkincil metin
val TextHint      = Color(0xFF3D6080)   // İpucu metin

// Özel arka planlar
val WoundPresent  = Color(0xFF2A0A0A)   // Yara var arka planı
val WoundAbsent   = Color(0xFF0A2A1A)   // Yara yok arka planı
val RiskLow       = Color(0xFF1A2A0A)   // Düşük risk
val RiskHigh      = Color(0xFF2A1A0A)   // Yüksek risk

// ── Color Scheme ─────────────────────────────────────────────────────────
private val DarkColorScheme = darkColorScheme(
    primary              = ThermalOrange,
    onPrimary            = Color(0xFF1A0A00),
    primaryContainer     = Color(0xFF2A1200),
    onPrimaryContainer   = ThermalAmber,

    secondary            = ClinicalTeal,
    onSecondary          = Color(0xFF001A16),
    secondaryContainer   = Color(0xFF00261F),
    onSecondaryContainer = Color(0xFF80EBD9),

    tertiary             = ClinicalBlue,
    onTertiary           = Color(0xFF001F2B),
    tertiaryContainer    = Color(0xFF00344A),
    onTertiaryContainer  = Color(0xFF9FE0FF),

    error                = ClinicalRed,
    onError              = Color(0xFF1A0000),
    errorContainer       = Color(0xFF2A0000),
    onErrorContainer     = Color(0xFFFF9090),

    background           = NavyDeep,
    onBackground         = TextPrimary,

    surface              = NavyBase,
    onSurface            = TextPrimary,
    surfaceVariant       = NavySurface,
    onSurfaceVariant     = TextSecondary,

    outline              = Color(0xFF1E3A5A),
    outlineVariant       = Color(0xFF132840),

    inverseSurface       = TextPrimary,
    inverseOnSurface     = NavyBase,
    inversePrimary       = Color(0xFF8B3A00),
)

// ── Tema ─────────────────────────────────────────────────────────────────
@Composable
fun TermoTabipTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography  = Typography,
        content     = content
    )
}