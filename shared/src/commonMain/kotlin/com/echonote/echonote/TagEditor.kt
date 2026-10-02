package com.echonote.echonote

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Editördeki etiket satırı: mevcut etiketler çip olarak, sonunda ekleme alanı.
 * Çipe dokunmak etiketi kaldırır — ayrı bir "x" düğmesi dokunma hedefini küçültürdü.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagRow(
    tags: List<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by remember { mutableStateOf("") }

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        tags.forEach { tag ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .echoSurface(
                        shape = RoundedCornerShape(50),
                        fill = EchoColors.primary.copy(alpha = 0.14f),
                    )
                    .clickable { onRemove(tag) }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
                Text(
                    text = "#$tag",
                    style = MaterialTheme.typography.labelSmall,
                    color = EchoColors.primary,
                )
                Text(
                    text = "  ×",
                    style = MaterialTheme.typography.labelSmall,
                    color = EchoColors.primary.copy(alpha = 0.7f),
                )
            }
        }

        Box(
            Modifier
                .widthIn(min = 96.dp)
                .echoSurface(RoundedCornerShape(50))
                .padding(horizontal = 10.dp, vertical = 5.dp),
        ) {
            if (draft.isEmpty()) {
                Text(
                    text = "+ etiket",
                    style = MaterialTheme.typography.labelSmall,
                    color = EchoColors.textSecondary.copy(alpha = 0.7f),
                )
            }
            BasicTextField(
                value = draft,
                onValueChange = { draft = it },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        onAdd(draft)
                        draft = ""
                    }
                ),
                textStyle = TextStyle(color = EchoColors.textPrimary, fontSize = 12.sp),
                cursorBrush = SolidColor(EchoColors.primary),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
