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

import com.xpdustry.distributor.api.command.CommandSender
import com.xpdustry.distributor.api.command.cloud.MindustryCommandManager
import com.xpdustry.distributor.api.command.cloud.parser.PlayerParser
import com.xpdustry.distributor.api.plugin.PluginListener
import mindustry.gen.Player
import org.incendo.cloud.Command
import org.incendo.cloud.component.DefaultValue
import org.incendo.cloud.description.Description
import org.incendo.cloud.meta.CommandMeta
import org.incendo.cloud.parser.standard.IntegerParser

internal class HistoryCommand(
    private val clientCommands: MindustryCommandManager<CommandSender>,
    private val serverCommands: MindustryCommandManager<CommandSender>,
    private val explorer: LiveHistoryReader,
    private val renderer: HistoryRenderer,
) : PluginListener {
    override fun onPluginLoad() {
        registerCommands(clientCommands)
        registerCommands(serverCommands)
    }

    private fun registerCommands(manager: MindustryCommandManager<CommandSender>) {
        val root =
            Command.newBuilder<CommandSender>(
                "history", CommandMeta.empty(), createDescription(arrayOf("history")))

        manager.command(
            root
                .literal("player")
                .commandDescription(createDescription(arrayOf("history", "player")))
                .required(
                    "player",
                    PlayerParser.playerParser(),
                    createDescription(arrayOf("history", "player"), "player"),
                )
                .optional(
                    "limit",
                    IntegerParser.integerParser(1, 50),
                    DefaultValue.constant(10),
                    createDescription(arrayOf("history", "player"), "limit"),
                )
                .handler { ctx ->
                    val player: Player = ctx["player"]
                    val entries = normalize(explorer.getHistory(player.uuid()), ctx["limit"])
                    if (entries.none()) {
                        ctx.sender().error("No history found.")
                        return@handler
                    }
                    ctx.sender().reply(renderer.render(entries, HistoryActor.Player(player)))
                })

        manager.command(
            root
                .literal("tile")
                .commandDescription(createDescription(arrayOf("history", "tile")))
                .required(
                    "x",
                    IntegerParser.integerParser(1, Short.MAX_VALUE.toInt()),
                    createDescription(arrayOf("history", "tile"), "x"),
                )
                .required(
                    "y",
                    IntegerParser.integerParser(1, Short.MAX_VALUE.toInt()),
                    createDescription(arrayOf("history", "tile"), "y"),
                )
                .optional(
                    "limit",
                    IntegerParser.integerParser(1, 50),
                    DefaultValue.constant(10),
                    createDescription(arrayOf("history", "tile"), "limit"),
                )
                .handler { ctx ->
                    val x: Int = ctx["x"]
                    val y: Int = ctx["y"]
                    val entries = normalize(explorer.getHistory(x, y), ctx["limit"])
                    if (entries.none()) {
                        ctx.sender().error("No history found.")
                        return@handler
                    }
                    ctx.sender().reply(renderer.render(entries, x, y))
                })
    }

    private fun createDescription(path: Array<String>, argument: String? = null) =
        Description.of(
            "sentinel.command.[${path.joinToString(".")}]${if (argument != null) ".$argument" else ""}")

    private fun normalize(entries: List<HistoryEntry>, limit: Int) =
        entries
            .asReversed()
            .asSequence()
            .withIndex()
            .filter {
                it.index == 0 ||
                    (it.value.type != HistoryEntry.Type.BREAKING &&
                        it.value.type != HistoryEntry.Type.PLACING)
            }
            .map { it.value }
            .take(limit)
            .toList()
}
