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

import arc.util.Strings
import com.xpdustry.distributor.api.component.TranslatableComponent.translatable
import com.xpdustry.sentinel.processing.Processor
import com.xpdustry.sentinel.util.toCompletableFuture
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

internal object LinkDetectionProcessor : Processor<GatekeeperContext, GatekeeperResult> {

    private val LINK_REGEX = Regex("https?://|discord.gg")

    override fun process(context: GatekeeperContext) =
        (if (LINK_REGEX.containsMatchIn(Strings.stripColors(context.name)))
                GatekeeperResult.Failure(
                    translatable("sentinel.gatekeeper.link-detection.failure"),
                    10.seconds.toJavaDuration())
            else GatekeeperResult.Success)
            .toCompletableFuture()
}
