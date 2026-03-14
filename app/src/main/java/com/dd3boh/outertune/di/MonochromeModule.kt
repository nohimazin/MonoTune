/*
 * Copyright (C) 2025 MonoTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 */

package com.dd3boh.outertune.di

import com.nohimazin.monotune.monochrome.MonochromeAuthRepository
import com.nohimazin.monotune.monochrome.MonochromeAuthRepositoryImpl
import com.nohimazin.monotune.monochrome.MonochromeClient
import com.nohimazin.monotune.monochrome.MonochromeClientApi
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
 * [MonochromeClient] targets the hifi-api backend used by monochrome.tf.
 * Authentication is handled via the official Appwrite instance at
 * [com.dd3boh.outertune.monochrome.APPWRITE_ENDPOINT].
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
