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
package com.xpdustry.sentinel.history

import com.xpdustry.sentinel.util.Point
import java.nio.ByteBuffer
import mindustry.ctype.MappableContent

public sealed interface BlockConfig {

    public data class Composite(val configs: List<BlockConfig>) : BlockConfig

    public data class Enable(val value: Boolean) : BlockConfig

    public data class Content(val value: MappableContent) : BlockConfig

    public data class Link(val positions: List<Point>, val connection: Boolean) : BlockConfig

    public data class Text(val text: String) : BlockConfig

    public data class Light(val color: Int) : BlockConfig

    public data class Canvas(val content: ByteBuffer) : BlockConfig

    public data object Reset : BlockConfig
}
