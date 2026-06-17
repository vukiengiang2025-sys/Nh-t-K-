package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class DiaryRepository(private val diaryDao: DiaryDao) {

    val allEntriesFlow: Flow<List<DiaryEntry>> = diaryDao.getAllEntriesFlow()

    fun getEntriesFlow(isDecoy: Boolean): Flow<List<DiaryEntry>> {
        return diaryDao.getEntriesFlow(isDecoy)
    }

    suspend fun getEntryById(id: Int): DiaryEntry? {
        return diaryDao.getEntryById(id)
    }

    suspend fun saveEntry(
        id: Int = 0,
        title: String,
        content: String,
        mood: String,
        imageBytes: ByteArray?,
        dbPassword: String,
        isDecoy: Boolean,
        context: Context
    ): Boolean {
        return try {
            // 1. Encrypt text fields
            val encryptedTitleData = CryptoHelper.encrypt(title, dbPassword)
            val encryptedContentData = CryptoHelper.encrypt(content, dbPassword)
            val encryptedMoodData = CryptoHelper.encrypt(mood, dbPassword)

            // 2. Handle image encryption if new image bytes are provided
            var imagePath: String? = null
            var imageIv: String? = null

            if (imageBytes != null) {
                // Generate relative filesDir file to store safely
                val fileName = "img_${UUID.randomUUID()}.enc"
                val diaryImagesDir = File(context.filesDir, "diary_images")
                if (!diaryImagesDir.exists()) {
                    diaryImagesDir.mkdirs()
                }
                val destinationFile = File(diaryImagesDir, fileName)
                
                // Write encrypted image bytes with metadata IV prepended
                val encryptedFileBytes = CryptoHelper.encryptFileBytes(imageBytes, dbPassword)
                destinationFile.writeBytes(encryptedFileBytes)
                
                imagePath = destinationFile.absolutePath
                imageIv = "FILE_ENCRYPTED"
            } else if (id != 0) {
                // For updates, keep existing image if no new one is provided
                val existing = diaryDao.getEntryById(id)
                imagePath = existing?.encryptedImagePath
                imageIv = existing?.imageIv
            }

            val entry = DiaryEntry(
                id = id,
                encryptedTitle = encryptedTitleData.cipherText,
                titleIv = encryptedTitleData.iv,
                encryptedContent = encryptedContentData.cipherText,
                contentIv = encryptedContentData.iv,
                encryptedImagePath = imagePath,
                imageIv = imageIv,
                encryptedMood = encryptedMoodData.cipherText,
                moodIv = encryptedMoodData.iv,
                timestamp = System.currentTimeMillis(),
                isSynced = false,
                isDecoy = isDecoy
            )

            diaryDao.insertEntry(entry)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun deleteEntry(entry: DiaryEntry, context: Context) {
        entry.encryptedImagePath?.let { path ->
            val file = File(path)
            if (file.exists()) {
                file.delete()
            }
        }
        diaryDao.deleteEntry(entry)
    }

    suspend fun deleteEntryById(id: Int, context: Context) {
        val entry = diaryDao.getEntryById(id)
        if (entry != null) {
            deleteEntry(entry, context)
        }
    }

    fun loadDecryptedImage(filePath: String, dbPassword: String): Bitmap? {
        return try {
            val file = File(filePath)
            if (!file.exists()) return null
            val fileBytes = file.readBytes()
            val decryptedBytes = CryptoHelper.decryptFileBytes(fileBytes, dbPassword)
            BitmapFactory.decodeByteArray(decryptedBytes, 0, decryptedBytes.size)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun exportBackup(dbPassword: String, context: Context): String {
        val entries = diaryDao.getAllEntries()
        val jsonArray = JSONArray()

        for (entry in entries) {
            val jsonEntry = JSONObject()
            val title = CryptoHelper.decrypt(entry.encryptedTitle, entry.titleIv, dbPassword)
            val content = CryptoHelper.decrypt(entry.encryptedContent, entry.contentIv, dbPassword)
            val mood = CryptoHelper.decrypt(entry.encryptedMood, entry.moodIv, dbPassword)

            jsonEntry.put("title", title)
            jsonEntry.put("content", content)
            jsonEntry.put("mood", mood)
            jsonEntry.put("timestamp", entry.timestamp)
            jsonEntry.put("isDecoy", entry.isDecoy)

            entry.encryptedImagePath?.let { path ->
                val file = File(path)
                if (file.exists()) {
                    val fileBytes = file.readBytes()
                    val decryptedBytes = CryptoHelper.decryptFileBytes(fileBytes, dbPassword)
                    val base64Image = Base64.encodeToString(decryptedBytes, Base64.NO_WRAP)
                    jsonEntry.put("imageBase64", base64Image)
                }
            }
            jsonArray.put(jsonEntry)
        }

        val rootJson = JSONObject()
        rootJson.put("backupVersion", 2)
        rootJson.put("entries", jsonArray)

        val rawBackupString = rootJson.toString()
        val encryptedBackup = CryptoHelper.encrypt(rawBackupString, dbPassword)
        return "${encryptedBackup.iv}:${encryptedBackup.cipherText}"
    }

    suspend fun importBackup(encryptedBackupString: String, dbPassword: String, context: Context): Boolean {
        return try {
            val parts = encryptedBackupString.split(":")
            if (parts.size != 2) return false
            val iv = parts[0]
            val cipherText = parts[1]

            val rootJsonString = CryptoHelper.decrypt(cipherText, iv, dbPassword)
            if (rootJsonString.startsWith("⚠️") || rootJsonString.isEmpty()) {
                return false
            }

            val rootJson = JSONObject(rootJsonString)
            val entriesArray = rootJson.getJSONArray("entries")

            diaryDao.clearAll()
            
            val diaryImagesDir = File(context.filesDir, "diary_images")
            if (diaryImagesDir.exists()) {
                diaryImagesDir.deleteRecursively()
            }
            diaryImagesDir.mkdirs()

            for (i in 0 until entriesArray.length()) {
                val jsonEntry = entriesArray.getJSONObject(i)
                val title = jsonEntry.getString("title")
                val content = jsonEntry.getString("content")
                val mood = jsonEntry.getString("mood")
                val timestamp = jsonEntry.getLong("timestamp")
                val isDecoy = if (jsonEntry.has("isDecoy")) jsonEntry.getBoolean("isDecoy") else false

                var localImagePath: String? = null
                var imageIv: String? = null

                if (jsonEntry.has("imageBase64")) {
                    val base64Image = jsonEntry.getString("imageBase64")
                    val decryptedImageBytes = Base64.decode(base64Image, Base64.NO_WRAP)
                    
                    val fileName = "img_${UUID.randomUUID()}.enc"
                    val file = File(diaryImagesDir, fileName)
                    
                    val encryptedLocalImage = CryptoHelper.encryptFileBytes(decryptedImageBytes, dbPassword)
                    file.writeBytes(encryptedLocalImage)
                    localImagePath = file.absolutePath
                    imageIv = "FILE_ENCRYPTED"
                }

                val titleEnc = CryptoHelper.encrypt(title, dbPassword)
                val contentEnc = CryptoHelper.encrypt(content, dbPassword)
                val moodEnc = CryptoHelper.encrypt(mood, dbPassword)

                val entry = DiaryEntry(
                    encryptedTitle = titleEnc.cipherText,
                    titleIv = titleEnc.iv,
                    encryptedContent = contentEnc.cipherText,
                    contentIv = contentEnc.iv,
                    encryptedImagePath = localImagePath,
                    imageIv = imageIv,
                    encryptedMood = moodEnc.cipherText,
                    moodIv = moodEnc.iv,
                    timestamp = timestamp,
                    isSynced = true,
                    isDecoy = isDecoy
                )
                diaryDao.insertEntry(entry)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
