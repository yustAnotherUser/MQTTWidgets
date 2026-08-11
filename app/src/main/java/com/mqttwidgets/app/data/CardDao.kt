package com.mqttwidgets.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CardDao {

    @Query("SELECT * FROM cards")
    fun getAllCards(): Flow<List<Card>>

    @Query("SELECT * FROM cards WHERE cardId = :cardId")
    suspend fun getCardById(cardId: String): Card?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCard(card: Card)

    @Update
    suspend fun updateCard(card: Card)

    @Query("DELETE FROM cards WHERE cardId = :cardId")
    suspend fun deleteCard(cardId: String)

    @Query(
        """
        UPDATE cards 
        SET lastValue = :lastValue, 
            lastFormattedValue = :lastFormattedValue, 
            lastUpdated = :lastUpdated, 
            consecutiveFailures = :consecutiveFailures 
        WHERE cardId = :cardId
        """
    )
    suspend fun updateCardValue(
        cardId: String,
        lastValue: String,
        lastFormattedValue: String,
        lastUpdated: Long,
        consecutiveFailures: Int
    )

    @Query("SELECT * FROM cards")
    suspend fun getAllCardsSync(): List<Card>

    @Query("UPDATE cards SET pinnedWidgetIds = :pinnedWidgetIds WHERE cardId = :cardId")
    suspend fun updatePinnedWidgetIds(cardId: String, pinnedWidgetIds: String)
}
