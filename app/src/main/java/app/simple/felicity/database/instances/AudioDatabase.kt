package app.simple.felicity.database.instances

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import app.simple.felicity.database.dao.AudioDao
import app.simple.felicity.models.normal.Audio
import app.simple.felicity.shared.utils.ConditionUtils.invert

@Database(entities = [Audio::class], exportSchema = true, version = 1)
abstract class AudioDatabase : RoomDatabase() {
    abstract fun audioDao(): AudioDao?

    companion object {
        private var instance: AudioDatabase? = null
        private const val db_name = "audio.db"

        @Synchronized
        fun init(context: Context) {
            kotlin.runCatching {
                if (instance!!.isOpen.invert()) {
                    instance = Room.databaseBuilder(context, AudioDatabase::class.java, db_name)
                        .fallbackToDestructiveMigration()
                        .build()
                }
            }.getOrElse {
                instance = Room.databaseBuilder(context, AudioDatabase::class.java, db_name)
                    .fallbackToDestructiveMigration()
                    .build()
            }
        }

        @Synchronized
        fun getInstance(context: Context): AudioDatabase? {
            kotlin.runCatching {
                if (instance!!.isOpen.invert()) {
                    instance = Room.databaseBuilder(context, AudioDatabase::class.java, db_name)
                        .fallbackToDestructiveMigration()
                        .build()
                }
            }.getOrElse {
                instance = Room.databaseBuilder(context, AudioDatabase::class.java, db_name)
                    .fallbackToDestructiveMigration()
                    .build()
            }

            return instance
        }

        @Synchronized
        fun getInstance(): AudioDatabase? {
            return instance
        }
    }
}
