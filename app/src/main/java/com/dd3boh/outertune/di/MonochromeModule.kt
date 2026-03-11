/*
 * Copyright (C) 2025 MonoTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 */

package com.dd3boh.outertune.di

import com.dd3boh.outertune.monochrome.MonochromeAuthRepository
import com.dd3boh.outertune.monochrome.MonochromeAuthRepositoryImpl
import com.dd3boh.outertune.monochrome.MonochromeClient
import com.dd3boh.outertune.monochrome.MonochromeClientApi
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module that wires the Monochrome client and auth abstractions.
 *
 * Both bindings are singletons so that the same [MonochromeClient] instance
 * (and its in-memory session state) is shared across the entire process.
 *
 * Replace the [MonochromeClient] binding with a real HTTP implementation once
 * the Monochrome API contract is finalised.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class MonochromeModule {

    @Singleton
    @Binds
    abstract fun bindMonochromeClientApi(impl: MonochromeClient): MonochromeClientApi

    @Singleton
    @Binds
    abstract fun bindMonochromeAuthRepository(
        impl: MonochromeAuthRepositoryImpl,
    ): MonochromeAuthRepository
}
