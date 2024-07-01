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

import com.google.common.collect.Range
import com.google.common.collect.RangeSet
import com.google.common.collect.TreeRangeSet
import com.google.common.net.InetAddresses
import com.xpdustry.distributor.api.plugin.PluginListener
import com.xpdustry.sentinel.AddressBlockerConfig
import com.xpdustry.sentinel.SentinelScope
import java.io.IOException
import java.io.InputStream
import java.math.BigInteger
import java.net.Inet4Address
import java.net.InetAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.CompletableFuture
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.Jsoup
import org.slf4j.LoggerFactory

private val PROVIDERS =
    listOf(
        XB4NetDatacenterAddressProvider,
        XB4NetVpnAddressProvider,
        AzureAddressProvider,
        GithubActionsAddressProvider,
        AmazonWebServicesAddressProvider,
        GoogleCloudAddressProvider,
        OracleCloudAddressProvider,
    )

internal class StaticAddressBlocker(
    private val config: AddressBlockerConfig.Static,
    private val http: HttpClient,
) : AddressBlocker, PluginListener {
    private val map: RangeSet<BigInteger> = TreeRangeSet.create()

    init {
        init()
    }

    private fun init() {
        val providers =
            PROVIDERS.filter {
                config.providers.isEmpty() ||
                    config.providers.any { p -> p.equals(it.name, ignoreCase = true) }
            }

        if (providers.isEmpty()) {
            LOGGER.debug("No providers configured, skipping fetching blocked addresses")
            return
        }

        LOGGER.debug("Fetching blocked addresses from {} providers", providers.size)
        runBlocking {
            providers
                .map { provider ->
                    SentinelScope.async {
                        try {
                            val result =
                                withTimeout(10.seconds) { provider.fetchAddressRanges(http) }
                            LOGGER.debug(
                                "Found {} address ranges from provider {}",
                                result.size,
                                provider.name)
                            result
                        } catch (e: Exception) {
                            LOGGER.error(
                                "Failed to fetch address ranges from provider {}", provider.name, e)
                            emptyList()
                        }
                    }
                }
                .awaitAll()
                .flatten()
                .forEach { map.add(it) }
        }

        LOGGER.debug("Fetching done")
    }

    override fun blocked(address: InetAddress): CompletableFuture<Boolean> =
        CompletableFuture.completedFuture(map.contains(BigInteger(1, address.address)))

    companion object {
        private val LOGGER = LoggerFactory.getLogger(StaticAddressBlocker::class.java)
    }
}

private interface AddressProvider {
    val name: String

    suspend fun fetchAddressRanges(http: HttpClient): List<Range<BigInteger>>
}

private abstract class AbstractAddressProvider(override val name: String) : AddressProvider {
    override suspend fun fetchAddressRanges(http: HttpClient): List<Range<BigInteger>> {
        val response =
            withContext(Dispatchers.IO) {
                http.send(
                    HttpRequest.newBuilder().GET().uri(fetchUri()).build(),
                    HttpResponse.BodyHandlers.ofInputStream(),
                )
            }
        if (response.statusCode() != 200) {
            throw IOException(
                "Failed to download '$name' public addresses file (uri: ${response.uri()}, code: ${response.statusCode()}")
        }
        return response.body().use { extractAddressRanges(it) }
    }

    protected abstract suspend fun fetchUri(): URI

    protected abstract fun extractAddressRanges(body: InputStream): List<Range<BigInteger>>
}

private object XB4NetDatacenterAddressProvider : AbstractAddressProvider("x4bnet-datacenter") {
    override suspend fun fetchUri() =
        URI("https://raw.githubusercontent.com/X4BNet/lists_vpn/main/output/datacenter/ipv4.txt")

    override fun extractAddressRanges(body: InputStream) =
        body.bufferedReader().lineSequence().map { createInetAddressRange(it) }.toList()
}

private object XB4NetVpnAddressProvider : AbstractAddressProvider("x4bnet-vpn") {
    override suspend fun fetchUri() =
        URI("https://raw.githubusercontent.com/X4BNet/lists_vpn/main/output/vpn/ipv4.txt")

    override fun extractAddressRanges(body: InputStream) =
        body.bufferedReader().lineSequence().map { createInetAddressRange(it) }.toList()
}

private abstract class AbstractCloudAddressProvider(name: String) : AbstractAddressProvider(name) {
    @OptIn(ExperimentalSerializationApi::class)
    override fun extractAddressRanges(body: InputStream) =
        extractAddressRanges(Json.decodeFromStream<JsonObject>(body))

    protected abstract fun extractAddressRanges(json: JsonObject): List<Range<BigInteger>>
}

private object AzureAddressProvider : AbstractCloudAddressProvider("azure") {
    // This goofy aah hacky code 💀
    override suspend fun fetchUri() =
        withContext(Dispatchers.IO) {
            Jsoup.connect("https://www.microsoft.com/en-us/download/confirmation.aspx?id=56519")
                .get()
                .select("a[href*=download.microsoft.com]")
                .map { element -> element.attr("abs:href") }
                .find { it.contains("ServiceTags_Public") }
                ?.let { URI(it) }
                ?: throw IOException("Failed to find Azure public addresses download link.")
        }

    override fun extractAddressRanges(json: JsonObject) =
        json["values"]!!
            .jsonArray
            .map(JsonElement::jsonObject)
            .filter { it["name"]!!.jsonPrimitive.content == "AzureCloud" }
            .map { it["properties"]!!.jsonObject["addressPrefixes"]!!.jsonArray }
            .flatMap { array -> array.map { createInetAddressRange(it.jsonPrimitive.content) } }
}

private object GithubActionsAddressProvider : AbstractCloudAddressProvider("github-actions") {
    override suspend fun fetchUri() = URI("https://api.github.com/meta")

    override fun extractAddressRanges(json: JsonObject) =
        json["actions"]!!.jsonArray.map { createInetAddressRange(it.jsonPrimitive.content) }
}

private object AmazonWebServicesAddressProvider :
    AbstractCloudAddressProvider("amazon-web-services") {
    override suspend fun fetchUri() = URI("https://ip-ranges.amazonaws.com/ip-ranges.json")

    override fun extractAddressRanges(json: JsonObject) =
        parsePrefix(json, "prefixes", "ip_prefix") +
            parsePrefix(json, "ipv6_prefixes", "ipv6_prefix")

    private fun parsePrefix(
        json: JsonObject,
        name: String,
        element: String,
    ) =
        json[name]!!.jsonArray.map {
            createInetAddressRange(it.jsonObject[element]!!.jsonPrimitive.content)
        }
}

private object GoogleCloudAddressProvider : AbstractCloudAddressProvider("google") {
    override suspend fun fetchUri() = URI("https://www.gstatic.com/ipranges/cloud.json")

    override fun extractAddressRanges(json: JsonObject) =
        json["prefixes"]!!.jsonArray.map { extractAddress(it.jsonObject) }

    private fun extractAddress(json: JsonObject) =
        createInetAddressRange((json["ipv4Prefix"] ?: json["ipv6Prefix"])!!.jsonPrimitive.content)
}

private object OracleCloudAddressProvider : AbstractCloudAddressProvider("oracle") {
    override suspend fun fetchUri() =
        URI("https://docs.cloud.oracle.com/en-us/iaas/tools/public_ip_ranges.json")

    override fun extractAddressRanges(json: JsonObject) =
        json["regions"]!!
            .jsonArray
            .flatMap { it.jsonObject["cidrs"]!!.jsonArray }
            .map { createInetAddressRange(it.jsonObject["cidr"]!!.jsonPrimitive.content) }
}

private fun createInetAddressRange(address: String): Range<BigInteger> {
    val parts = address.split("/", limit = 2)
    val parsedAddress = InetAddresses.forString(parts[0])
    if (parts.size != 2) return Range.singleton(BigInteger(1, parsedAddress.address))
    val bigIntAddress = BigInteger(1, parsedAddress.address)
    val cidrPrefixLen = parts[1].toInt()
    val bits = if (parsedAddress is Inet4Address) 32 else 128
    val addressCount = BigInteger.ONE.shiftLeft(bits - cidrPrefixLen)
    return Range.closed(bigIntAddress, bigIntAddress + addressCount)
}
