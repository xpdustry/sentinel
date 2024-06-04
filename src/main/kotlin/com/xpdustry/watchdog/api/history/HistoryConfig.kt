/*
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
package com.xpdustry.watchdog.api.history

import com.xpdustry.watchdog.util.Point
import mindustry.ctype.UnlockableContent
import mindustry.gen.Building
import java.awt.Color
import java.nio.ByteBuffer

public sealed interface HistoryConfig {
    public data class Enable(val value: Boolean) : HistoryConfig

    public data class Content(val value: UnlockableContent?) : HistoryConfig

    public data class Link(val positions: List<Point>, val type: Type) : HistoryConfig {
        public constructor(
            positions: List<Point>,
            connection: Boolean,
        ) : this(positions, if (connection) Type.CONNECT else Type.DISCONNECT)

        public enum class Type {
            CONNECT,
            DISCONNECT,
            RESET,
        }
    }

    public data class Composite(val configurations: List<HistoryConfig>) : HistoryConfig {
        init {
            for (configuration in configurations) {
                require(configuration !is Composite) {
                    "A Composite configuration cannot contain another."
                }
            }
        }
    }

    public data class Text(val text: String, val type: Type) : HistoryConfig {
        public enum class Type {
            MESSAGE,
            CODE,
        }
    }

    public data class Light(val color: Color) : HistoryConfig

    public data class Unknown(val value: Any?) : HistoryConfig

    public data class Canvas(val bytes: ByteBuffer) : HistoryConfig {
        public constructor(bytes: ByteArray) : this(ByteBuffer.wrap(bytes.clone()))
    }

    public fun interface Factory<B : Building> {
        public fun create(
            building: B,
            type: HistoryEntry.Type,
            config: Any?,
        ): HistoryConfig?
    }
}
