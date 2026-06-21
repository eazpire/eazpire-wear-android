package com.eazpire.wear.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.eazpire.wear.R
import com.eazpire.wear.theme.EazWearColors
import kotlinx.coroutines.delay

private const val SEGMENTS = 16

@Composable
fun WearBootSplashScreen(
    targetProgress: Int,
    modifier: Modifier = Modifier,
) {
    var progress by remember { mutableIntStateOf(0) }
    val statusLines = listOf(
        stringResource(R.string.splash_status_loading),
        stringResource(R.string.splash_status_artifacts),
        stringResource(R.string.splash_status_feed),
        stringResource(R.string.splash_status_ready),
    )
    val statusIndex = ((progress.toFloat() / SEGMENTS) * statusLines.size)
        .toInt()
        .coerceIn(0, statusLines.lastIndex)

    LaunchedEffect(targetProgress) {
        while (progress < targetProgress.coerceIn(0, SEGMENTS)) {
            delay(70)
            progress += 1
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.eazpire_wear_logo),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .height(72.dp),
        )
        Spacer(modifier = Modifier.height(36.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            repeat(SEGMENTS) { index ->
                val lit = index < progress
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .background(
                            color = if (lit) EazWearColors.Orange else EazWearColors.PanelBorder,
                            shape = RoundedCornerShape(2.dp),
                        ),
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = statusLines[statusIndex],
            style = MaterialTheme.typography.bodyMedium,
            color = EazWearColors.TextMuted,
            textAlign = TextAlign.Center,
        )
    }
}
