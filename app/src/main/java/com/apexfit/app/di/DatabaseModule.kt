package com.apexfit.app.di

import android.content.Context
import com.apexfit.app.data.AppDatabase
import com.apexfit.app.data.FitnessDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getDatabase(context)
    }

    @Provides
    @Singleton
    fun provideFitnessDao(database: AppDatabase): FitnessDao {
        return database.fitnessDao()
    }
}
