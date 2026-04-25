package com.paul.infrastructure

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.paul.domain.StravaActivity
import com.paul.domain.StravaStreamEntity
import com.paul.infrastructure.converters.TimestampConverter
import com.paul.infrastructure.dao.StravaDao
import com.paul.infrastructure.persistence.PointListConverter
import kotlinx.coroutines.Dispatchers

// This tells the compiler each platform will provide its own builder
expect fun getDatabaseBuilder(): RoomDatabase.Builder<AppDatabase>

// This is the common entry point to build the DB once the builder is provided
fun getRoomDatabase(builder: RoomDatabase.Builder<AppDatabase>): AppDatabase {
    return builder
        // You likely tried to use AndroidSQLiteDriver() in a Room KMP project. In the current version of Room KMP, if you use the standard Android driver, it conflicts with the coroutine-based connection pool Room uses internally for Multiplatform, leading to that SupportSQLiteConnection error.
        .setDriver(BundledSQLiteDriver()) // Use bundled driver to work on device (kmp oroitines)
        // .setDriver(AndroidSQLiteDriver()) // Use system driver for Android Studio support
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
}

@Database(
    entities = [StravaActivity::class, StravaStreamEntity::class],
    version = 2,
    autoMigrations = [
        AutoMigration(from = 1, to = 2)
    ]
)
@TypeConverters(TimestampConverter::class, PointListConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun stravaDao(): StravaDao
}