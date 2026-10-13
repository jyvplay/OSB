package dev.fusion.storage

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Framework SQLite. No ORM, no annotation processor, no hidden sync.
 * Two tables only: session key/value, and an opt-in alert log.
 */
class FusionDb(context: Context) : SQLiteOpenHelper(context.applicationContext, NAME, null, VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE session (" +
                "key TEXT PRIMARY KEY NOT NULL," +
                "value TEXT NOT NULL," +
                "updated INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE alerts (" +
                "key TEXT PRIMARY KEY NOT NULL," +
                "pkg TEXT NOT NULL," +
                "title TEXT NOT NULL," +
                "body TEXT NOT NULL," +
                "posted INTEGER NOT NULL)"
        )
        db.execSQL("CREATE INDEX idx_alerts_posted ON alerts(posted DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // No migration path published yet: drop and rebuild rather than pretend.
        db.execSQL("DROP TABLE IF EXISTS session")
        db.execSQL("DROP TABLE IF EXISTS alerts")
        onCreate(db)
    }

    companion object {
        const val NAME = "fusion.db"
        const val VERSION = 1
    }
}

/** Durable key/value for desktop session + module state. */
class SessionStore(private val db: FusionDb) {
    fun put(key: String, value: String) {
        val cv = ContentValues().apply {
            put("key", key); put("value", value); put("updated", System.currentTimeMillis())
        }
        db.writableDatabase.insertWithOnConflict("session", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun get(key: String): String? =
        db.readableDatabase.query("session", arrayOf("value"), "key = ?", arrayOf(key), null, null, null, "1")
            .use { if (it.moveToFirst()) it.getString(0) else null }

    fun remove(key: String) {
        db.writableDatabase.delete("session", "key = ?", arrayOf(key))
    }

    fun keys(): List<String> =
        db.readableDatabase.query("session", arrayOf("key"), null, null, null, null, "key ASC")
            .use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }
}

data class AlertRow(
    val key: String,
    val pkg: String,
    val title: String,
    val body: String,
    val posted: Long
)

/**
 * Opt-in alert log. Secret-visibility notifications are rejected by the caller,
 * never stored here. Retention is enforced by prune().
 */
class AlertStore(private val db: FusionDb) {
    fun insert(row: AlertRow) {
        val cv = ContentValues().apply {
            put("key", row.key); put("pkg", row.pkg)
            put("title", row.title); put("body", row.body); put("posted", row.posted)
        }
        db.writableDatabase.insertWithOnConflict("alerts", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun recent(limit: Int = 200): List<AlertRow> =
        db.readableDatabase.query(
            "alerts", arrayOf("key", "pkg", "title", "body", "posted"),
            null, null, null, null, "posted DESC", limit.toString()
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(AlertRow(c.getString(0), c.getString(1), c.getString(2), c.getString(3), c.getLong(4)))
                }
            }
        }

    fun remove(key: String) {
        db.writableDatabase.delete("alerts", "key = ?", arrayOf(key))
    }

    fun prune(olderThanMillis: Long) {
        db.writableDatabase.delete("alerts", "posted < ?", arrayOf(olderThanMillis.toString()))
    }

    fun clear() {
        db.writableDatabase.delete("alerts", null, null)
    }

    fun count(): Int =
        db.readableDatabase.rawQuery("SELECT COUNT(*) FROM alerts", null)
            .use { if (it.moveToFirst()) it.getInt(0) else 0 }
}
