package com.example.di

import android.content.Context
import com.example.data.AppDatabase
import com.example.data.DataStoreManager
import com.example.data.repository.FitnessRepositoryImpl
import com.example.domain.repository.FitnessRepository

object ServiceLocator {
    @Volatile private var database: AppDatabase? = null
    @Volatile private var dataStore: DataStoreManager? = null
    @Volatile private var repository: FitnessRepository? = null

    fun database(context: Context): AppDatabase =
        database ?: synchronized(this) {
            database ?: AppDatabase.getDatabase(context).also { database = it }
        }

    fun dataStore(context: Context): DataStoreManager =
        dataStore ?: synchronized(this) {
            dataStore ?: DataStoreManager(context.applicationContext).also { dataStore = it }
        }

    fun repository(context: Context): FitnessRepository {
        val db = database(context)
        return repository ?: synchronized(this) {
            repository ?: FitnessRepositoryImpl(db, db.fitnessDao(), dataStore(context)).also { repository = it }
        }
    }

    fun reset() {
        synchronized(this) {
            database = null
            dataStore = null
            repository = null
        }
    }
}
