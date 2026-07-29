package com.example.mylibrary.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.mylibrary.data.dao.ActivityDao
import com.example.mylibrary.data.dao.BackupDao
import com.example.mylibrary.data.dao.DynamicFieldDao
import com.example.mylibrary.data.dao.ItemDao
import com.example.mylibrary.data.dao.ItemTypeDao
import com.example.mylibrary.data.dao.QuoteDao
import com.example.mylibrary.data.dao.ReportDao
import com.example.mylibrary.data.dao.RecordDao
import com.example.mylibrary.data.dao.StatusDao
import com.example.mylibrary.data.dao.TagDao
import com.example.mylibrary.data.entity.ActivityEntity
import com.example.mylibrary.data.entity.FieldDefinitionEntity
import com.example.mylibrary.data.entity.FieldValueEntity
import com.example.mylibrary.data.entity.ItemEntity
import com.example.mylibrary.data.entity.ItemTagEntity
import com.example.mylibrary.data.entity.ItemTypeEntity
import com.example.mylibrary.data.entity.QuoteEntity
import com.example.mylibrary.data.entity.RecordEntity
import com.example.mylibrary.data.entity.RecordFieldValueEntity
import com.example.mylibrary.data.entity.StatusEntity
import com.example.mylibrary.data.entity.TagEntity

@Database(
    entities = [
        ItemEntity::class,
        ItemTypeEntity::class,
        RecordEntity::class,
        ActivityEntity::class,
        TagEntity::class,
        ItemTagEntity::class,
        QuoteEntity::class,
        FieldDefinitionEntity::class,
        FieldValueEntity::class,
        RecordFieldValueEntity::class,
        StatusEntity::class
    ],
    version = LibraryDatabase.SCHEMA_VERSION,
    exportSchema = true
)
@TypeConverters(DatabaseConverters::class)
abstract class LibraryDatabase : RoomDatabase() {
    abstract fun itemDao(): ItemDao
    abstract fun itemTypeDao(): ItemTypeDao
    abstract fun recordDao(): RecordDao
    abstract fun statusDao(): StatusDao
    abstract fun activityDao(): ActivityDao
    abstract fun tagDao(): TagDao
    abstract fun quoteDao(): QuoteDao
    abstract fun reportDao(): ReportDao
    abstract fun dynamicFieldDao(): DynamicFieldDao
    abstract fun backupDao(): BackupDao

    companion object {
        const val SCHEMA_VERSION = 12
        const val DATABASE_NAME = "my_library.db"

        @Volatile
        private var instance: LibraryDatabase? = null

        fun getInstance(context: Context): LibraryDatabase =
            instance ?: synchronized(this) {
                instance ?: buildDatabase(context).also { instance = it }
            }

        private fun buildDatabase(context: Context): LibraryDatabase {
            val builder = Room.databaseBuilder(
                context.applicationContext,
                LibraryDatabase::class.java,
                DATABASE_NAME
            ).addCallback(DefaultLibraryDataCallback)

            if (LibraryMigrations.ALL.isNotEmpty()) {
                builder.addMigrations(*LibraryMigrations.ALL)
            }

            return builder.build()
        }
    }
}
