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
package com.xpdustry.sentinel.gatekeeper.blocker

import com.google.common.cache.CacheBuilder
import com.xpdustry.sentinel.AddressBlockerConfig
import com.xpdustry.sentinel.SentinelScope
import java.io.IOException
import java.net.InetAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.future
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal class VpnApiAddressBlocker(
    private val config: AddressBlockerConfig.VpnApi,
    private val http: HttpClient
) : AddressBlocker {
    private val cache =
        CacheBuilder.newBuilder()
            .expireAfterAccess(Duration.ofHours(1))
            .build<InetAddress, Boolean>()

    override fun blocked(address: InetAddress) =
        SentinelScope.future {
            if (address.isLoopbackAddress || address.isAnyLocalAddress) {
                return@future false
            }
            withContext(Dispatchers.IO) { cache.get(address) { blocked0(address) } }
        }

    private fun blocked0(address: InetAddress): Boolean {
        val response =
            http.send(
                HttpRequest.newBuilder()
                    .GET()
                    .uri(URI("https://vpnapi.io/api/${address.hostAddress}?key=${config.token}"))
                    .build(),
                HttpResponse.BodyHandlers.ofString(),
            )

        if (response.statusCode() == 429) {
            throw IOException("Rate limited")
        }
        if (response.statusCode() != 200) {
            throw IOException("Unexpected status code: ${response.statusCode()}")
        }

        return Json.decodeFromString<JsonObject>(response.body())["security"]!!
            .jsonObject["vpn"]!!
            .jsonPrimitive
            .boolean
    }
}
