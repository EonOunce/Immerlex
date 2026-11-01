package com.immerlex.core.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/** 一台机器一条血缘，读它的是 `core.backup.SyncGuard`。 */
@Dao
interface SyncLineageDao {
    @Query("SELECT * FROM sync_lineage WHERE id = $SYNC_LINEAGE_ROW")
    suspend fun get(): SyncLineageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entity: SyncLineageEntity)
}
