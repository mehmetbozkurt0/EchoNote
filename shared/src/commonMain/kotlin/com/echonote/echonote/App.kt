package com.echonote.echonote

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.echonote.echonote.data.auth.AuthGate
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/** Split-pane (desktop/tablet) ile tam ekran yığın (mobil) arasındaki eşik. */
private val ExpandedWidthThreshold = 800.dp

@Composable
@Preview
fun App() {
    val themeMode by AppServices.settings.themeMode.collectAsStateWithLifecycle()
    val fontScale by AppServices.settings.fontScale.collectAsStateWithLifecycle()
    val systemDark = isSystemInDarkTheme()
    val palette = when (themeMode) {
        ThemeMode.System -> if (systemDark) DarkPalette else LightPalette
        ThemeMode.Dark -> DarkPalette
        ThemeMode.Light -> LightPalette
    }

    EchoTheme(palette = palette, fontScale = fontScale) {
        ApplySystemBarAppearance(isLight = palette.isLight)

        val session: SessionViewModel = viewModel { SessionViewModel() }
        val sessionState by session.uiState.collectAsStateWithLifecycle()
        val activity = rememberActivityState()

        EchoBackground(
            modifier = Modifier.trackActivity(activity),
            active = activity.isActive,
        ) {
            when (sessionState.gate) {
                AuthGate.Loading -> LoadingGate()

                AuthGate.SignedOut -> AuthScreen(
                    state = sessionState,
                    onSignIn = session::signIn,
                    onSignUp = session::signUp,
                    onDismissError = session::dismissError,
                )

                // RefreshFailure da buraya düşer: çevrimdışıyken saklı oturum yenilenemez
                // ama kullanıcı kendi yerel notlarından kilitlenmemeli.
                AuthGate.SignedIn -> NotesApp(
                    accountEmail = sessionState.email,
                    onSignOut = session::signOut,
                    activity = activity,
                )
            }
        }
    }
}

@Composable
private fun BoxScope.LoadingGate() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = EchoColors.primaryBright, strokeWidth = 2.dp)
    }
}

@Composable
private fun BoxScope.NotesApp(
    accountEmail: String?,
    onSignOut: () -> Unit,
    activity: ActivityState,
) {
    val viewModel: NotesViewModel = viewModel { NotesViewModel() }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val geminiKey by AppServices.settings.geminiApiKey.collectAsStateWithLifecycle()
    val themeMode by AppServices.settings.themeMode.collectAsStateWithLifecycle()
    val fontScale by AppServices.settings.fontScale.collectAsStateWithLifecycle()

    // Yazmak da etkileşimdir: fiziksel klavyeyle yazarken işaretçi olayı gelmez.
    LaunchedEffect(state.editorContent, state.selectedNoteId) { activity.touch() }

    val searchFocus = remember { FocusRequester() }
    var tab by rememberSaveable { mutableStateOf(HomeTab.Notes) }

    // Yazmalar yerel depoya iniyor; arka plana düşerken bekleyen senkronu uzağa
    // göndermeye çalışmak yine de değerli.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.requestOutboxFlush() }

    val settings: @Composable (Dp) -> Unit = { bottomPadding ->
        SettingsPane(
            accountEmail = accountEmail,
            geminiApiKey = geminiKey,
            onGeminiApiKeyChange = AppServices.settings::setGeminiApiKey,
            themeMode = themeMode,
            onThemeModeChange = AppServices.settings::setThemeMode,
            fontScale = fontScale,
            onFontScaleChange = AppServices.settings::setFontScale,
            trashed = state.trashedNotes,
            onRestore = viewModel::restoreFromTrash,
            onDeleteForever = viewModel::deleteForever,
            onSignOut = onSignOut,
            bottomPadding = bottomPadding,
        )
    }

    // Insets kök yerine sayfa içeriklerine uygulanır: zeminler status bar'ın
    // arkasına taşar (edge-to-edge), yazılar ise güvenli alanda kalır.
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .appShortcuts(
                onNewNote = viewModel::createNote,
                onFocusSearch = {
                    tab = HomeTab.Notes
                    runCatching { searchFocus.requestFocus() }
                },
                onToggleRead = viewModel::toggleReadMode,
                onEscape = {
                    tab = HomeTab.Notes
                    viewModel.clearSearch()
                },
            )
    ) {
        if (maxWidth >= ExpandedWidthThreshold) {
            ExpandedLayout(state, viewModel, searchFocus, tab, { tab = it }, settings)
        } else {
            CompactLayout(state, viewModel, searchFocus, tab, { tab = it }, settings)
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

    state.pendingDeleteNote?.let { note ->
        DeleteConfirmDialog(
            note = note,
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::cancelDelete,
        )
    }
}

/**
 * Masaüstü: sol şerit (gezinme) + liste + editör. Tasarımın üç panelli düzeni.
 *
 * Alt gezinme çubuğu yerine sol şerit: fare ile çalışırken hedefler kenarda daha
 * yakın ve geniş ekranda dikey alan değerli.
 */
@Composable
private fun ExpandedLayout(
    state: NotesUiState,
    viewModel: NotesViewModel,
    searchFocus: FocusRequester,
    tab: HomeTab,
    onTabChange: (HomeTab) -> Unit,
    settings: @Composable (Dp) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        HomeSideRail(
            current = tab,
            onSelect = onTabChange,
            onNewNote = viewModel::createNote,
            sync = state.sync,
        )
        VerticalHairline()

        when (tab) {
            HomeTab.Settings -> Box(Modifier.weight(1f).fillMaxHeight()) { settings(0.dp) }

            HomeTab.Tags -> {
                Box(Modifier.width(360.dp).fillMaxHeight()) {
                    TagsPane(
                        tagCounts = state.tagCounts,
                        active = state.activeTag,
                        onSelect = { tag ->
                            viewModel.setTagFilter(tag)
                            onTabChange(HomeTab.Notes)
                        },
                    )
                }
                VerticalHairline()
                EditorPane(state, viewModel, Modifier.weight(1f).fillMaxHeight())
            }

            HomeTab.Notes -> {
                NoteListPane(
                    state = state,
                    onSelect = viewModel::selectNote,
                    onDelete = viewModel::requestDelete,
                    onSearchChange = viewModel::updateSearchQuery,
                    onTagFilter = viewModel::setTagFilter,
                    onTogglePin = viewModel::togglePinned,
                    onSortChange = viewModel::setSort,
                    searchFocus = searchFocus,
                    modifier = Modifier.width(360.dp).fillMaxHeight(),
                )
                VerticalHairline()
                EditorPane(state, viewModel, Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}

@Composable
private fun VerticalHairline() {
    Box(Modifier.width(1.dp).fillMaxHeight().background(EchoColors.outline))
}

@Composable
private fun EditorPane(state: NotesUiState, viewModel: NotesViewModel, modifier: Modifier) {
    NoteEditorPane(
        state = state,
        onContentChange = viewModel::updateContent,
        onTitleChange = viewModel::updateTitle,
        onExpand = viewModel::expandSelected,
        onCondense = viewModel::condenseSelected,
        onUndo = viewModel::undoSelected,
        onStop = viewModel::stopStreaming,
        onToggleTask = viewModel::toggleTask,
        onAddTag = viewModel::addTag,
        onRemoveTag = viewModel::removeTag,
        onExport = viewModel::exportSelected,
        onToggleReadMode = viewModel::toggleReadMode,
        onDelete = { state.selectedNoteId?.let(viewModel::requestDelete) },
        modifier = modifier,
    )
}

/**
 * Mobil: başlık + sekmeli gövde + alt gezinme; sağ altta yeni not düğmesi.
 * Not seçilince editör sağdan kayarak üste gelir.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun CompactLayout(
    state: NotesUiState,
    viewModel: NotesViewModel,
    searchFocus: FocusRequester,
    tab: HomeTab,
    onTabChange: (HomeTab) -> Unit,
    settings: @Composable (Dp) -> Unit,
) {
    // rememberSaveable: ekran döndürüldüğünde editörden listeye fırlamamak için.
    var editorOpen by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        HomeHeader(state.sync)
        Box(Modifier.weight(1f)) {
            when (tab) {
                HomeTab.Notes -> NoteListPane(
                    state = state,
                    onSelect = { id ->
                        viewModel.selectNote(id)
                        editorOpen = true
                    },
                    onDelete = viewModel::requestDelete,
                    onSearchChange = viewModel::updateSearchQuery,
                    onTagFilter = viewModel::setTagFilter,
                    onTogglePin = viewModel::togglePinned,
                    onSortChange = viewModel::setSort,
                    searchFocus = searchFocus,
                    bottomPadding = 72.dp,
                )

                HomeTab.Tags -> TagsPane(
                    tagCounts = state.tagCounts,
                    active = state.activeTag,
                    onSelect = { tag ->
                        viewModel.setTagFilter(tag)
                        onTabChange(HomeTab.Notes)
                    },
                )

                HomeTab.Settings -> settings(0.dp)
            }

            // Yeni not düğmesi yalnızca notlar sekmesinde: diğer sekmelerdeki bağlam
            // gezinmek ya da ayar değiştirmek, yazmak değil.
            if (tab == HomeTab.Notes) {
                NewNoteFab(
                    onClick = {
                        viewModel.createNote()
                        editorOpen = true
                    },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 20.dp),
                )
            }
        }
        HomeBottomBar(current = tab, onSelect = onTabChange)
    }

    AnimatedVisibility(
        visible = editorOpen,
        enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
        exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
    ) {
        // Tam opak perde: alttaki liste hiçbir koşulda görünmez; zemin
        // status bar'ın arkasına kadar uzanır, içerik insets ile korunur.
        Box(Modifier.fillMaxSize().background(EchoColors.canvas)) {
            NoteEditorPane(
                state = state,
                onContentChange = viewModel::updateContent,
                onTitleChange = viewModel::updateTitle,
                onExpand = viewModel::expandSelected,
                onCondense = viewModel::condenseSelected,
                onUndo = viewModel::undoSelected,
                onStop = viewModel::stopStreaming,
                onToggleTask = viewModel::toggleTask,
                onAddTag = viewModel::addTag,
                onRemoveTag = viewModel::removeTag,
                onExport = viewModel::exportSelected,
                onToggleReadMode = viewModel::toggleReadMode,
                onDelete = { state.selectedNoteId?.let(viewModel::requestDelete) },
                onBack = { editorOpen = false },
                modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
            )
        }
    }

    BackHandler(enabled = editorOpen) { editorOpen = false }
}
