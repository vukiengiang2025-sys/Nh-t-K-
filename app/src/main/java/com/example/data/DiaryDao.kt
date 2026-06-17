package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DiaryDao {
    @Query("SELECT * FROM diary_entries WHERE isDecoy = :isDecoy ORDER BY timestamp DESC")
    fun getEntriesFlow(isDecoy: Boolean): Flow<List<DiaryEntry>>

    @Query("SELECT * FROM diary_entries WHERE isDecoy = :isDecoy ORDER BY timestamp DESC")
    suspend fun getEntries(isDecoy: Boolean): List<DiaryEntry>

    @Query("SELECT * FROM diary_entries ORDER BY timestamp DESC")
    fun getAllEntriesFlow(): Flow<List<DiaryEntry>>

    @Query("SELECT * FROM diary_entries ORDER BY timestamp DESC")
    suspend fun getAllEntries(): List<DiaryEntry>

    @Query("SELECT * FROM diary_entries WHERE id = :id")
    suspend fun getEntryById(id: Int): DiaryEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: DiaryEntry): Long

    @Delete
    suspend fun deleteEntry(entry: DiaryEntry)

    @Query("DELETE FROM diary_entries WHERE id = :id")
    suspend fun deleteEntryById(id: Int)

    @Query("DELETE FROM diary_entries")
    suspend fun clearAll()
}
