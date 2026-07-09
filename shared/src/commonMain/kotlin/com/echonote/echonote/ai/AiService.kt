package com.echonote.echonote.ai

import com.echonote.echonote.model.mockAiCondense
import com.echonote.echonote.model.mockAiExpand
import kotlinx.coroutines.delay

enum class AiAction { EXPAND, CONDENSE }

/**
 * Not içeriğini dönüştüren AI soyutlaması. Tam metni döndürür;
 * typewriter akıtma işi ViewModel'deki streaming katmanına aittir.
 */
interface AiService {
    suspend fun transform(action: AiAction, title: String, content: String): String
}

/** Gemini anahtarı girilmemişken devreye giren çevrimdışı sahte AI. */
class MockAiService : AiService {
    override suspend fun transform(action: AiAction, title: String, content: String): String {
        delay(600) // ağ gecikmesi hissi
        return when (action) {
            AiAction.EXPAND -> mockAiExpand(title, content)
            AiAction.CONDENSE -> mockAiCondense(title, content)
        }
    }
}
