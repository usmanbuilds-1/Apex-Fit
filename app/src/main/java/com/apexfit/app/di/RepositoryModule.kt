package com.apexfit.app.di

import com.apexfit.app.data.repository.FitnessRepositoryImpl
import com.apexfit.app.domain.repository.FitnessRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindFitnessRepository(
        fitnessRepositoryImpl: FitnessRepositoryImpl
    ): FitnessRepository

    companion object {
        @dagger.Provides
        @Singleton
        fun provideWorkoutSessionManager(
            repository: FitnessRepository,
            dataStore: com.apexfit.app.data.DataStoreManager,
            dao: com.apexfit.app.data.FitnessDao,
            @dagger.hilt.android.qualifiers.ApplicationContext appContext: android.content.Context
        ): com.apexfit.app.WorkoutSessionManager {
            return com.apexfit.app.WorkoutSessionManager(
                repository = repository,
                dataStore = dataStore,
                scope = repository.appScope,
                dao = dao,
                appContext = appContext
            )
        }
    }
}
