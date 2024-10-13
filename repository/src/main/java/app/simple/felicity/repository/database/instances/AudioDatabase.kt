package app.simple.felicity.repository.database.instances

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import app.simple.felicity.repository.database.dao.AudioDao
import app.simple.felicity.repository.models.normal.Audio
import app.simple.felicity.shared.utils.ConditionUtils.invert

@Database(entities = [Audio::class], exportSchema = true, version = 1)
abstract class AudioDatabase : RoomDatabase() {
    abstract fun audioDao(): AudioDao?

    companion object {
        private var instance: AudioDatabase? = null
        private const val DB_NAME = "audio.db"

        @Synchronized
        fun init(context: Context) {
            kotlin.runCatching {
                if (instance!!.isOpen.invert()) {
                    instance = Room.databaseBuilder(context, AudioDatabase::class.java, DB_NAME)
                        .fallbackToDestructiveMigration()
                        .build()
                }
            }.getOrElse {
                instance = Room.databaseBuilder(context, AudioDatabase::class.java, DB_NAME)
                    .fallbackToDestructiveMigration()
                    .build()
            }
        }

        @Synchronized
        fun getInstance(context: Context): AudioDatabase? {
            kotlin.runCatching {
                if (instance!!.isOpen.invert()) {
                    instance = Room.databaseBuilder(context, AudioDatabase::class.java, DB_NAME)
                        .fallbackToDestructiveMigration()
                        .build()
                }
            }.getOrElse {
                instance = Room.databaseBuilder(context, AudioDatabase::class.java, DB_NAME)
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
