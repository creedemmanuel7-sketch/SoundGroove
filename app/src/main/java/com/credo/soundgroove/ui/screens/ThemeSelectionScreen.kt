package com.credo.soundgroove.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.credo.soundgroove.ui.components.SoundGrooveLogo
import com.credo.soundgroove.ui.theme.*
import kotlinx.coroutines.launch

private enum class OnboardingStep { Welcome, Theme, Ready }

@Composable
fun ThemeSelectionScreen(
    onThemeSelected: (AppTheme) -> Unit
) {
    var selectedTheme by remember { mutableStateOf(AppTheme.NOIR_ABSOLU) }
    var step by remember { mutableStateOf(OnboardingStep.Welcome) }
    val revealState = rememberSgThemeRevealState()
    val scope = rememberCoroutineScope()
    val activeColor = resolveAccentColor(selectedTheme, AppAccent.VIOLET)

    SgThemeRevealHost(
        baseTheme = selectedTheme,
        revealState = revealState
    ) { theme ->
        val previewAccent = resolveAccentColor(theme, AppAccent.VIOLET)
        SgScreenBackground(appTheme = theme) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = SgSpacing.screenHorizontal)
                    .padding(bottom = SgSpacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                OnboardingStepIndicator(
                    currentStep = step,
                    accentColor = previewAccent,
                    modifier = Modifier.padding(top = SgSpacing.screenTop)
                )

                AnimatedContent(
                    targetState = step,
                    transitionSpec = {
                        val forward = targetState.ordinal > initialState.ordinal
                        val enter = fadeIn(SgMotion.tweenMediumOf()) +
                            slideInHorizontally(
                                initialOffsetX = { if (forward) it / 4 else -it / 4 },
                                animationSpec = SgMotion.tweenMediumOf()
                            )
                        val exit = fadeOut(SgMotion.tweenFastAccelOf()) +
                            slideOutHorizontally(
                                targetOffsetX = { if (forward) -it / 4 else it / 4 },
                                animationSpec = SgMotion.tweenFastAccelOf()
                            )
                        enter togetherWith exit
                    },
                    label = "onboardingStep",
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) { currentStep ->
                    when (currentStep) {
                        OnboardingStep.Welcome -> OnboardingWelcomeStep(
                            accentColor = previewAccent,
                            modifier = Modifier.fillMaxSize()
                        )
                        OnboardingStep.Theme -> OnboardingThemeStep(
                            selectedTheme = selectedTheme,
                            revealState = revealState,
                            scope = scope,
                            onThemeChange = { selectedTheme = it },
                            modifier = Modifier.fillMaxSize()
                        )
                        OnboardingStep.Ready -> OnboardingReadyStep(
                            selectedTheme = selectedTheme,
                            accentColor = previewAccent,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                OnboardingNavBar(
                    step = step,
                    accentColor = activeColor,
                    selectedTheme = selectedTheme,
                    onBack = {
                        step = when (step) {
                            OnboardingStep.Theme -> OnboardingStep.Welcome
                            OnboardingStep.Ready -> OnboardingStep.Theme
                            OnboardingStep.Welcome -> OnboardingStep.Welcome
                        }
                    },
                    onNext = {
                        step = when (step) {
                            OnboardingStep.Welcome -> OnboardingStep.Theme
                            OnboardingStep.Theme -> OnboardingStep.Ready
                            OnboardingStep.Ready -> OnboardingStep.Ready
                        }
                    },
                    onFinish = { onThemeSelected(selectedTheme) }
                )
            }
        }
    }
}

@Composable
private fun OnboardingStepIndicator(
    currentStep: OnboardingStep,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val steps = OnboardingStep.entries
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        steps.forEachIndexed { index, step ->
            val isActive = step == currentStep
            val isPast = step.ordinal < currentStep.ordinal
            val width by animateFloatAsState(
                targetValue = if (isActive) 28f else 8f,
                animationSpec = tween(SgMotion.MediumMs, easing = SgMotion.EaseOut),
                label = "stepDot"
            )
            Box(
                modifier = Modifier
                    .height(8.dp)
                    .width(width.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isActive -> accentColor
                            isPast -> accentColor.copy(alpha = 0.45f)
                            else -> TextTertiary.copy(alpha = 0.35f)
                        }
                    )
            )
            if (index < steps.lastIndex) {
                Spacer(modifier = Modifier.width(SgSpacing.sm))
            }
        }
    }
}

@Composable
private fun OnboardingWelcomeStep(
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val logoScale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(SgMotion.SlowMs, easing = SgMotion.EaseOut),
        label = "logoScale"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(modifier = Modifier.scale(logoScale)) {
            SoundGrooveLogo(accentColor = accentColor)
        }
        Spacer(modifier = Modifier.height(SgSpacing.xl))
        Text(
            text = "Bienvenue sur SoundGroove",
            style = MaterialTheme.typography.displayMedium,
            color = TextPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(SgSpacing.sm))
        Text(
            text = "Votre musique, votre ambiance",
            style = MaterialTheme.typography.headlineSmall,
            color = accentColor,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(SgSpacing.md))
        Text(
            text = "Écoutez, organisez et personnalisez votre bibliothèque locale avec une expérience pensée pour le confort et le style.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
        )
    }
}

@Composable
private fun OnboardingThemeStep(
    selectedTheme: AppTheme,
    revealState: SgThemeRevealState,
    scope: kotlinx.coroutines.CoroutineScope,
    onThemeChange: (AppTheme) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Choisis ton ambiance",
            style = MaterialTheme.typography.headlineLarge,
            color = TextPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(SgSpacing.sm))
        Text(
            text = "Sélectionne ton style visuel. Tu pourras le modifier plus tard dans les paramètres.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(SgSpacing.lg))
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(SgSpacing.md)
        ) {
            ThemeCard(
                title = "Noir Absolu",
                description = "Noir profond, contraste maximal.",
                accentColor = resolveAccentColor(AppTheme.NOIR_ABSOLU, AppAccent.VIOLET),
                bgColor = AbsoluteBlackSurface,
                isSelected = selectedTheme == AppTheme.NOIR_ABSOLU,
                onClick = { origin ->
                    launchThemeReveal(
                        revealState, scope, AppTheme.NOIR_ABSOLU, selectedTheme, origin
                    ) { onThemeChange(it) }
                }
            )
            ThemeCard(
                title = "Clair Argent",
                description = "Fond clair, lisibilité WCAG.",
                accentColor = resolveAccentColor(AppTheme.ARGENT_CLAIR, AppAccent.VIOLET),
                bgColor = ArgentClairSurface,
                isSelected = selectedTheme == AppTheme.ARGENT_CLAIR,
                onClick = { origin ->
                    launchThemeReveal(
                        revealState, scope, AppTheme.ARGENT_CLAIR, selectedTheme, origin
                    ) { onThemeChange(it) }
                }
            )
            ThemeCard(
                title = "Graphite",
                description = "Graphite mat et touches argent.",
                accentColor = resolveAccentColor(AppTheme.GRAPHITE, AppAccent.VIOLET),
                bgColor = GraphiteCard,
                isSelected = selectedTheme == AppTheme.GRAPHITE,
                onClick = { origin ->
                    launchThemeReveal(
                        revealState, scope, AppTheme.GRAPHITE, selectedTheme, origin
                    ) { onThemeChange(it) }
                }
            )
        }
    }
}

@Composable
private fun OnboardingReadyStep(
    selectedTheme: AppTheme,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val themeLabel = when (selectedTheme) {
        AppTheme.NOIR_ABSOLU -> "Noir Absolu"
        AppTheme.ARGENT_CLAIR -> "Clair Argent"
        AppTheme.GRAPHITE -> "Graphite"
    }
    val iconScale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(SgMotion.SlowMs, easing = SgMotion.EaseOut),
        label = "readyIcon"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .scale(iconScale)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(accentColor.copy(alpha = 0.28f), Color.Transparent)
                    )
                )
                .border(1.5.dp, accentColor.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(40.dp)
            )
        }
        Spacer(modifier = Modifier.height(SgSpacing.xl))
        Text(
            text = "Tout est prêt !",
            style = MaterialTheme.typography.displaySmall,
            color = TextPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(SgSpacing.sm))
        Text(
            text = "Thème « $themeLabel » sélectionné",
            style = MaterialTheme.typography.titleMedium,
            color = accentColor,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(SgSpacing.md))
        Text(
            text = "Lance ta première lecture, explore ta bibliothèque et laisse SoundGroove s'adapter à ton rythme.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun OnboardingNavBar(
    step: OnboardingStep,
    accentColor: Color,
    selectedTheme: AppTheme,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onFinish: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = SgSpacing.md),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (step != OnboardingStep.Welcome) {
            TextButton(onClick = onBack) {
                Text("Retour", color = TextSecondary)
            }
        } else {
            Spacer(modifier = Modifier.width(1.dp))
        }

        Button(
            onClick = {
                when (step) {
                    OnboardingStep.Ready -> onFinish()
                    else -> onNext()
                }
            },
            modifier = Modifier
                .height(52.dp)
                .defaultMinSize(minWidth = 160.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = accentColor,
                contentColor = if (selectedTheme == AppTheme.ARGENT_CLAIR) TextPrimary else Color.White
            ),
            shape = RoundedCornerShape(SgRadius.pill),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
        ) {
            Text(
                text = when (step) {
                    OnboardingStep.Welcome -> "Continuer"
                    OnboardingStep.Theme -> "Suivant"
                    OnboardingStep.Ready -> "Commencer l'expérience"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (step != OnboardingStep.Ready) {
                Spacer(modifier = Modifier.width(SgSpacing.xs))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun ThemeCard(
    title: String,
    description: String,
    accentColor: Color,
    bgColor: Color,
    isSelected: Boolean,
    onClick: (Offset) -> Unit
) {
    val borderAlpha by animateFloatAsState(if (isSelected) 0.7f else 0.12f, label = "borderAlpha")
    var revealOrigin by remember { mutableStateOf(Offset.Zero) }

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .sgThemeRevealOrigin { revealOrigin = it }
            .clickable { onClick(revealOrigin) },
        cornerRadius = SgRadius.lg,
        accentColor = accentColor
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (isSelected) accentColor.copy(alpha = 0.08f) else Color.Transparent
                )
                .padding(SgSpacing.cardPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(SgRadius.md))
                    .background(bgColor)
                    .border(1.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(SgRadius.md)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(accentColor.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MusicNote,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .width(28.dp)
                            .height(3.dp)
                            .clip(CircleShape)
                            .background(TextPrimary.copy(alpha = if (IsLightTheme) 0.10f else 0.15f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.55f)
                                .fillMaxHeight()
                                .background(accentColor)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(SgSpacing.md))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            if (isSelected) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Sélectionné",
                    tint = accentColor,
                    modifier = Modifier.size(22.dp)
                )
            } else {
                val ringColor = if (IsLightTheme) Color.Black else Color.White
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .border(1.5.dp, ringColor.copy(alpha = borderAlpha), CircleShape)
                )
            }
        }
    }
}
