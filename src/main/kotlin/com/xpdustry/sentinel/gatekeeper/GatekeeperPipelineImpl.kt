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
package com.xpdustry.sentinel.gatekeeper

import com.xpdustry.distributor.api.DistributorProvider
import com.xpdustry.sentinel.SentinelScope
import kotlinx.coroutines.future.await
import kotlinx.coroutines.future.future

internal class GatekeeperPipelineImpl : GatekeeperPipeline {
    override fun pump(context: GatekeeperContext) = SentinelScope.future { pump0(context) }

    private suspend fun pump0(context: GatekeeperContext): GatekeeperResult {
        for (processor in
            DistributorProvider.get()
                .serviceManager
                .getProviders(GatekeeperPipeline.PROCESSOR_TYPE)) {
            val result =
                try {
                    processor.instance.process(context).await()
                } catch (error: Exception) {
                    processor.plugin.logger.error(
                        "Error while processing player (name={}, uuid={}, address={}) in gatekeeper pipeline",
                        context.name,
                        context.muuid.uuid,
                        context.address.hostAddress,
                        error,
                    )
                    GatekeeperResult.Success
                }
            if (result is GatekeeperResult.Failure) {
                return result
            }
        }
        return GatekeeperResult.Success
    }
}
