package com.walkcraft.app.di

import android.content.Context
import com.walkcraft.app.health.HealthConnectManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object HealthModule {

    @Provides
    @Singleton
    fun provideHealthConnectManager(@ApplicationContext ctx: Context): HealthConnectManager =
        HealthConnectManager(ctx)
}
