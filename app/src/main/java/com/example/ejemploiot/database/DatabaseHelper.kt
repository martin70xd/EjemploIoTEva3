package com.example.ejemploiot.database

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.ejemploiot.model.MotionEventModel

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "IoTLocalDatabase.db"
        private const val DATABASE_VERSION = 1

        const val TABLE_EVENTS = "offline_events"
        const val COL_ID = "id"
        const val COL_SENSOR_NAME = "sensor_name"
        const val COL_LOCATION = "location"
        const val COL_TIMESTAMP = "timestamp"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = ("CREATE TABLE $TABLE_EVENTS ("
                + "$COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "$COL_SENSOR_NAME TEXT,"
                + "$COL_LOCATION TEXT,"
                + "$COL_TIMESTAMP INTEGER)")
        db.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_EVENTS")
        onCreate(db)
    }

    // Insertar evento cuando no hay internet
    fun insertOfflineEvent(sensorName: String, location: String, timestamp: Long): Long {
        val db = this.writableDatabase
        val values = ContentValues()
        values.put(COL_SENSOR_NAME, sensorName)
        values.put(COL_LOCATION, location)
        values.put(COL_TIMESTAMP, timestamp)

        return db.insert(TABLE_EVENTS, null, values)
    }

    // Obtener todos los eventos guardados para sincronizar
    fun getOfflineEvents(): List<MotionEventModel> {
        val list = mutableListOf<MotionEventModel>()
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_EVENTS", null)

        if (cursor.moveToFirst()) {
            do {
                val event = MotionEventModel(
                    id = "local_${cursor.getInt(0)}",
                    sensorId = "offline",
                    sensorName = cursor.getString(1),
                    location = cursor.getString(2),
                    timestamp = cursor.getLong(3),
                    userEmail = "Sincronizado Offline"
                )
                list.add(event)
            } while (cursor.moveToNext())
        }
        cursor.close()
        return list
    }

    // Limpiar base de datos local después de sincronizar
    fun clearOfflineEvents() {
        val db = this.writableDatabase
        db.execSQL("DELETE FROM $TABLE_EVENTS")
    }
}
