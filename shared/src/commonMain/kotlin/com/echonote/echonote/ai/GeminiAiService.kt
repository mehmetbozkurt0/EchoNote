package com.echonote.echonote.ai

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable

/** Gemini REST API'si üzerinden gerçek metin dönüşümü yapar. */
class GeminiAiService(
    private val apiKey: String,
    private val client: HttpClient,
) : AiService {

    override suspend fun transform(action: AiAction, title: String, content: String): String {
        val response = client.post("$BASE_URL/models/$MODEL:generateContent") {
            header("x-goog-api-key", apiKey)
            contentType(ContentType.Application.Json)
            setBody(GeminiRequest(contents = listOf(GeminiContent(parts = listOf(GeminiPart(buildPrompt(action, title, content)))))))
        }
        if (!response.status.isSuccess()) {
            error("Gemini API hatası (${response.status}): ${response.bodyAsText().take(200)}")
        }
        val body: GeminiResponse = response.body()
        return body.candidates.firstOrNull()
            ?.content?.parts?.joinToString("") { it.text }
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: error("Gemini boş yanıt döndürdü")
    }

    private fun buildPrompt(action: AiAction, title: String, content: String): String = when (action) {
        AiAction.EXPAND -> """
            Aşağıdaki "$title" başlıklı Markdown notunu, yapısını ve dilini (Türkçe) koruyarak genişlet.
            Alt başlıklar, maddeler ve somut detaylar ekle. YALNIZCA genişletilmiş Markdown metnini döndür,
            başka hiçbir açıklama ekleme.

            $content
        """.trimIndent()

        AiAction.CONDENSE -> """
            Aşağıdaki "$title" başlıklı Markdown notunu, dilini (Türkçe) koruyarak kısa ve öz bir Markdown
            özetine indir. En fazla 5 madde kullan. YALNIZCA özet Markdown metnini döndür,
            başka hiçbir açıklama ekleme.

            $content
        """.trimIndent()
    }

    private companion object {
        const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
        const val MODEL = "gemini-2.5-flash"
    }
}

@Serializable
private data class GeminiRequest(val contents: List<GeminiContent>)

@Serializable
private data class GeminiContent(val parts: List<GeminiPart>)

@Serializable
private data class GeminiPart(val text: String = "")

@Serializable
private data class GeminiResponse(val candidates: List<GeminiCandidate> = emptyList())

@Serializable
private data class GeminiCandidate(val content: GeminiContent? = null)
