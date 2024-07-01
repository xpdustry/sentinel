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

import com.xpdustry.distributor.api.DistributorProvider
import com.xpdustry.distributor.api.component.TranslatableComponent.translatable
import com.xpdustry.distributor.api.translation.TranslationArguments.array
import com.xpdustry.sentinel.AddressBlockerConfig
import com.xpdustry.sentinel.SentinelScope
import com.xpdustry.sentinel.gatekeeper.GatekeeperContext
import com.xpdustry.sentinel.gatekeeper.GatekeeperResult
import com.xpdustry.sentinel.processing.Processor
import java.net.http.HttpClient
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration
import kotlinx.coroutines.future.await
import kotlinx.coroutines.future.future

internal class AddressBlockerProcessor(config: AddressBlockerConfig, http: HttpClient) :
    Processor<GatekeeperContext, GatekeeperResult> {

    private val blocker: AddressBlocker =
        when (config) {
            is AddressBlockerConfig.None -> AddressBlocker.Noop
            is AddressBlockerConfig.VpnApi -> VpnApiAddressBlocker(config, http)
            is AddressBlockerConfig.Static -> StaticAddressBlocker(config, http)
        }

    override fun process(context: GatekeeperContext) =
        SentinelScope.future {
            val whitelist =
                DistributorProvider.get()
                    .serviceManager
                    .provide(AddressWhitelist::class.java)
                    .orElse(AddressWhitelist.Noop)
            if (blocker is AddressBlocker.Noop ||
                context.address.isLoopbackAddress ||
                context.address.isAnyLocalAddress ||
                whitelist.whitelisted(context.address).await()) {
                return@future GatekeeperResult.Success
            }
            if (blocker.blocked(context.address).await()) {
                GatekeeperResult.Failure(
                    translatable(
                        "sentinel.gatekeeper.address-blocker.failure",
                        array(context.address.hostAddress)),
                    10.seconds.toJavaDuration())
            } else {
                GatekeeperResult.Success
            }
        }
}
