package com.paul.infrastructure

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.paul.domain.StravaActivity
import com.paul.infrastructure.converters.TimestampConverter
import com.paul.infrastructure.dao.StravaDao
import kotlinx.coroutines.Dispatchers

// This tells the compiler each platform will provide its own builder
expect fun getDatabaseBuilder(): RoomDatabase.Builder<AppDatabase>

// This is the common entry point to build the DB once the builder is provided
fun getRoomDatabase(builder: RoomDatabase.Builder<AppDatabase>): AppDatabase {
    return builder
        .setDriver(BundledSQLiteDriver()) // Use the bundled driver for KMP
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
}

@Database(entities = [StravaActivity::class], version = 1)
@TypeConverters(TimestampConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun stravaDao(): StravaDao
}