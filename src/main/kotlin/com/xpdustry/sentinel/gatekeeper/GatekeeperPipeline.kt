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
import com.xpdustry.distributor.api.component.Component
import com.xpdustry.distributor.api.player.MUUID
import com.xpdustry.distributor.api.util.TypeToken
import com.xpdustry.sentinel.processing.Processor
import com.xpdustry.sentinel.processing.ProcessorPipeline
import com.xpdustry.sentinel.util.typeToken
import java.net.InetAddress
import java.time.Duration
import java.util.Locale

public interface GatekeeperPipeline : ProcessorPipeline<GatekeeperContext, GatekeeperResult> {
    public companion object {
        @JvmStatic
        public val PROCESSOR_TYPE: TypeToken<Processor<GatekeeperContext, GatekeeperResult>> =
            typeToken<Processor<GatekeeperContext, GatekeeperResult>>()
    }
}

public data class GatekeeperContext(
    val name: String,
    val muuid: MUUID,
    val address: InetAddress,
    val locale: Locale,
)

public sealed interface GatekeeperResult {
    public data object Success : GatekeeperResult

    public data class Failure(val reason: Component, val time: Duration) : GatekeeperResult {
        public constructor(
            reason: String,
            time: Duration
        ) : this(DistributorProvider.get().mindustryComponentDecoder.decode(reason), time)
    }
}
