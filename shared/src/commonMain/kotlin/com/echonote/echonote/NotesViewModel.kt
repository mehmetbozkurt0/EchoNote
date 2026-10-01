package com.echonote.echonote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echonote.echonote.ai.AiAction
import com.echonote.echonote.ai.AiService
import com.echonote.echonote.data.NotesRepository
import com.echonote.echonote.data.SyncState
import com.echonote.echonote.model.Note
import com.echonote.echonote.model.localDeviceId
import com.echonote.echonote.model.newNoteId
import com.echonote.echonote.model.nowIsoUtc
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NotesUiState(
    /** Yerel depodan türetilir; sıralama ve birleştirme veri katmanında yapılmıştır. */
    val notes: List<Note> = emptyList(),
    val searchQuery: String = "",
    /** Çöp kutusundaki notlar; ana listede görünmezler. */
    val trashedNotes: List<Note> = emptyList(),
    /** Silme onayı bekleyen notun kimliği; null ise diyalog kapalı. */
    val pendingDeleteNoteId: String? = null,
    val trashOpen: Boolean = false,
    val selectedNoteId: String? = null,
    /**
     * Editörün metni. Liste DB'den türetilirken editör VM'e aittir: her tuş vuruşunu
     * DB'ye yazıp geri okumak birkaç ms gecikme yaratır ve imleç zıplardı.
     */
    val editorTitle: String = "",
    val editorContent: String = "",
    /** AI şu anda hangi nota yazıyor; null ise akış yok. */
    val streamingNoteId: String? = null,
    /** Seçili not için geri alınabilecek bir sürüm var mı. */
    val canUndo: Boolean = false,
    val sync: SyncState = SyncState(),
    val errorMessage: String? = null,
) {
    /**
     * Listede gösterilecek notlar. `val` olarak hesaplanır (get() değil): sorgu ya da
     * liste değişmedikçe yeniden hesaplanmaz, her recomposition'da filtre koşmaz.
     */
    val visibleNotes: List<Note> = if (searchQuery.isBlank()) {
        notes
    } else {
        val key = searchQuery.searchKey()
        notes.filter { it.title.searchKey().contains(key) || it.content.searchKey().contains(key) }
    }

    val hasSelection: Boolean get() = selectedNoteId != null
    val isStreamingSelected: Boolean get() = streamingNoteId != null && streamingNoteId == selectedNoteId
    val isSearching: Boolean get() = searchQuery.isNotBlank()
    val pendingDeleteNote: Note? get() = notes.firstOrNull { it.id == pendingDeleteNoteId }
}

/**
 * Arama için normalleştirme: küçük harfe indirir ve Türkçe harfleri ASCII karşılığına
 * eşler. Böylece "sok" yazınca "şök" de bulunur ve SQLite `LIKE`'ın Türkçe'de yanlış
 * eşleşen büyük/küçük harf kuralına hiç bulaşmayız.
 *
 * Filtreleme bilinçli olarak Kotlin tarafında: notların tamamı zaten bellekte ve bu
 * ölçekte (yüzlerce not) maliyet mikrosaniyeler. Binlerce nota çıkılırsa SQLite FTS5'e
 * taşınmalı.
 */
internal fun String.searchKey(): String = buildString(length) {
    for (ch in this@searchKey) {
        append(
            when (ch) {
                'ı', 'I', 'İ', 'i' -> 'i'
                'ş', 'Ş' -> 's'
                'ğ', 'Ğ' -> 'g'
                'ü', 'Ü' -> 'u'
                'ö', 'Ö' -> 'o'
                'ç', 'Ç' -> 'c'
                else -> ch.lowercaseChar()
            }
        )
    }
}

class NotesViewModel(
    private val repository: NotesRepository = createNotesRepository(),
    private val aiService: AiService = createAiService(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotesUiState())
    val uiState: StateFlow<NotesUiState> = _uiState.asStateFlow()

    /** Not başına eski içerik yığını; oturum içi UI durumu, kalıcı olması gerekmiyor. */
    private val undoStacks = mutableMapOf<String, ArrayDeque<String>>()

    private var aiJob: Job? = null

    /** Kapanışta outbox'ı uzağa boşaltma denemesi; [onCleared] kaydı iptal eder. */
    private val unregisterFlushHook = SaveCoordinator.register(repository::flushOutbox)

    init {
        viewModelScope.launch {
            repository.observeNotes().collect(::onNotesFromStore)
        }
        viewModelScope.launch {
            repository.observeTrash().collect { trashed ->
                _uiState.update { it.copy(trashedNotes = trashed) }
            }
        }
        viewModelScope.launch {
            repository.observeSyncState().collect { sync -> _uiState.update { it.copy(sync = sync) } }
        }
        viewModelScope.launch {
            repository.observeErrors().collect { message ->
                _uiState.update { it.copy(errorMessage = message) }
            }
        }
    }

    override fun onCleared() {
        unregisterFlushHook()
        super.onCleared()
    }

    // --- Kullanıcı eylemleri ---

    fun selectNote(id: String) {
        val note = _uiState.value.notes.firstOrNull { it.id == id } ?: return
        openInEditor(note)
    }

    fun createNote() {
        val note = Note(
            id = newNoteId(),
            title = "Yeni Not",
            content = "# Yeni Not\n\n",
            updatedAt = nowIsoUtc(),
            deviceId = localDeviceId,
        )
        repository.saveNote(note)
        // Editör anında açılır; not listeye DB akışıyla birkaç ms içinde düşer.
        openInEditor(note)
    }

    /** Silme onayı ister; asıl silme [confirmDelete] ile olur. */
    fun requestDelete(id: String) {
        _uiState.update { it.copy(pendingDeleteNoteId = id) }
    }

    fun cancelDelete() {
        _uiState.update { it.copy(pendingDeleteNoteId = null) }
    }

    fun confirmDelete() {
        val id = _uiState.value.pendingDeleteNoteId ?: return
        _uiState.update { it.copy(pendingDeleteNoteId = null) }
        moveToTrash(id)
    }

    fun openTrash() = _uiState.update { it.copy(trashOpen = true) }

    fun closeTrash() = _uiState.update { it.copy(trashOpen = false) }

    fun restoreFromTrash(id: String) = repository.restoreNote(id)

    /** Geri dönüşü yok: hem yerelden hem uzaktan kalıcı siler. */
    fun deleteForever(id: String) = repository.deleteForever(id)

    private fun moveToTrash(id: String) {
        if (_uiState.value.streamingNoteId == id) stopStreaming()
        undoStacks.remove(id)
        repository.deleteNote(id)
        if (_uiState.value.selectedNoteId == id) {
            // Seçim kalkar; gelen liste ile komşu nota geçilir.
            _uiState.update {
                it.copy(selectedNoteId = null, editorTitle = "", editorContent = "", canUndo = false)
            }
        }
    }

    fun updateContent(newContent: String) {
        val state = _uiState.value
        val noteId = state.selectedNoteId ?: return
        // AI aynı nota yazarken kullanıcı düzenlemesi yok sayılır (editör zaten readOnly).
        if (state.streamingNoteId == noteId) return
        _uiState.update { it.copy(editorContent = newContent) }
        persistEditor()
    }

    /** Okuma modunda bir görev kutusuna dokunuldu: ham metni yeniden yazar. */
    fun toggleTask(lineIndex: Int) {
        val state = _uiState.value
        if (state.selectedNoteId == null || state.streamingNoteId != null) return
        val updated = toggleTask(state.editorContent, lineIndex)
        if (updated == state.editorContent) return
        _uiState.update { it.copy(editorContent = updated) }
        persistEditor()
    }

    fun updateTitle(newTitle: String) {
        if (_uiState.value.selectedNoteId == null) return
        _uiState.update { it.copy(editorTitle = newTitle) }
        persistEditor()
    }

    fun expandSelected() = runAi(AiAction.EXPAND)

    fun condenseSelected() = runAi(AiAction.CONDENSE)

    fun stopStreaming() {
        aiJob?.cancel()
        aiJob = null
    }

    fun undoSelected() {
        val state = _uiState.value
        val noteId = state.selectedNoteId ?: return
        if (state.streamingNoteId == noteId) return
        val previous = undoStacks[noteId]?.removeLastOrNull() ?: return
        _uiState.update { it.copy(editorContent = previous, canUndo = canUndoFor(noteId)) }
        persistEditor()
    }

    /**
     * Arama yalnızca listeyi filtreler; seçimi ve editörü etkilemez. Aranan sorgu
     * seçili notu gizlese bile editör açık kalır — yazarken arama yapmak notu kapatmaz.
     */
    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun clearSearch() {
        _uiState.update { it.copy(searchQuery = "") }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    /**
     * Bekleyen senkronu uzağa göndermeyi dener. Yazmalar zaten diske indiği için bu bir
     * kurtarma değil, erken gönderme girişimidir; başarısız olursa outbox DB'de durur.
     */
    fun requestOutboxFlush() {
        viewModelScope.launch { repository.flushOutbox() }
    }

    // --- AI akışı ---

    private fun runAi(action: AiAction) {
        val state = _uiState.value
        val noteId = state.selectedNoteId ?: return
        if (state.streamingNoteId != null) return
        val before = state.editorContent

        aiJob = viewModelScope.launch {
            _uiState.update { it.copy(streamingNoteId = noteId, errorMessage = null) }
            try {
                // Tam yanıt gelmeden undo yığınına dokunma: AI hata verirse yığın kirlenmesin.
                val target = aiService.transform(action, state.editorTitle, before)
                pushUndo(noteId, before)
                var sinceSave = 0
                streamText(target).collect { partial ->
                    _uiState.update { it.copy(editorContent = partial) }
                    // Her token'da DB'ye yazmak israf (45 ms'de bir token gelir);
                    // periyodik yaz, kesin yazma finally'de.
                    if (++sinceSave >= STREAM_SAVE_EVERY_TOKENS) {
                        sinceSave = 0
                        persistEditor()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "AI hatası: ${e.message}") }
            } finally {
                _uiState.update { it.copy(streamingNoteId = null, canUndo = canUndoFor(noteId)) }
                // Tamamlanan ya da "Durdur" ile yarım kalan içeriği kalıcılaştır.
                if (_uiState.value.selectedNoteId == noteId) persistEditor()
            }
        }
    }

    /** Metni kelime kelime (boşluk/satır sonları korunarak) akıtan typewriter Flow'u. */
    private fun streamText(full: String): Flow<String> = flow {
        val builder = StringBuilder()
        for (token in TOKEN_REGEX.findAll(full)) {
            builder.append(token.value)
            emit(builder.toString())
            delay(TYPEWRITER_DELAY_MS)
        }
    }

    // --- Depo ---

    /**
     * Yerel depodan gelen liste. Editör alanlarına **dokunulmaz** sürece seçili not hâlâ
     * duruyor: kullanıcı yazarken altından metni değiştirmek imleci bozardı. Seçili not
     * kaybolduysa (başka cihazda silinmiş ya da yerel silme uygulanmış) komşuya geçilir.
     */
    private fun onNotesFromStore(notes: List<Note>) {
        _uiState.update { state ->
            val selectionAlive = state.selectedNoteId != null &&
                notes.any { it.id == state.selectedNoteId }
            if (selectionAlive) {
                state.copy(notes = notes)
            } else {
                val fallback = notes.firstOrNull()
                state.copy(
                    notes = notes,
                    selectedNoteId = fallback?.id,
                    editorTitle = fallback?.title.orEmpty(),
                    editorContent = fallback?.content.orEmpty(),
                    canUndo = canUndoFor(fallback?.id),
                )
            }
        }
    }

    /** Editördeki hâli yerel depoya yazar; anında kalıcı olur, senkron veri katmanında. */
    private fun persistEditor() {
        val state = _uiState.value
        val noteId = state.selectedNoteId ?: return
        repository.saveNote(
            Note(
                id = noteId,
                title = state.editorTitle,
                content = state.editorContent,
                updatedAt = nowIsoUtc(),
                deviceId = localDeviceId,
            )
        )
    }

    // --- Yardımcılar ---

    private fun openInEditor(note: Note) {
        _uiState.update {
            it.copy(
                selectedNoteId = note.id,
                editorTitle = note.title,
                editorContent = note.content,
                canUndo = canUndoFor(note.id),
            )
        }
    }

    private fun pushUndo(noteId: String, content: String) {
        val stack = undoStacks.getOrPut(noteId) { ArrayDeque() }
        stack.addLast(content)
        while (stack.size > MAX_UNDO_DEPTH) stack.removeFirst()
    }

    private fun canUndoFor(noteId: String?): Boolean =
        noteId != null && !undoStacks[noteId].isNullOrEmpty()

    private companion object {
        val TOKEN_REGEX = Regex("""\S+\s*""")
        const val TYPEWRITER_DELAY_MS = 45L
        const val MAX_UNDO_DEPTH = 20
        const val STREAM_SAVE_EVERY_TOKENS = 25
    }
}
