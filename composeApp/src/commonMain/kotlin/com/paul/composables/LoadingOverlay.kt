package com.paul.composables

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun LoadingOverlay(isLoading: Boolean, loadingText: String) {
    AnimatedVisibility(
        visible = isLoading,
        modifier = Modifier.fillMaxSize(), // Takes full overlay space
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f))
                // Use clickable with empty lambda to consume clicks and prevent interaction below
                .clickable(enabled = true) {},
            contentAlignment = Alignment.Center // Center content within the Box
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(
                    modifier = Modifier.size(60.dp),
                    color = MaterialTheme.colors.primary // Use theme color
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = loadingText,
                    color = Color.White, // Contrast against dark background
                    style = MaterialTheme.typography.h6, // Slightly smaller header style
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp) // Add horizontal padding
                )
            }
        }
    }
}