package com.echonote.echonote

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Giriş / kayıt ekranı. Yalnızca saklı oturum hiç yokken gösterilir —
 * çevrimdışıyken yenilenemeyen oturum buraya DÜŞMEZ (bkz. toAuthState).
 */
@Composable
fun AuthScreen(
    state: SessionUiState,
    onSignIn: (String, String) -> Unit,
    onSignUp: (String, String) -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    val canSubmit = email.isNotBlank() && password.length >= 6 && !state.busy

    Box(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .widthIn(max = 420.dp)
                .echoSurface(RoundedCornerShape(24.dp))
                .padding(24.dp),
        ) {
            Text(
                text = "EchoNote",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = EchoColors.primaryBright,
            )
            Text(
                text = "Notların hesabına bağlı ve yalnızca sana görünür.",
                style = MaterialTheme.typography.bodySmall,
                color = EchoColors.textSecondary,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            AuthField(
                value = email,
                onValueChange = { email = it },
                placeholder = "E-posta",
                keyboardType = KeyboardType.Email,
            )
            AuthField(
                value = password,
                onValueChange = { password = it },
                placeholder = "Parola (en az 6 karakter)",
                keyboardType = KeyboardType.Password,
                masked = true,
            )

            AnimatedVisibility(
                visible = state.errorMessage != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                ErrorBanner(
                    message = state.errorMessage.orEmpty(),
                    onDismiss = onDismissError,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                GhostButton(
                    text = "Giriş yap",
                    onClick = { onSignIn(email.trim(), password) },
                    accent = EchoColors.primaryBright,
                    enabled = canSubmit,
                )
                GhostButton(
                    text = "Kayıt ol",
                    onClick = { onSignUp(email.trim(), password) },
                    accent = EchoColors.primary,
                    enabled = canSubmit,
                )
                if (state.busy) {
                    CircularProgressIndicator(
                        color = EchoColors.primaryBright,
                        strokeWidth = 2.dp,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AuthField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType,
    masked: Boolean = false,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .echoSurface(RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyMedium,
                color = EchoColors.textSecondary.copy(alpha = 0.7f),
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            visualTransformation = if (masked) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Next),
            textStyle = TextStyle(color = EchoColors.textPrimary, fontSize = 15.sp),
            cursorBrush = SolidColor(EchoColors.primaryBright),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
