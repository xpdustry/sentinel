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
package com.xpdustry.watchdog.factory

import com.xpdustry.watchdog.api.history.HistoryConfig
import com.xpdustry.watchdog.api.history.HistoryEntry
import com.xpdustry.watchdog.util.Point
import com.xpdustry.watchdog.util.asList
import mindustry.world.blocks.logic.LogicBlock
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.zip.InflaterInputStream

internal object LogicProcessorConfigurationFactory :
    LinkableBlockConfigurationFactory<LogicBlock.LogicBuild>() {
    private const val MAX_INSTRUCTIONS_SIZE = 1024 * 500

    override fun create(
        building: LogicBlock.LogicBuild,
        type: HistoryEntry.Type,
        config: Any?,
    ): HistoryConfig? {
        if (type === HistoryEntry.Type.PLACING ||
            type === HistoryEntry.Type.PLACE ||
            type === HistoryEntry.Type.BREAKING ||
            type === HistoryEntry.Type.BREAK
        ) {
            return getConfiguration(building)
        } else if (config is ByteArray) {
            return readCode(config)?.let { HistoryConfig.Text(it, HistoryConfig.Text.Type.CODE) }
        }
        return super.create(building, type, config)
    }

    override fun isLinkValid(
        building: LogicBlock.LogicBuild,
        x: Int,
        y: Int,
    ): Boolean {
        val link = building.links.find { it.x == x && it.y == y }
        return link != null && link.active
    }

    private fun getConfiguration(building: LogicBlock.LogicBuild): HistoryConfig? {
        val configurations = mutableListOf<HistoryConfig>()
        val links =
            building.links
                .asList()
                .filter { it.active }
                .map { link ->
                    Point(link.x - building.tileX(), link.y - building.tileY())
                }
                .toList()

        if (links.isNotEmpty()) {
            configurations += HistoryConfig.Link(links, true)
        }
        if (building.code.isNotBlank()) {
            configurations += HistoryConfig.Text(building.code, HistoryConfig.Text.Type.CODE)
        }

        return if (configurations.isEmpty()) {
            null
        } else if (configurations.size == 1) {
            configurations[0]
        } else {
            HistoryConfig.Composite(configurations)
        }
    }

    private fun readCode(compressed: ByteArray): String? {
        try {
            DataInputStream(InflaterInputStream(ByteArrayInputStream(compressed))).use { stream ->
                val version: Int = stream.read()
                val length: Int = stream.readInt()
                if (length > MAX_INSTRUCTIONS_SIZE) {
                    return null
                }
                val bytes = ByteArray(length)
                stream.readFully(bytes)
                val links: Int = stream.readInt()
                if (version == 0) {
                    // old version just had links
                    for (i in 0 until links) {
                        stream.readInt()
                    }
                } else {
                    for (i in 0 until links) {
                        stream.readUTF() // name
                        stream.readShort() // x
                        stream.readShort() // y
                    }
                }
                return String(bytes, StandardCharsets.UTF_8)
            }
        } catch (exception: IOException) {
            return null
        }
    }
}
