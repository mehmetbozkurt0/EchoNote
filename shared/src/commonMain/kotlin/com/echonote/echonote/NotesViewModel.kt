package com.echonote.echonote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echonote.echonote.ai.AiAction
import com.echonote.echonote.ai.AiService
import com.echonote.echonote.data.NotesRepository
import com.echonote.echonote.data.SyncState
import com.echonote.echonote.model.Note
import com.echonote.echonote.model.deriveTitle
import com.echonote.echonote.model.exportFileName
import com.echonote.echonote.model.newNoteId
import com.echonote.echonote.model.normalizeTag
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
    /** Seçili etiket filtresi; null ise filtre yok. */
    val activeTag: String? = null,
    /** Çöp kutusundaki notlar; ana listede görünmezler. */
    val trashedNotes: List<Note> = emptyList(),
    /** Silme onayı bekleyen notun kimliği; null ise diyalog kapalı. */
    val pendingDeleteNoteId: String? = null,
    val trashOpen: Boolean = false,
    /** Okuma modu; kısayol ve ekran döndürme için ViewModel'de tutuluyor. */
    val readMode: Boolean = false,
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
    /** Depodan ilk yayın geldi mi. False iken liste "boş" değil "henüz bilinmiyor". */
    val loaded: Boolean = false,
) {
    /**
     * Listede gösterilecek notlar. `val` olarak hesaplanır (get() değil): sorgu ya da
     * liste değişmedikçe yeniden hesaplanmaz, her recomposition'da filtre koşmaz.
     */
    val visibleNotes: List<Note> = notes
        .let { list -> if (activeTag == null) list else list.filter { activeTag in it.tags } }
        .let { list ->
            if (searchQuery.isBlank()) {
                list
            } else {
                val key = searchQuery.searchKey()
                list.filter { it.title.searchKey().contains(key) || it.content.searchKey().contains(key) }
            }
        }

    /** Filtre çubuğu için kullanılan tüm etiketler. */
    val allTags: List<String> = notes.flatMap { it.tags }.distinct().sorted()

    val selectedNote: Note? get() = notes.firstOrNull { it.id == selectedNoteId }
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
    /** Yazdığımız her satıra işlenir; kalıcıdır, bkz. `AppSettings.deviceId`. */
    private val deviceId: String = createDeviceId(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotesUiState())
    val uiState: StateFlow<NotesUiState> = _uiState.asStateFlow()

    /** Not başına eski içerik yığını; oturum içi UI durumu, kalıcı olması gerekmiyor. */
    private val undoStacks = mutableMapOf<String, ArrayDeque<String>>()

    private var aiJob: Job? = null

    /** Bekleyen (geciktirilmiş) editör yazması; bkz. [schedulePersist]. */
    private var persistJob: Job? = null

    /**
     * Kapanışta önce bekleyen editör yazmasını diske indirir, sonra outbox'ı uzağa
     * boşaltmayı dener. Sıra önemli: boşaltılmamış bir yazma uzağa gidemez.
     * [onCleared] kaydı iptal eder.
     */
    private val unregisterFlushHook = SaveCoordinator.register {
        persistNow()
        repository.flushOutbox()
    }

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
        // viewModelScope burada iptal edilmek üzere: bekleyen yazmayı kaçırmamak için
        // depoya doğrudan (coroutine'siz) gönderiyoruz.
        persistJob?.cancel()
        persistJob = null
        persistEditor()
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
            deviceId = deviceId,
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

    fun toggleReadMode() = _uiState.update { it.copy(readMode = !it.readMode) }

    fun openTrash() = _uiState.update { it.copy(trashOpen = true) }

    fun closeTrash() = _uiState.update { it.copy(trashOpen = false) }

    fun restoreFromTrash(id: String) = repository.restoreNote(id)

    /** Geri dönüşü yok: hem yerelden hem uzaktan kalıcı siler. */
    fun deleteForever(id: String) = repository.deleteForever(id)

    private fun moveToTrash(id: String) {
        if (_uiState.value.streamingNoteId == id) stopStreaming()
        undoStacks.remove(id)
        // Bekleyen yazma iptal edilmezse 400 ms sonra silinen notu geri yazar.
        if (_uiState.value.selectedNoteId == id) cancelPendingPersist()
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
        schedulePersist()
    }

    /** Okuma modunda bir görev kutusuna dokunuldu: ham metni yeniden yazar. */
    fun toggleTask(lineIndex: Int) {
        val state = _uiState.value
        if (state.selectedNoteId == null || state.streamingNoteId != null) return
        val updated = toggleTask(state.editorContent, lineIndex)
        if (updated == state.editorContent) return
        _uiState.update { it.copy(editorContent = updated) }
        persistNow()
    }

    fun updateTitle(newTitle: String) {
        if (_uiState.value.selectedNoteId == null) return
        _uiState.update { it.copy(editorTitle = newTitle) }
        schedulePersist()
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
        persistNow()
    }

    /**
     * Arama yalnızca listeyi filtreler; seçimi ve editörü etkilemez. Aranan sorgu
     * seçili notu gizlese bile editör açık kalır — yazarken arama yapmak notu kapatmaz.
     */
    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun setTagFilter(tag: String?) {
        _uiState.update { it.copy(activeTag = if (it.activeTag == tag) null else tag) }
    }

    fun togglePinned(id: String) {
        val note = _uiState.value.notes.firstOrNull { it.id == id } ?: return
        repository.setPinned(id, !note.pinned)
    }

    /** Seçili nota etiket ekler; normalleştirilir ve tekrarlanmaz. */
    fun addTag(raw: String) {
        val tag = normalizeTag(raw)
        if (tag.isBlank()) return
        val note = _uiState.value.selectedNote ?: return
        if (tag in note.tags) return
        repository.saveNote(note.copy(tags = note.tags + tag, updatedAt = nowIsoUtc()))
    }

    fun removeTag(tag: String) {
        val note = _uiState.value.selectedNote ?: return
        if (tag !in note.tags) return
        repository.saveNote(note.copy(tags = note.tags - tag, updatedAt = nowIsoUtc()))
    }

    fun clearSearch() {
        _uiState.update { it.copy(searchQuery = "") }
    }

    /** Seçili notu Markdown olarak dışa aktarır. */
    fun exportSelected() {
        val state = _uiState.value
        if (!state.hasSelection) return
        val title = state.editorTitle.ifBlank { deriveTitle(state.editorContent) }
        exportNoteAsMarkdown(exportFileName(title), state.editorContent)
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
                        persistNow()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "AI hatası: ${e.message}") }
            } finally {
                _uiState.update { it.copy(streamingNoteId = null, canUndo = canUndoFor(noteId)) }
                // Tamamlanan ya da "Durdur" ile yarım kalan içeriği kalıcılaştır.
                if (_uiState.value.selectedNoteId == noteId) persistNow()
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
        _uiState.update { it.copy(loaded = true) }
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

    /**
     * Yazmayı geciktirir: her tuş vuruşunda değil, yazmaya ara verilince diske iner.
     *
     * Neden: her vuruşta `saveNote` çağrılıyordu ve bu tek bir SQLite yazmasından
     * ibaret değil — yazma `selectVisible()` akışını tetikliyor, **tüm** notlar
     * yeniden sorgulanıp dönüştürülüyor (satır başına JSON etiket çözümü), ardından
     * liste yeniden filtrelenip sıralanıyor ve besteleniyor. Ölçümde bu, 12 karakterlik
     * boş bir notta bile p90 = 36 ms'lik bir kare maliyeti demekti.
     *
     * Dayanıklılık: gecikme [PERSIST_DEBOUNCE_MS]; bu pencere [persistNow] ile kapanış,
     * not değiştirme, geri alma ve AI yollarında zaten boşaltılıyor. Aynı fikir AI
     * akışında [STREAM_SAVE_EVERY_TOKENS] olarak hâlihazırda uygulanıyordu.
     */
    private fun schedulePersist() {
        persistJob?.cancel()
        persistJob = viewModelScope.launch {
            delay(PERSIST_DEBOUNCE_MS)
            persistEditor()
        }
    }

    /** Bekleyen yazmayı hemen diske indirir. */
    private fun persistNow() {
        persistJob?.cancel()
        persistJob = null
        persistEditor()
    }

    /** Bekleyen yazmayı **yazmadan** iptal eder; silme yolunda şart. */
    private fun cancelPendingPersist() {
        persistJob?.cancel()
        persistJob = null
    }

    /** Editördeki hâli yerel depoya yazar; anında kalıcı olur, senkron veri katmanında. */
    private fun persistEditor() {
        val state = _uiState.value
        val noteId = state.selectedNoteId ?: return
        val existing = state.selectedNote
        repository.saveNote(
            Note(
                id = noteId,
                // Başlık elle yazılmadıysa içerikten türetilir; listede "Yeni Not"
                // kalabalığının sebebi buydu.
                title = state.editorTitle.ifBlank { deriveTitle(state.editorContent) },
                content = state.editorContent,
                updatedAt = nowIsoUtc(),
                deviceId = deviceId,
                // Etiket ve sabitleme editörde düzenlenmiyor; yazarken kaybolmasınlar.
                tags = existing?.tags.orEmpty(),
                pinned = existing?.pinned ?: false,
            )
        )
    }

    // --- Yardımcılar ---

    private fun openInEditor(note: Note) {
        // Önceki notun bekleyen yazması, seçim değişmeden önce diske inmeli: sonra
        // inerse yeni notun içeriğiyle eski nota yazar.
        val current = _uiState.value.selectedNoteId
        if (current != null && current != note.id) persistNow()
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

        /** Yazmaya ara verildikten sonra diske inme gecikmesi. */
        const val PERSIST_DEBOUNCE_MS = 400L
    }
}
