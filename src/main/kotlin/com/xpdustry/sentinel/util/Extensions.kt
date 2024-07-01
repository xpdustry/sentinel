/*
 * This file is part of Sentinel, a powerful security plugin for Mindustry.
 *
 * MIT License
 *
 * Copyright (c) 2024 Xpdustry
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.xpdustry.sentinel.util

import arc.struct.ObjectMap
import arc.struct.Seq
import com.xpdustry.distributor.api.DistributorProvider
import com.xpdustry.distributor.api.collection.MindustryCollections
import com.xpdustry.distributor.api.util.Priority
import com.xpdustry.distributor.api.util.TypeToken
import com.xpdustry.sentinel.SentinelPlugin
import java.util.concurrent.CompletableFuture

internal fun <T> Seq<T>.asList(): List<T> = MindustryCollections.immutableList(this)

internal fun <K, V> ObjectMap<K, V>.asMap(): Map<K, V> = MindustryCollections.immutableMap(this)

internal inline fun <reified T : Any> typeToken(): TypeToken<T> = object : TypeToken<T>() {}

internal class RegistrationScope<T : Any>(private val token: TypeToken<T>) {
    fun register(service: T, priority: Priority = Priority.NORMAL) {
        DistributorProvider.get()
            .serviceManager
            .register(SentinelPlugin.INSTANCE, token, service, priority)
    }
}

internal fun <T : Any> service(token: TypeToken<T>, block: RegistrationScope<T>.() -> Unit) {
    RegistrationScope(token).block()
}

internal fun <T : Any> T.toCompletableFuture(): CompletableFuture<T> =
    CompletableFuture.completedFuture(this)
