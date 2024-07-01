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

import com.xpdustry.distributor.api.component.Component
import com.xpdustry.distributor.api.component.ListComponent.components
import com.xpdustry.distributor.api.component.NumberComponent.number
import com.xpdustry.distributor.api.component.TextComponent.empty
import com.xpdustry.distributor.api.component.TextComponent.newline
import com.xpdustry.distributor.api.component.TextComponent.space
import com.xpdustry.distributor.api.component.TextComponent.text
import com.xpdustry.distributor.api.component.TranslatableComponent.translatable
import com.xpdustry.distributor.api.component.style.ComponentColor.ACCENT
import com.xpdustry.distributor.api.translation.TranslationArguments.array
import mindustry.Vars

internal class HistoryRendererImpl : HistoryRenderer {

    override fun render(entries: List<HistoryEntry>, actor: HistoryActor.Player) =
        render0(
            translatable("sentinel.history.header.actor", array(getDisplayName(actor)))
                .append(newline()),
            entries,
            location = true)

    override fun render(entries: List<HistoryEntry>, x: Int, y: Int) =
        render0(
            translatable(
                    "sentinel.history.header.coordinates",
                    array(number(x, ACCENT), number(y, ACCENT)))
                .append(newline()),
            entries,
            name = true)

    override fun render(entries: List<HistoryEntry>) =
        render0(empty(), entries, name = true, location = true)

    private fun render0(
        header: Component,
        entries: List<HistoryEntry>,
        name: Boolean = false,
        location: Boolean = false,
    ): Component =
        components()
            .append(header)
            .modify {
                entries
                    .flatMap { entry -> render0(entry).map { entry to it } }
                    .forEach { (entry, component) ->
                        it.append(text(" > ", ACCENT))
                        if (name) {
                            it.append(getDisplayName(entry.actor), text(":"), space())
                        }
                        it.append(component)
                        if (location) {
                            it.append(
                                space(),
                                translatable(
                                    "sentinel.history.location",
                                    array(
                                        number(entry.buildX, ACCENT),
                                        number(entry.buildY, ACCENT))))
                        }
                        it.append(newline())
                    }
            }
            .build()

    private fun render0(entry: HistoryEntry): List<Component> {
        var components =
            when (entry.type) {
                HistoryEntry.Type.PLACING ->
                    listOf(
                        translatable(
                            "sentinel.history.type.placing",
                            array(translatable(entry.block, ACCENT))))
                HistoryEntry.Type.PLACE ->
                    listOf(
                        translatable(
                            "sentinel.history.type.place",
                            array(translatable(entry.block, ACCENT))))
                HistoryEntry.Type.BREAKING ->
                    listOf(
                        translatable(
                            "sentinel.history.type.breaking",
                            array(translatable(entry.block, ACCENT))))
                HistoryEntry.Type.BREAK ->
                    listOf(
                        translatable(
                            "sentinel.history.type.break",
                            array(translatable(entry.block, ACCENT))))
                HistoryEntry.Type.ROTATE ->
                    listOf(
                        translatable(
                            "sentinel.history.type.rotate",
                            array(
                                translatable(entry.block, ACCENT),
                                getDisplayOrientation(entry.rotation))))
                HistoryEntry.Type.CONFIGURE -> render0(entry, entry.configuration)
            }
        if (entry.type !== HistoryEntry.Type.CONFIGURE && entry.configuration != null) {
            components = render0(entry, entry.configuration) + components
        }
        return components
    }

    private fun render0(entry: HistoryEntry, config: BlockConfig?): List<Component> =
        when (config) {
            is BlockConfig.Composite -> config.configs.flatMap { render0(entry, it) }
            is BlockConfig.Text ->
                listOf(
                    translatable(
                        "sentinel.history.type.configure.text",
                        array(translatable(entry.block, ACCENT))))
            is BlockConfig.Link ->
                listOf(
                    translatable()
                        .modify {
                            if (config.connection) it.setKey("sentinel.history.type.configure.link")
                            else it.setKey("sentinel.history.type.configure.unlink")
                        }
                        .setParameters(
                            array(
                                translatable(entry.block, ACCENT),
                                text(
                                    config.positions.joinToString(", ") { point ->
                                        "(${(point.x + entry.buildX)}, ${(point.y + entry.buildY)})"
                                    })))
                        .build())
            is BlockConfig.Canvas ->
                listOf(
                    translatable(
                        "sentinel.history.type.configure.canvas",
                        array(translatable(entry.block, ACCENT))))
            is BlockConfig.Content ->
                listOf(
                    translatable(
                        "sentinel.history.type.configure.content",
                        array(
                            translatable(entry.block, ACCENT), translatable(config.value, ACCENT))))
            is BlockConfig.Enable ->
                listOf(
                    translatable(
                        if (config.value) "sentinel.history.type.configure.enabled"
                        else "sentinel.history.type.configure.disabled",
                        array(translatable(entry.block, ACCENT))))
            is BlockConfig.Light ->
                listOf(
                    translatable(
                        "sentinel.history.type.configure.light",
                        array(
                            translatable(entry.block, ACCENT),
                            text("%06X".format(0xFFFFFF and config.color), ACCENT))))
            is BlockConfig.Reset ->
                listOf(
                    translatable(
                        "sentinel.history.type.configure.reset",
                        array(translatable(entry.block, ACCENT))))
            else -> emptyList()
        }

    private fun getDisplayName(author: HistoryActor): Component {
        return if (author is HistoryActor.Player) {
            text(
                Vars.netServer.admins.getInfoOptional(author.muuid.uuid)?.plainLastName()
                    ?: "Unknown",
                ACCENT)
        } else {
            components(ACCENT, translatable(author.team), space(), translatable(author.unit))
        }
    }

    private fun getDisplayOrientation(rotation: Int): Component =
        when (rotation % 4) {
            0 -> translatable("sentinel.orientation.right", ACCENT)
            1 -> translatable("sentinel.orientation.top", ACCENT)
            2 -> translatable("sentinel.orientation.left", ACCENT)
            3 -> translatable("sentinel.orientation.bottom", ACCENT)
            else -> error("This should never happen")
        }
}
