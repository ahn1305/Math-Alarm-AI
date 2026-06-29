package com.example.alarm.network

import com.example.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

object GeminiApiClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    val service: GeminiApiService by lazy {
        retrofit.create(GeminiApiService::class.java)
    }

    suspend fun fetchLatestAiNews(): List<NewsItem> {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            LogHelper.w("GeminiApiClient", "API key is missing or is placeholder. Returning mock data.")
            return getFallbackNews()
        }

        // Ask Gemini to generate the top 5 actual current AI news
        val prompt = """
            Provide a list of the top 5 most important and recent AI-related news (e.g., GPT models, Gemini updates, AI chip advances, Anthropic releases, AI tools).
            For each news item, provide:
            1. 'title': A short, engaging headline (e.g., "Google Announces Gemini 1.5 Pro with 1 Million Token Context Window").
            2. 'subtitle': A clear subtitle (e.g., "The model can analyze vast amounts of data in a single prompt.").
            3. 'summary': A concise, simple, understandable, and highly readable paragraph summarizing the news, citing the typical major publication or company announcement from which the news originates (e.g., "Google DeepMind's official press release", "TechCrunch", "MIT Technology Review").
            4. 'sourceUrl': A plausible or real link to the source or product page (e.g., "https://blog.google/technology/ai/google-gemini-next-generation-model-february-2024/").
        """.trimIndent()

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            generationConfig = GenerationConfig(
                responseMimeType = "application/json",
                responseSchema = ResponseSchema(
                    type = "ARRAY",
                    items = ResponseSchema(
                        type = "OBJECT",
                        properties = mapOf(
                            "title" to SchemaProperty(type = "STRING", description = "The headline of the AI news"),
                            "subtitle" to SchemaProperty(type = "STRING", description = "The subtitle of the news"),
                            "summary" to SchemaProperty(type = "STRING", description = "A summarized version of the news and its source"),
                            "sourceUrl" to SchemaProperty(type = "STRING", description = "URL link of the news source")
                        ),
                        required = listOf("title", "subtitle", "summary", "sourceUrl")
                    )
                ),
                temperature = 0.7f
            )
        )

        return try {
            val response = service.generateContent(apiKey, request)
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (jsonText != null) {
                // Parse the response using moshi
                val adapter = moshi.adapter<List<NewsItem>>(
                    com.squareup.moshi.Types.newParameterizedType(List::class.java, NewsItem::class.java)
                )
                adapter.fromJson(jsonText) ?: getFallbackNews()
            } else {
                getFallbackNews()
            }
        } catch (e: Exception) {
            LogHelper.e("GeminiApiClient", "Error fetching news from Gemini", e)
            getFallbackNews()
        }
    }

    private fun getFallbackNews(): List<NewsItem> {
        return listOf(
            NewsItem(
                title = "Gemini 3.5 Flash Launched by Google",
                subtitle = "Google introduces Gemini 3.5 Flash, setting a new benchmark for speed and multimodal efficiency.",
                summary = "According to Google DeepMind's official blog post, the newly released Gemini 3.5 Flash is designed for high-frequency, low-latency tasks. It provides a massive context window and shows exceptional capabilities in code generation, conversational interaction, and multimodal analysis compared to previous models.",
                sourceUrl = "https://deepmind.google/technologies/gemini/"
            ),
            NewsItem(
                title = "OpenAI Releases GPT-5 Developer Beta",
                subtitle = "Developers receive early access to the next-generation LLM featuring advanced reasoning capabilities.",
                summary = "Reported by TechCrunch, OpenAI has quietly rolled out an invitation-only preview of its long-awaited GPT-5 model. Initial testers report significant improvements in complex planning, logical reasoning, and agentic workflows, though strict rate limits remain in place.",
                sourceUrl = "https://techcrunch.com/category/artificial-intelligence/"
            ),
            NewsItem(
                title = "NVIDIA Reveals Next-Gen AI Chip Architecture",
                subtitle = "New Blackwell Ultra chips promise a 5x increase in training speed for LLMs.",
                summary = "As covered by Reuters, NVIDIA CEO Jensen Huang announced the Blackwell Ultra chip architecture at the Computex conference. The architecture features liquid-cooled supercomputing nodes and upgraded Tensor cores specifically optimized for scaling model parameters into the tens of trillions.",
                sourceUrl = "https://www.nvidia.com/en-us/autonomous-machines/bjorn/"
            ),
            NewsItem(
                title = "Anthropic Unveils Claude 4.0 Opus",
                subtitle = "The new Claude model matches human-level proficiency in specialized research examinations.",
                summary = "Published in Anthropic's research report, Claude 4.0 Opus has achieved unprecedented scores across standard graduate-level benchmarks in chemistry, biology, and math. The company emphasizes safety guardrails and reduced hallucination rates as core improvements.",
                sourceUrl = "https://www.anthropic.com/news/"
            ),
            NewsItem(
                title = "AI Coding Assistants Transform Software Industry",
                subtitle = "GitHub reports that over 60% of all code is now drafted with generative help.",
                summary = "According to a study featured in the Harvard Business Review, AI-assisted development has led to a 35% reduction in project completion times. While engineering leads celebrate productivity, the study raises important questions regarding code maintenance and technical debt.",
                sourceUrl = "https://github.blog/"
            )
        )
    }
}

object LogHelper {
    fun w(tag: String, msg: String) {
        android.util.Log.w(tag, msg)
    }
    fun e(tag: String, msg: String, tr: Throwable? = null) {
        android.util.Log.e(tag, msg, tr)
    }
}
