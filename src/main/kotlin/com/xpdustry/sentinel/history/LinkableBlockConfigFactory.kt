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

import arc.math.geom.Point2
import com.xpdustry.sentinel.util.Point
import mindustry.gen.Building

internal abstract class LinkableBlockConfigFactory<B : Building> : BlockConfigFactory<B> {
    override fun create(
        building: B,
        type: HistoryEntry.Type,
        config: Any?,
    ): BlockConfig? {
        if (config == null || !building.block().configurations.containsKey(config.javaClass)) {
            return null
        }
        return if (config is Int) {
            if (config == -1 || config == building.pos()) {
                return BlockConfig.Reset
            }
            val point = Point2.unpack(config)
            if (point.x < 0 || point.y < 0) {
                null
            } else {
                BlockConfig.Link(
                    listOf(Point(point.x - building.tileX(), point.y - building.tileY())),
                    isLinkValid(building, point.x, point.y),
                )
            }
        } else if (config is Point2) {
            // Point2 are used by schematics, so they are already relative to the building
            BlockConfig.Link(
                listOf(Point(config.x, config.y)),
                isLinkValid(building, config.x + building.tileX(), config.y + building.tileY()),
            )
        } else if (config is Array<*> && config.isArrayOf<Point2>()) {
            BlockConfig.Link(
                config.map {
                    it as Point2
                    Point(it.x, it.y)
                },
                true,
            )
        } else {
            null
        }
    }

    protected abstract fun isLinkValid(building: B, x: Int, y: Int): Boolean
}
