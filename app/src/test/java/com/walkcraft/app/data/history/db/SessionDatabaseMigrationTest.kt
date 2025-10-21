package com.walkcraft.app.data.history.db

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.FrameworkSQLiteOpenHelperFactory
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class SessionDatabaseMigrationTest {
    private lateinit var context: Context
    private val dbName = "session-migration-test.db"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    @Test
    fun migrationAddsTotalStepsAndPreservesExistingData() = runTest {
        createVersion1Database()
        val db = Room.databaseBuilder(context, SessionDatabase::class.java, dbName)
            .addMigrations(SessionDatabase.MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()

        val sessions = runBlocking { db.sessionDao().all() }
        assertEquals(1, sessions.size)
        val session = sessions.first()
        assertEquals("legacy", session.id)
        assertEquals(90, session.avgHr)
        assertNull(session.totalSteps)
        db.close()
    }

    private fun createVersion1Database() {
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE IF NOT EXISTS sessions (id TEXT NOT NULL PRIMARY KEY, avgHr INTEGER)")
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val database = helper.writableDatabase
        database.execSQL("INSERT INTO sessions (id, avgHr) VALUES ('legacy', 90)")
        database.close()
    }
}
