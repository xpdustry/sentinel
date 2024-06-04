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
package com.xpdustry.watchdog

import com.xpdustry.distributor.api.component.Component
import com.xpdustry.watchdog.api.history.HistoryAuthor
import com.xpdustry.watchdog.api.history.HistoryConfig
import com.xpdustry.watchdog.api.history.HistoryEntry
import com.xpdustry.watchdog.api.history.HistoryRenderer
import mindustry.Vars

internal class SimpleHistoryRenderer : HistoryRenderer {
    override fun render(entries: List<HistoryEntry>): Component {
        TODO("Not yet implemented")
    }

    private fun render(
        entry: HistoryEntry,
        name: Boolean,
        position: Boolean,
        indent: Int,
        id: Boolean = true,
    ): String {
        val builder = StringBuilder("[white]")
        if (name) {
            builder.append(getName(entry.author))
            if (id && entry.author is HistoryAuthor.Player) {
                builder.append(" [lightgray](#").append(entry.author.muuid.uuid).append(")")
            }
            builder.append("[white]: ")
        }
        when (entry.type) {
            HistoryEntry.Type.PLACING ->
                builder.append("Constructing [accent]").append(entry.block.name)
            HistoryEntry.Type.PLACE ->
                builder.append("Constructed [accent]").append(entry.block.name)
            HistoryEntry.Type.BREAKING ->
                builder.append("Deconstructing [accent]").append(entry.block.name)
            HistoryEntry.Type.BREAK ->
                builder.append("Deconstructed [accent]").append(entry.block.name)
            HistoryEntry.Type.ROTATE ->
                builder
                    .append("Rotated [accent]")
                    .append(entry.block.name)
                    .append(" [white]to [accent]")
                    .append(getOrientation(entry.rotation))
            HistoryEntry.Type.CONFIGURE ->
                renderConfiguration(
                    builder,
                    entry,
                    entry.configuration,
                    indent,
                )
        }
        if (entry.type !== HistoryEntry.Type.CONFIGURE && entry.configuration != null) {
            renderConfiguration(
                builder.append(" ".repeat(indent)).append("\n[accent] > [white]"),
                entry,
                entry.configuration,
                indent + 3,
            )
        }
        builder.append("[white]")
        if (position) {
            builder.append(" at [accent](").append(entry.x).append(", ").append(entry.y).append(")")
        }
        // builder.append(" [white]").append(renderer.renderRelativeInstant(entry.timestamp))
        return builder.toString()
    }

    private fun renderConfiguration(
        builder: StringBuilder,
        entry: HistoryEntry,
        config: HistoryConfig?,
        ident: Int,
    ) {
        when (config) {
            is HistoryConfig.Composite -> {
                builder.append("Configured [accent]").append(entry.block.name).append("[white]:")
                for (component in config.configurations) {
                    renderConfiguration(
                        builder.append("\n").append(" ".repeat(ident)).append("[accent] - [white]"),
                        entry,
                        component,
                        ident + 3,
                    )
                }
            }
            is HistoryConfig.Text -> {
                builder
                    .append("Edited [accent]")
                    .append(config.type.name.lowercase())
                    .append("[white] of [accent]")
                    .append(entry.block.name)
                if (config.type === HistoryConfig.Text.Type.MESSAGE) {
                    builder.append("[white] to [gray]").append(config.text)
                }
            }
            is HistoryConfig.Link -> {
                if (config.type === HistoryConfig.Link.Type.RESET) {
                    builder.append("Reset [accent]").append(entry.block.name)
                    return
                }
                builder
                    .append(
                        if (config.type === HistoryConfig.Link.Type.CONNECT) {
                            "Connected"
                        } else {
                            "Disconnected"
                        },
                    )
                    .append(" [accent]")
                    .append(entry.block.name)
                    .append("[white] ")
                    .append(if (config.type === HistoryConfig.Link.Type.CONNECT) "to" else "from")
                    .append(" [accent]")
                    .append(
                        config.positions.joinToString(", ") { point ->
                            "(${(point.x + entry.buildX)}, ${(point.y + entry.buildY)})"
                        },
                    )
            }
            is HistoryConfig.Canvas -> {
                builder.append("Edited [accent]").append(entry.block.name)
            }
            is HistoryConfig.Content -> {
                if (config.value == null) {
                    builder.append("Reset [accent]").append(entry.block.name)
                    return
                }
                builder
                    .append("Configured [accent]")
                    .append(entry.block.name)
                    .append("[white] to [accent]")
                    .append(config.value.name)
            }
            is HistoryConfig.Enable -> {
                builder
                    .append(if (config.value) "Enabled" else "Disabled")
                    .append(" [accent]")
                    .append(entry.block.name)
            }
            is HistoryConfig.Light -> {
                builder
                    .append("Configured [accent]")
                    .append(entry.block.name)
                    .append("[white] to [accent]")
                // .append(config.color.toHexString())
            }
            is HistoryConfig.Unknown -> {
                builder
                    .append("Configured [accent]")
                    .append(entry.block.name)
                    .append("[white] to [accent]")
                    .append(config.value?.toString() ?: "null")
            }
            null -> {
                builder
                    .append("Configured [accent]")
                    .append(entry.block.name)
                    .append("[white] to [accent]null")
            }
        }
    }

    private fun getName(author: HistoryAuthor): String {
        return if (author is HistoryAuthor.Player) {
            Vars.netServer.admins.getInfo(author.muuid.uuid).lastName
        } else {
            author.team.name.lowercase() + " " + author.unit.name
        }
    }

    private fun normalize(
        entries: List<HistoryEntry>,
        limit: Int,
    ) = entries
        .asReversed()
        .asSequence()
        .withIndex()
        .filter {
            it.index == 0 ||
                (
                    it.value.type != HistoryEntry.Type.BREAKING &&
                        it.value.type != HistoryEntry.Type.PLACING
                )
        }
        .map { it.value }
        .take(limit)
        .toList()

    private fun getOrientation(rotation: Int): String =
        when (rotation % 4) {
            0 -> "right"
            1 -> "top"
            2 -> "left"
            3 -> "bottom"
            else -> error("This should never happen")
        }
}
