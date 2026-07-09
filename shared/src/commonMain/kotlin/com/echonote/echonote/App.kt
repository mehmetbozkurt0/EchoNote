package com.echonote.echonote

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/** Split-pane (desktop/tablet) ile tam ekran yığın (mobil) arasındaki eşik. */
private val ExpandedWidthThreshold = 800.dp

@Composable
@Preview
fun App() {
    EchoTheme {
        val viewModel: NotesViewModel = viewModel { NotesViewModel() }
        val state by viewModel.uiState.collectAsStateWithLifecycle()

        MeshBackground {
            // Insets kök yerine sayfa içeriklerine uygulanır: zeminler status bar'ın
            // arkasına taşar (edge-to-edge), yazılar ise güvenli alanda kalır.
            BoxWithConstraints(Modifier.fillMaxSize()) {
                if (maxWidth >= ExpandedWidthThreshold) {
                    ExpandedLayout(state, viewModel)
                } else {
                    CompactLayout(state, viewModel)
                }
            }
        }
    }
}

/** Desktop/Tablet: yan yana cam paneller. */
@Composable
private fun ExpandedLayout(state: NotesUiState, viewModel: NotesViewModel) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(16.dp),
    ) {
        NoteListPane(
            state = state,
            onSelect = viewModel::selectNote,
            onCreate = viewModel::createNote,
            onDelete = viewModel::deleteNote,
            modifier = Modifier.width(340.dp).fillMaxHeight(),
        )
        NoteEditorPane(
            state = state,
            onContentChange = viewModel::updateContent,
            onTitleChange = viewModel::updateTitle,
            onExpand = viewModel::expandSelected,
            onCondense = viewModel::condenseSelected,
            onUndo = viewModel::undoSelected,
            onStop = viewModel::stopStreaming,
            onDismissError = viewModel::dismissError,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
    }
}

/** Mobil: tam ekran liste; seçimde editör sağdan kayarak üste gelir. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun CompactLayout(state: NotesUiState, viewModel: NotesViewModel) {
    var editorOpen by remember { mutableStateOf(false) }

    NoteListPane(
        state = state,
        onSelect = { id ->
            viewModel.selectNote(id)
            editorOpen = true
        },
        onCreate = {
            viewModel.createNote()
            editorOpen = true
        },
        onDelete = viewModel::deleteNote,
        framed = false,
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
    )

    AnimatedVisibility(
        visible = editorOpen,
        enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
        exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
    ) {
        // Tam opak perde: alttaki liste hiçbir koşulda görünmez; zemin
        // status bar'ın arkasına kadar uzanır, içerik insets ile korunur.
        Box(Modifier.fillMaxSize().background(EchoColors.SpaceBlack)) {
            NoteEditorPane(
                state = state,
                onContentChange = viewModel::updateContent,
                onTitleChange = viewModel::updateTitle,
                onExpand = viewModel::expandSelected,
                onCondense = viewModel::condenseSelected,
                onUndo = viewModel::undoSelected,
                onStop = viewModel::stopStreaming,
                onDismissError = viewModel::dismissError,
                onBack = { editorOpen = false },
                framed = false,
                modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
            )
        }
    }

    BackHandler(enabled = editorOpen) { editorOpen = false }
}
