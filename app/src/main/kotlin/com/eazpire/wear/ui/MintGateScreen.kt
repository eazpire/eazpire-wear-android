package com.eazpire.wear.ui

import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.eazpire.wear.R
import com.eazpire.wear.auth.SessionResolver
import com.eazpire.wear.theme.EazWearColors

@Composable
fun MintGateScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    fun openMintArtifacts() {
        val creatorIntent = Intent().apply {
            setClassName("com.eazpire.creator", "com.eazpire.creator.MainActivity")
            putExtra("eaz_eazy_tab", "Artifacts")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (SessionResolver.isCreatorInstalled(context)) {
            runCatching { context.startActivity(creatorIntent) }.onSuccess { return }
        }
        CustomTabsIntent.Builder().build().launchUrl(
            context,
            Uri.parse("https://www.eazpire.com/?eazy=artifacts"),
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.mint_gate_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = EazWearColors.TextPrimary,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.mint_gate_body),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = EazWearColors.TextMuted,
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = { openMintArtifacts() },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = EazWearColors.Orange,
                contentColor = Color.White,
            ),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(
                stringResource(R.string.mint_gate_cta),
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        TextButton(onClick = onBack) {
            Text(stringResource(R.string.cancel))
        }
    }
}
