package app.simple.felicity.repository.database.instances

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import app.simple.felicity.repository.database.dao.StackTraceDao
import app.simple.felicity.repository.models.normal.StackTrace
import app.simple.felicity.shared.utils.ConditionUtils.invert

@Database(entities = [StackTrace::class], exportSchema = true, version = 1)
abstract class StackTraceDatabase : RoomDatabase() {
    abstract fun stackTraceDao(): StackTraceDao?

    companion object {
        private var instance: StackTraceDatabase? = null
        private const val DB_NAME = "stacktrace.db"

        @Synchronized
        fun init(context: Context) {
            kotlin.runCatching {
                if (instance!!.isOpen.invert()) {
                    instance =
                        Room.databaseBuilder(context, StackTraceDatabase::class.java, DB_NAME)
                            .fallbackToDestructiveMigration()
                            .build()
                }
            }.getOrElse {
                instance = Room.databaseBuilder(context, StackTraceDatabase::class.java, DB_NAME)
                    .fallbackToDestructiveMigration()
                    .build()
            }
        }

        @Synchronized
        fun getInstance(context: Context): StackTraceDatabase? {
            kotlin.runCatching {
                if (instance!!.isOpen.invert()) {
                    instance =
                        Room.databaseBuilder(context, StackTraceDatabase::class.java, DB_NAME)
                            .fallbackToDestructiveMigration()
                            .build()
                }
            }.getOrElse {
                instance = Room.databaseBuilder(context, StackTraceDatabase::class.java, DB_NAME)
                    .fallbackToDestructiveMigration()
                    .build()
            }

            return instance
        }

        @Synchronized
        fun getInstance(): StackTraceDatabase? {
            return instance
        }
    }
}
