package org.imdtv.iptvtv.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    version = 20,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4), AutoMigration(from = 4, to = 5), AutoMigration(from = 5, to = 6), AutoMigration(from = 6, to = 7), AutoMigration(from = 7, to = 8), AutoMigration(from = 8, to = 9), AutoMigration(from = 9, to = 10), AutoMigration(from = 10, to = 11), AutoMigration(from = 11, to = 12), AutoMigration(from = 12, to = 13), AutoMigration(from = 13, to = 14), AutoMigration(from = 14, to = 15), AutoMigration(from = 15, to = 16), AutoMigration(from = 16, to = 17), AutoMigration(from = 17, to = 18), AutoMigration(from = 18, to = 19), AutoMigration(from = 19, to = 20, spec = AppDatabase.MergeSettingsGlobal::class)] /* 13 (a programme index) was never released: 11 -> 14 skips it, see MIGRATION_11_14 */, // 4: (providerId, categoryId) on movie/series for fast category counts
    entities = [
        ProviderEntity::class, CategoryEntity::class, ChannelEntity::class, MovieEntity::class,
        SeriesEntity::class, EpisodeEntity::class, EpgSourceEntity::class, EpgChannelEntity::class,
        ProgrammeEntity::class, EpgLinkEntity::class, NameRuleEntity::class, HideRuleEntity::class,
        FavouriteEntity::class, ProgressEntity::class, RecentChannelEntity::class, ReminderEntity::class,
        CustomGroupEntity::class, CustomGroupChannelEntity::class, org.imdtv.iptvtv.recording.RecordingEntity::class,
    ],
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    /** 18–19 (never released) kept merge settings per playlist; they moved to the app settings, leaving one switch per playlist. */
    @androidx.room.DeleteColumn.Entries(
        androidx.room.DeleteColumn(tableName = "provider", columnName = "mergeDuplicates"),
        androidx.room.DeleteColumn(tableName = "provider", columnName = "mergeBy"),
        androidx.room.DeleteColumn(tableName = "provider", columnName = "mergeAcross"),
    )
    class MergeSettingsGlobal : androidx.room.migration.AutoMigrationSpec
    abstract fun providerDao(): ProviderDao
    abstract fun categoryDao(): CategoryDao
    abstract fun channelDao(): ChannelDao
    abstract fun movieDao(): MovieDao
    abstract fun seriesDao(): SeriesDao
    abstract fun programmeDao(): ProgrammeDao
    abstract fun epgDao(): EpgDao
    abstract fun ruleDao(): RuleDao
    abstract fun favouriteDao(): FavouriteDao
    abstract fun progressDao(): ProgressDao
    abstract fun customGroupDao(): CustomGroupDao
    abstract fun recentDao(): RecentDao
    abstract fun recordingDao(): org.imdtv.iptvtv.recording.RecordingDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "iptv.db").addMigrations(MIGRATION_11_14).build()

        /** 0.5.0 -> 0.5.1: only the playlist's contentOff. Room takes this longest step, so released installs never
         *  build v13's programme index (a minute and a half of black screen on a big guide). */
        val MIGRATION_11_14 = object : androidx.room.migration.Migration(11, 14) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) =
                db.execSQL("ALTER TABLE `provider` ADD COLUMN `contentOff` TEXT NOT NULL DEFAULT ''")
        }
    }
}
