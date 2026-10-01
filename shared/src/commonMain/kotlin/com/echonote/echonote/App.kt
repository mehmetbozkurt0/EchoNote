package com.echonote.echonote

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
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

        // Yazmalar artık anında yerel depoya indiği için burada kurtarılacak bir şey yok;
        // arka plana düşerken bekleyen senkronu uzağa göndermeye çalışmak yine değerli.
        LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.requestOutboxFlush() }

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

            // Hata bannerı kökte: her iki layout'ta ve hiç not seçili olmasa da görünür.
            AnimatedVisibility(
                visible = state.errorMessage != null,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(16.dp),
            ) {
                ErrorBanner(
                    message = state.errorMessage.orEmpty(),
                    onDismiss = viewModel::dismissError,
                )
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
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
    }
}


/** Mobil: tam ekran liste; seçimde editör sağdan kayarak üste gelir. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun CompactLayout(state: NotesUiState, viewModel: NotesViewModel) {
    // rememberSaveable: ekran döndürüldüğünde editörden listeye fırlamamak için.
    var editorOpen by rememberSaveable { mutableStateOf(false) }

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
                onBack = { editorOpen = false },
                framed = false,
                modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
            )
        }
    }

    BackHandler(enabled = editorOpen) { editorOpen = false }
}
