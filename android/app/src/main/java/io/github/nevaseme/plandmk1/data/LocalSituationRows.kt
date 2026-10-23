package io.github.nevaseme.plandmk1.data

import androidx.room.*
import io.github.nevaseme.plandmk1.core.PhonePosition
import io.github.nevaseme.plandmk1.core.SosMemo
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "phone_positions", primaryKeys = ["groupId", "originId"])
data class PhonePositionRow(@Embedded val value: PhonePosition)

@Entity(tableName = "sos_memos", primaryKeys = ["groupId", "answerId"])
data class SosMemoRow(@Embedded val value: SosMemo)

@Dao
interface LocalSituationDao {
    @Query("SELECT * FROM phone_positions WHERE groupId = :group")
    fun observePositions(group: String): Flow<List<PhonePositionRow>>
    @Query("SELECT * FROM phone_positions WHERE groupId = :group")
    suspend fun positions(group: String): List<PhonePositionRow>
    @Query("SELECT * FROM phone_positions WHERE groupId = :group AND originId = :origin")
    suspend fun position(group: String, origin: String): PhonePositionRow?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putPosition(row: PhonePositionRow)

    @Query("SELECT * FROM sos_memos WHERE groupId = :group")
    fun observeMemos(group: String): Flow<List<SosMemoRow>>
    @Query("SELECT * FROM sos_memos WHERE groupId = :group")
    suspend fun memos(group: String): List<SosMemoRow>
    @Query("SELECT * FROM sos_memos WHERE groupId = :group AND answerId = :answer")
    suspend fun memo(group: String, answer: String): SosMemoRow?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putMemo(row: SosMemoRow)
}
