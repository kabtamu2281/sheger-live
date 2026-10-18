package et.ethiolive.app.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import et.ethiolive.app.data.AppJson
import kotlinx.serialization.KSerializer

/**
 * Read-through cache for public, non-sensitive lists (discovery feed, gift catalog, coin packages) so the app
 * opens instantly on slow networks. Balances, messages and anything private are never cached here.
 */
@Entity(tableName = "cache")
data class CacheEntry(@PrimaryKey val key: String, val json: String, val savedAt: Long)

@Dao
interface CacheDao {
    @Query("select * from cache where `key` = :key")
    suspend fun get(key: String): CacheEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entry: CacheEntry)

    @Query("delete from cache")
    suspend fun clear()
}

@Database(entities = [CacheEntry::class], version = 1, exportSchema = true)
abstract class CacheDatabase : RoomDatabase() {
    abstract fun dao(): CacheDao

    companion object {
        fun create(context: Context) = Room.databaseBuilder(context, CacheDatabase::class.java, "ethiolive-cache.db")
            .fallbackToDestructiveMigration()
            .build()
    }
}

class Cache(private val dao: CacheDao) {
    suspend fun <T> read(key: String, serializer: KSerializer<T>, maxAgeMs: Long = 24 * 3600_000L): T? {
        val e = dao.get(key) ?: return null
        if (System.currentTimeMillis() - e.savedAt > maxAgeMs) return null
        return runCatching { AppJson.decodeFromString(serializer, e.json) }.getOrNull()
    }

    suspend fun <T> write(key: String, serializer: KSerializer<T>, value: T) {
        dao.put(CacheEntry(key, AppJson.encodeToString(serializer, value), System.currentTimeMillis()))
    }

    suspend fun clear() = dao.clear()
}
