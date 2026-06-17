package com.example.api

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GeminiManager {
    private const val TAG = "GeminiManager"
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun generateContent(prompt: String, systemInstruction: String? = null): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext "⚠️ API Key Gemini chưa được cấu hình. Bạn hãy cấu nhập khóa API tại bảng điều khiển Secrets trong AI Studio để mở khóa Trợ lý AI nhé!"
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"
        
        try {
            val root = JSONObject()
            
            val contentsArray = JSONArray()
            val contentObj = JSONObject()
            val partsArray = JSONArray()
            val partObj = JSONObject()
            partObj.put("text", prompt)
            partsArray.put(partObj)
            contentObj.put("parts", partsArray)
            contentsArray.put(contentObj)
            root.put("contents", contentsArray)

            if (systemInstruction != null) {
                val systemContent = JSONObject()
                val systemParts = JSONArray()
                val systemPart = JSONObject()
                systemPart.put("text", systemInstruction)
                systemParts.put(systemPart)
                systemContent.put("parts", systemParts)
                root.put("systemInstruction", systemContent)
            }

            val generationConfig = JSONObject()
            generationConfig.put("temperature", 0.7)
            root.put("generationConfig", generationConfig)

            val requestBodyJson = root.toString()
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = requestBodyJson.toRequestBody(mediaType)

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string() ?: ""
                    Log.e(TAG, "Gemini Call Failed: $errBody")
                    return@withContext "⚠️ Không thể kết nối Gemini API. Vui lòng kiểm tra lại API Key hoặc mạng internet."
                }

                val resBody = response.body?.string() ?: return@withContext "⚠️ Phản hồi từ Gemini API rỗng."
                val responseJson = JSONObject(resBody)
                
                val candidates = responseJson.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val firstCandidate = candidates.getJSONObject(0)
                    val candidateContent = firstCandidate.optJSONObject("content")
                    if (candidateContent != null) {
                        val parts = candidateContent.optJSONArray("parts")
                        if (parts != null && parts.length() > 0) {
                            return@withContext parts.getJSONObject(0).optString("text", "Không nhận được phản hồi.")
                        }
                    }
                }
                "⚠️ Lỗi định dạng phản hồi từ Gemini API."
            }
        } catch (e: Exception) {
            e.printStackTrace()
            "⚠️ Lỗi kết nối: ${e.localizedMessage}"
        }
    }

    suspend fun generateSuggestivePrompt(mood: String): String {
        val systemInstruction = "Bạn là Trợ lý Sổ tay Tâm hồn ấm áp. Hãy khơi gợi viết nhật ký dựa trên tâm trạng ngày hôm nay. Hãy tạo ra 2 gợi ý hoặc câu hỏi khơi gợi suy nghĩ nhẹ nhàng, ngắn gọn và nghệ thuật bằng tiếng Việt."
        val prompt = "Hôm nay tôi cảm thấy: $mood. Hãy cho tôi gợi ý chủ đề viết nhật ký hôm nay, trả về dưới dạng 2 gạch đầu dòng ngắn gọn, lãng mạn."
        return generateContent(prompt, systemInstruction)
    }

    suspend fun generateMoodReport(diaryContentsList: List<String>): String {
        if (diaryContentsList.isEmpty()) {
            return "Hãy ghi một vài dòng nhật ký trước, Trợ lý ảo sẽ giúp bạn xâu chuỗi xu hướng cảm xúc và chúc bạn mỗi tối ấm áp! ✨"
        }
        val systemInstruction = "Bạn là người lắng nghe đồng hành cùng tâm trạng của chủ nhân cuốn nhật ký này. Hãy viết một lời chia sẻ, thấu cảm sâu sắc, vô cùng ấm áp, nghệ thuật và tiếp thêm năng lực dưới dạng một bức thư ngắn không quá 150 từ, xưng là 'Sổ tay nhỏ' và gọi người viết là 'Bạn nhỏ'."
        val diariesJoined = diaryContentsList.joinToString("\n---\n")
        val prompt = "Dưới đây là tổng hợp tâm sự nhật ký của tôi sau vài ngày:\n$diariesJoined\n\nHãy lắng nghe xâu chuỗi tâm tư của tôi."
        return generateContent(prompt, systemInstruction)
    }
}
