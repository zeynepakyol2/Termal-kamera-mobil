package edu.ibu.termotabip.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import edu.ibu.termotabip.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sin

// ── Splash ────────────────────────────────────────────────────────────────
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.85f, targetValue = 1f, label = "pulse_scale",
        animationSpec = infiniteRepeatable(tween(1200, easing = EaseInOut), RepeatMode.Reverse)
    )

    LaunchedEffect(Unit) {
        delay(150); visible = true
        delay(2400); onFinished()
    }

    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(700), label = "alpha")
    val scale by animateFloatAsState(
        if (visible) 1f else 0.6f,
        spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow), label = "scale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NavyDeep)
            .drawBehind { drawGrid(this) },
        contentAlignment = Alignment.Center
    ) {
        // Arka plan ışıma efekti
        Box(
            modifier = Modifier
                .size(300.dp)
                .background(
                    Brush.radialGradient(
                        listOf(ThermalOrange.copy(alpha = 0.12f), Color.Transparent)
                    ),
                    CircleShape
                )
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .alpha(alpha)
                .graphicsLayer { scaleX = scale; scaleY = scale }
        ) {
            // Logo çemberi
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .background(
                        Brush.radialGradient(
                            listOf(ThermalOrange.copy(0.25f), NavySurface)
                        ),
                        CircleShape
                    )
                    .graphicsLayer { scaleX = pulse; scaleY = pulse },
                contentAlignment = Alignment.Center
            ) {
                Text("🌡", fontSize = 48.sp)
            }

            Spacer(Modifier.height(24.dp))

            Text(
                "TermoInjury",
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                letterSpacing = (-0.5).sp
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.width(24.dp).height(1.dp).background(ThermalOrange.copy(0.5f)))
                Text(
                    "TERMAL YARA ANALİZ SİSTEMİ",
                    fontSize = 11.sp,
                    color = ThermalOrange,
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.Medium
                )
                Box(Modifier.width(24.dp).height(1.dp).background(ThermalOrange.copy(0.5f)))
            }

            Spacer(Modifier.height(56.dp))

            LinearProgressIndicator(
                modifier = Modifier.width(120.dp).height(2.dp).clip(CircleShape),
                color = ThermalOrange,
                trackColor = NavySurface
            )
        }

        Text(
            "v1.0 · IBU",
            color = TextHint,
            fontSize = 11.sp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp)
        )
    }
}

// Arka plan grid çizgisi
private fun drawGrid(scope: DrawScope) {
    val color = Color(0xFF1A3050).copy(alpha = 0.4f)
    val step = 40f
    var x = 0f
    while (x <= scope.size.width) {
        scope.drawLine(color, Offset(x, 0f), Offset(x, scope.size.height), 0.5f)
        x += step
    }
    var y = 0f
    while (y <= scope.size.height) {
        scope.drawLine(color, Offset(0f, y), Offset(scope.size.width, y), 0.5f)
        y += step
    }
}

// ── Onboarding ────────────────────────────────────────────────────────────
data class OnboardingPage(
    val emoji: String, val title: String,
    val description: String, val accent: Color
)

private val pages = listOf(
    OnboardingPage("🌡", "Termal Görüntüleme",
        "HIKMICRO Mini3 termal kameranızı USB ile bağlayın. Galerinizden de termal fotoğraf seçebilirsiniz.",
        ThermalOrange),
    OnboardingPage("🔬", "Yapay Zeka Analizi",
        "3 katmanlı derin öğrenme modeli çalışır: Yara varlığı tespiti → Evre sınıflandırması → Risk değerlendirmesi.",
        ClinicalTeal),
    OnboardingPage("📋", "Klinik Protokoller",
        "Analiz sonucuna göre kanıta dayalı bakım önerileri sunulur. Evre ve risk seviyesine özel protokoller.",
        ClinicalBlue),
    OnboardingPage("📄", "Rapor & Takip",
        "Her analiz otomatik kaydedilir. PDF rapor oluşturup paylaşabilir, geçmiş trendleri izleyebilirsiniz.",
        ThermalAmber),
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val pagerState = rememberPagerState { pages.size }
    val scope      = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NavyDeep)
            .drawBehind { drawGrid(this) }
    ) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { idx ->
            OnboardingPageContent(pages[idx])
        }

        // Alt panel
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(listOf(Color.Transparent, NavyDeep, NavyDeep))
                )
                .padding(horizontal = 28.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Nokta göstergesi
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(pages.size) { idx ->
                    val selected = pagerState.currentPage == idx
                    val accent   = pages[pagerState.currentPage].accent
                    val w by animateDpAsState(if (selected) 20.dp else 6.dp,
                        spring(Spring.DampingRatioMediumBouncy), label = "dot")
                    Box(
                        Modifier
                            .height(6.dp).width(w)
                            .clip(CircleShape)
                            .background(if (selected) accent else NavySurface)
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            val accent = pages[pagerState.currentPage].accent

            if (pagerState.currentPage < pages.size - 1) {
                Row(Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onFinished) {
                        Text("Atla", color = TextSecondary, fontSize = 14.sp)
                    }
                    Button(
                        onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                        colors = ButtonDefaults.buttonColors(containerColor = accent),
                        shape  = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(46.dp)
                    ) {
                        Text("İleri →", fontWeight = FontWeight.SemiBold,
                            color = if (accent == ThermalAmber) NavyDeep else Color.White)
                    }
                }
            } else {
                Button(
                    onClick  = onFinished,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = accent),
                    shape    = RoundedCornerShape(14.dp)
                ) {
                    Text("Başla", fontWeight = FontWeight.Bold, fontSize = 16.sp,
                        color = NavyDeep)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OnboardingPageContent(page: OnboardingPage) {
    val alpha by produceState(0f) {
        value = 0f; delay(80)
        animate(0f, 1f, animationSpec = tween(500)) { v, _ -> value = v }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 36.dp)
            .padding(top = 120.dp, bottom = 200.dp)
            .alpha(alpha),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // İkon kutusu
        Box(
            modifier = Modifier
                .size(96.dp)
                .background(
                    Brush.radialGradient(listOf(page.accent.copy(0.2f), Color.Transparent)),
                    CircleShape
                )
                .drawBehind {
                    drawCircle(page.accent.copy(0.25f), radius = size.minDimension / 2f, style =
                        androidx.compose.ui.graphics.drawscope.Stroke(width = 1f))
                },
            contentAlignment = Alignment.Center
        ) {
            Text(page.emoji, fontSize = 44.sp)
        }

        Spacer(Modifier.height(32.dp))

        // Aksan çizgisi
        Box(
            Modifier.width(32.dp).height(3.dp)
                .clip(CircleShape).background(page.accent)
        )

        Spacer(Modifier.height(20.dp))

        Text(
            page.title,
            fontSize   = 24.sp,
            fontWeight = FontWeight.Bold,
            color      = TextPrimary,
            textAlign  = TextAlign.Center
        )

        Spacer(Modifier.height(14.dp))

        Text(
            page.description,
            fontSize  = 15.sp,
            color     = TextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 23.sp
        )
    }
}