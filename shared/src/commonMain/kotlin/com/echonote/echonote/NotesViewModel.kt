package com.echonote.echonote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echonote.echonote.ai.AiAction
import com.echonote.echonote.ai.AiService
import com.echonote.echonote.data.InMemoryNotesRepository
import com.echonote.echonote.data.NotesRepository
import com.echonote.echonote.model.Note
import com.echonote.echonote.model.localDeviceId
import com.echonote.echonote.model.newNoteId
import com.echonote.echonote.model.nowIsoUtc
import com.echonote.echonote.model.parseTimestampOrNull
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

data class NotesUiState(
    val notes: List<Note> = emptyList(),
    val selectedNoteId: String? = null,
    /** AI şu anda hangi nota yazıyor; null ise akış yok. */
    val streamingNoteId: String? = null,
    /** Seçili not için geri alınabilecek bir sürüm var mı. */
    val canUndo: Boolean = false,
    /** Secrets boş bırakıldıysa true: sahte AI + bellek içi depo. */
    val isOfflineMode: Boolean = false,
    val errorMessage: String? = null,
) {
    val selectedNote: Note? get() = notes.firstOrNull { it.id == selectedNoteId }
    val isStreamingSelected: Boolean get() = streamingNoteId != null && streamingNoteId == selectedNoteId
}

class NotesViewModel(
    private val repository: NotesRepository = createNotesRepository(),
    private val aiService: AiService = createAiService(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        NotesUiState(isOfflineMode = repository is InMemoryNotesRepository)
    )
    val uiState: StateFlow<NotesUiState> = _uiState.asStateFlow()

    /** Not başına eski içerik yığını; AI her değişiklik öncesi buraya iter. */
    private val undoStacks = mutableMapOf<String, ArrayDeque<String>>()

    /** Yerelde değişip henüz depoya yazılmamış notlar; uzak yayın bunları ezmesin. */
    private val dirtyNoteIds = mutableSetOf<String>()

    /** Silinmesi istenen ama uzak silme onayı henüz yayına düşmemiş notlar. */
    private val pendingDeleteIds = mutableSetOf<String>()
    private val saveJobs = mutableMapOf<String, Job>()
    private var aiJob: Job? = null

    init {
        viewModelScope.launch {
            repository.observeNotes()
                .catch { e ->
                    _uiState.update { it.copy(errorMessage = "Veritabanı bağlantı hatası: ${e.message}") }
                }
                .collect(::mergeRemoteNotes)
        }
    }

    // --- Kullanıcı eylemleri ---

    fun selectNote(id: String) {
        _uiState.update { it.copy(selectedNoteId = id, canUndo = canUndoFor(id)) }
    }

    fun createNote() {
        val note = Note(
            id = newNoteId(),
            title = "Yeni Not",
            content = "# Yeni Not\n\n",
            updatedAt = nowIsoUtc(),
            deviceId = localDeviceId,
        )
        // Uzak yayını beklemeden iyimser olarak listeye koy ki seçim anında çalışsın.
        _uiState.update { state ->
            state.copy(notes = listOf(note) + state.notes, selectedNoteId = note.id, canUndo = false)
        }
        viewModelScope.launch {
            try {
                repository.createNote(note)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Not oluşturulamadı: ${e.message}") }
            }
        }
    }

    fun deleteNote(id: String) {
        // Silinen nota AI yazıyorsa önce akışı kes.
        if (_uiState.value.streamingNoteId == id) stopStreaming()
        saveJobs.remove(id)?.cancel()
        dirtyNoteIds -= id
        undoStacks.remove(id)
        pendingDeleteIds += id

        // İyimser: listeden hemen düş; seçiliyse komşu nota geç.
        _uiState.update { state ->
            val remaining = state.notes.filterNot { it.id == id }
            val selected = if (state.selectedNoteId == id) remaining.firstOrNull()?.id else state.selectedNoteId
            state.copy(notes = remaining, selectedNoteId = selected, canUndo = canUndoFor(selected))
        }

        viewModelScope.launch {
            try {
                repository.deleteNote(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Başarısızsa korumayı kaldır: not bir sonraki uzak yayında geri gelir.
                pendingDeleteIds -= id
                _uiState.update { it.copy(errorMessage = "Not silinemedi: ${e.message}") }
            }
        }
    }

    fun updateContent(noteId: String, newContent: String) {
        // AI aynı nota yazarken kullanıcı düzenlemesi yok sayılır (editör zaten readOnly).
        if (_uiState.value.streamingNoteId == noteId) return
        setNoteContent(noteId, newContent)
        scheduleSave(noteId)
    }

    fun updateTitle(noteId: String, newTitle: String) {
        mutateNote(noteId) { it.copy(title = newTitle) }
        scheduleSave(noteId)
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
        setNoteContent(noteId, previous)
        scheduleSave(noteId)
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    // --- AI akışı ---

    private fun runAi(action: AiAction) {
        val note = _uiState.value.selectedNote ?: return
        if (_uiState.value.streamingNoteId != null) return

        aiJob = viewModelScope.launch {
            _uiState.update { it.copy(streamingNoteId = note.id, errorMessage = null) }
            try {
                // Tam yanıt gelmeden undo yığınına dokunma: AI hata verirse yığın kirlenmesin.
                val target = aiService.transform(action, note.title, note.content)
                pushUndo(note.id, note.content)
                dirtyNoteIds += note.id
                streamText(target).collect { partial -> setNoteContent(note.id, partial) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "AI hatası: ${e.message}") }
            } finally {
                _uiState.update { it.copy(streamingNoteId = null, canUndo = canUndoFor(it.selectedNoteId)) }
                // Tamamlanan ya da "Durdur" ile yarım kalan içeriği kalıcılaştır.
                val current = _uiState.value.notes.firstOrNull { it.id == note.id }
                if (current != null && current.content != note.content) scheduleSave(note.id)
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

    // --- Depo senkronizasyonu ---

    /**
     * Uzaktan gelen listeyi işler. Çakışma çözümü last-write-wins:
     * yerelde kaydedilmemiş (dirty) ve updated_at'i daha yeni olan not, uzak sürümü ezer;
     * diğer her durumda uzak sürüm kazanır. Liste updated_at'e göre yeniden eskiye sıralanır.
     */
    private fun mergeRemoteNotes(remote: List<Note>) {
        // Uzak yayın silineni artık içermiyorsa koruma görevini tamamlamıştır.
        pendingDeleteIds.retainAll { id -> remote.any { it.id == id } }
        _uiState.update { state ->
            val merged = remote
                .filterNot { it.id in pendingDeleteIds } // iyimser silinen, yankıyla geri dirilmesin
                .map { incoming ->
                    val local = state.notes.firstOrNull { it.id == incoming.id }
                    if (local != null && incoming.id in dirtyNoteIds && isNewer(local, incoming)) local else incoming
                }
            // İyimser eklenen ama uzak yayına henüz düşmemiş yeni notları kaybetme.
            val pendingLocal = state.notes.filter { note ->
                note.id in dirtyNoteIds && merged.none { it.id == note.id }
            }
            val sorted = (merged + pendingLocal).sortedByDescending { it.updatedAtInstant() }
            val selected = state.selectedNoteId?.takeIf { id -> sorted.any { it.id == id } }
                ?: sorted.firstOrNull()?.id
            state.copy(notes = sorted, selectedNoteId = selected, canUndo = canUndoFor(selected))
        }
    }

    private fun scheduleSave(noteId: String) {
        dirtyNoteIds += noteId
        saveJobs.remove(noteId)?.cancel()
        saveJobs[noteId] = viewModelScope.launch {
            delay(SAVE_DEBOUNCE_MS)
            persist(noteId)
        }
    }

    private suspend fun persist(noteId: String) {
        val note = _uiState.value.notes.firstOrNull { it.id == noteId } ?: return
        try {
            repository.updateNote(note)
            dirtyNoteIds -= noteId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _uiState.update { it.copy(errorMessage = "Not kaydedilemedi: ${e.message}") }
        }
    }

    // --- Yardımcılar ---

    /** Yerel mutasyonların tek kapısı: updated_at ve device_id damgasını burada basar. */
    private fun mutateNote(noteId: String, transform: (Note) -> Note) {
        _uiState.update { state ->
            state.copy(
                notes = state.notes.map { note ->
                    if (note.id == noteId) {
                        transform(note).copy(updatedAt = nowIsoUtc(), deviceId = localDeviceId)
                    } else {
                        note
                    }
                },
                canUndo = canUndoFor(state.selectedNoteId),
            )
        }
    }

    private fun setNoteContent(noteId: String, content: String) {
        mutateNote(noteId) { it.copy(content = content) }
    }

    private fun isNewer(candidate: Note, reference: Note): Boolean {
        val candidateTime = candidate.updatedAtInstant() ?: return true // çözümlenemezse yereli koru
        val referenceTime = reference.updatedAtInstant() ?: return true
        return candidateTime >= referenceTime
    }

    @OptIn(ExperimentalTime::class)
    private fun Note.updatedAtInstant(): Instant? = parseTimestampOrNull(updatedAt)

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
        const val SAVE_DEBOUNCE_MS = 600L
        const val MAX_UNDO_DEPTH = 20
    }
}
