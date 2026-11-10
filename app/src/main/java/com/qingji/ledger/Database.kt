package com.qingji.ledger

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Dao
interface LedgerDao {
    @Query("SELECT * FROM entries ORDER BY date DESC, time DESC, createdAt DESC") fun observeEntries(): Flow<List<Entry>>
    @Query("SELECT * FROM categories ORDER BY sortOrder, name") fun observeCategories(): Flow<List<Category>>
    @Query("SELECT * FROM budgets") fun observeBudgets(): Flow<List<Budget>>
    @Query("SELECT * FROM goals ORDER BY createdAt DESC") fun observeGoals(): Flow<List<Goal>>
    @Query("SELECT * FROM goal_events ORDER BY date DESC, createdAt DESC") fun observeEvents(): Flow<List<GoalEvent>>
    @Query("SELECT * FROM asset_accounts ORDER BY archived,createdAt") fun observeAssets(): Flow<List<AssetAccount>>
    @Query("SELECT * FROM asset_transfers ORDER BY date DESC,createdAt DESC") fun observeTransfers(): Flow<List<AssetTransfer>>
    @Query("SELECT * FROM asset_adjustments ORDER BY date DESC,createdAt DESC") fun observeAdjustments(): Flow<List<AssetAdjustment>>
    @Query("SELECT * FROM asset_accounts") suspend fun assets():List<AssetAccount>
    @Query("SELECT * FROM asset_transfers") suspend fun transfers():List<AssetTransfer>
    @Query("SELECT * FROM asset_adjustments") suspend fun adjustments():List<AssetAdjustment>
    @Query("SELECT * FROM asset_accounts WHERE id=:id") suspend fun asset(id:String):AssetAccount?
    @Query("SELECT * FROM asset_transfers WHERE id=:id") suspend fun transfer(id:String):AssetTransfer?
    @Query("SELECT * FROM asset_adjustments WHERE id=:id") suspend fun adjustment(id:String):AssetAdjustment?
    @Upsert suspend fun putAsset(asset:AssetAccount)
    @Upsert suspend fun putAssets(assets:List<AssetAccount>)
    @Upsert suspend fun putTransfer(transfer:AssetTransfer)
    @Upsert suspend fun putTransfers(transfers:List<AssetTransfer>)
    @Upsert suspend fun putAdjustment(adjustment:AssetAdjustment)
    @Upsert suspend fun putAdjustments(adjustments:List<AssetAdjustment>)
    @Query("DELETE FROM asset_transfers") suspend fun clearTransfers()
    @Query("DELETE FROM asset_adjustments") suspend fun clearAdjustments()
    @Query("DELETE FROM asset_accounts") suspend fun clearAssets()
    @Query("SELECT * FROM entries") suspend fun entries(): List<Entry>
    @Query("SELECT * FROM categories") suspend fun categories(): List<Category>
    @Query("SELECT * FROM budgets") suspend fun budgets(): List<Budget>
    @Query("SELECT * FROM goals") suspend fun goals(): List<Goal>
    @Query("SELECT * FROM goal_events") suspend fun events(): List<GoalEvent>
    @Query("SELECT * FROM entries WHERE id=:id") suspend fun entry(id: String): Entry?
    @Query("SELECT * FROM goals WHERE id=:id") suspend fun goal(id: String): Goal?
    @Query("SELECT * FROM categories WHERE id=:id") suspend fun category(id: String): Category?
    @Query("SELECT COALESCE(SUM(amount),0) FROM entries WHERE refundOf=:id AND deletedAt IS NULL AND id != :excluding") suspend fun refunded(id: String, excluding: String = ""): Long
    @Query("SELECT COALESCE(SUM(amount),0) FROM goal_events WHERE goalId=:id") suspend fun progress(id: String): Long
    @Query("SELECT COUNT(*) FROM entries WHERE deletedAt IS NULL AND date=:date") suspend fun countOn(date: String): Int
    @Upsert suspend fun putEntry(entry: Entry)
    @Upsert suspend fun putEntries(entries: List<Entry>)
    @Upsert suspend fun putCategory(category: Category)
    @Upsert suspend fun putCategories(categories: List<Category>)
    @Upsert suspend fun putBudget(budget: Budget)
    @Upsert suspend fun putBudgets(budgets: List<Budget>)
    @Upsert suspend fun putGoal(goal: Goal)
    @Upsert suspend fun putGoals(goals: List<Goal>)
    @Upsert suspend fun putEvent(event: GoalEvent)
    @Upsert suspend fun putEvents(events: List<GoalEvent>)
    @Query("DELETE FROM budgets WHERE period=:period AND scope=:scope") suspend fun deleteBudget(period: String, scope: String)
    @Query("DELETE FROM goal_events WHERE goalId=:id") suspend fun deleteGoalEvents(id: String)
    @Query("DELETE FROM goals WHERE id=:id") suspend fun deleteGoal(id: String)
    @Query("DELETE FROM entries") suspend fun clearEntries()
    @Query("DELETE FROM categories") suspend fun clearCategories()
    @Query("DELETE FROM budgets") suspend fun clearBudgets()
    @Query("DELETE FROM goals") suspend fun clearGoals()
    @Query("DELETE FROM goal_events") suspend fun clearEvents()
}
@Database(entities = [Entry::class, Category::class, Budget::class, Goal::class, GoalEvent::class,AssetAccount::class,AssetTransfer::class,AssetAdjustment::class], version = 3, exportSchema = true)
abstract class LedgerDatabase : RoomDatabase() {
    abstract fun dao(): LedgerDao
    companion object {
        val MIGRATION_1_2=object:Migration(1,2){
            override fun migrate(db:SupportSQLiteDatabase){
                db.execSQL("ALTER TABLE `entries` ADD COLUMN `assetId` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `entries` ADD COLUMN `affectsBalance` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_entries_assetId` ON `entries` (`assetId`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `asset_accounts` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `kind` TEXT NOT NULL, `openingBalance` INTEGER NOT NULL, `archived` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `asset_transfers` (`id` TEXT NOT NULL, `fromAssetId` TEXT NOT NULL, `toAssetId` TEXT NOT NULL, `amount` INTEGER NOT NULL, `date` TEXT NOT NULL, `note` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, PRIMARY KEY(`id`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_asset_transfers_fromAssetId` ON `asset_transfers` (`fromAssetId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_asset_transfers_toAssetId` ON `asset_transfers` (`toAssetId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_asset_transfers_date` ON `asset_transfers` (`date`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `asset_adjustments` (`id` TEXT NOT NULL, `assetId` TEXT NOT NULL, `delta` INTEGER NOT NULL, `targetBalance` INTEGER NOT NULL, `date` TEXT NOT NULL, `note` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, PRIMARY KEY(`id`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_asset_adjustments_assetId` ON `asset_adjustments` (`assetId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_asset_adjustments_date` ON `asset_adjustments` (`date`)")
            }
        }
        /** v3：账单加资金池 / 预算标记，资产账户加资金池。旧 DataStore 里的预算标记由 Repository 启动时搬运。 */
        val MIGRATION_2_3=object:Migration(2,3){
            override fun migrate(db:SupportSQLiteDatabase){
                db.execSQL("ALTER TABLE `entries` ADD COLUMN `pool` TEXT NOT NULL DEFAULT 'LIQUID'")
                db.execSQL("ALTER TABLE `entries` ADD COLUMN `inBudget` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `entries` ADD COLUMN `repayment` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `asset_accounts` ADD COLUMN `pool` TEXT NOT NULL DEFAULT 'LIQUID'")
            }
        }
        fun create(context: Context) = Room.databaseBuilder(context, LedgerDatabase::class.java, "qingji.db").addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
    }
}
