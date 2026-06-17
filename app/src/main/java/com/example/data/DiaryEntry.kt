package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "diary_entries")
data class DiaryEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val encryptedTitle: String,
    val titleIv: String,
    val encryptedContent: String,
    val contentIv: String,
    // Path to the encrypted local image file inside private filesDir
    val encryptedImagePath: String? = null,
    val imageIv: String? = null,
    // Encrypted mood state (e.g. "HAPPY", "SAD")
    val encryptedMood: String,
    val moodIv: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false,
    val isDecoy: Boolean = false
)
