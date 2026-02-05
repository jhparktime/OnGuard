package com.onguard.di

import android.content.Context
import com.onguard.domain.usecase.PhoneAccountValidator
import com.onguard.llm.LlamaManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideApplicationContext(@ApplicationContext context: Context): Context {
        return context
    }

    @Provides
    @Singleton
    fun provideLlamaManager(@ApplicationContext context: Context): LlamaManager {
        return LlamaManager(context)
    }

    @Provides
    @Singleton
    fun providePhoneAccountValidator(): PhoneAccountValidator = PhoneAccountValidator.Default
}
