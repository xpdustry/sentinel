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

import arc.math.geom.Point2
import com.xpdustry.distributor.api.annotation.EventHandler
import com.xpdustry.distributor.api.plugin.PluginListener
import com.xpdustry.distributor.api.util.Priority
import com.xpdustry.watchdog.api.history.HistoryAuthor
import com.xpdustry.watchdog.api.history.HistoryConfig
import com.xpdustry.watchdog.api.history.HistoryEntry
import com.xpdustry.watchdog.api.history.HistoryExplorer
import com.xpdustry.watchdog.factory.CANVAS_CONFIGURATION_FACTORY
import com.xpdustry.watchdog.factory.CommonConfigurationFactory
import com.xpdustry.watchdog.factory.ITEM_BRIDGE_CONFIGURATION_FACTORY
import com.xpdustry.watchdog.factory.LIGHT_CONFIGURATION_FACTORY
import com.xpdustry.watchdog.factory.LogicProcessorConfigurationFactory
import com.xpdustry.watchdog.factory.MASS_DRIVER_CONFIGURATION_FACTORY
import com.xpdustry.watchdog.factory.MESSAGE_BLOCK_CONFIGURATION_FACTORY
import com.xpdustry.watchdog.factory.PAYLOAD_DRIVER_CONFIGURATION_FACTORY
import com.xpdustry.watchdog.factory.POWER_NODE_CONFIGURATION_FACTORY
import com.xpdustry.watchdog.factory.UNIT_FACTORY_CONFIGURATION_FACTORY
import com.xpdustry.watchdog.util.LimitedList
import mindustry.core.GameState.State
import mindustry.game.EventType
import mindustry.game.EventType.StateChangeEvent
import mindustry.gen.Building
import mindustry.gen.Unit
import mindustry.world.Block
import mindustry.world.blocks.ConstructBlock
import mindustry.world.blocks.distribution.ItemBridge
import mindustry.world.blocks.distribution.MassDriver
import mindustry.world.blocks.logic.CanvasBlock
import mindustry.world.blocks.logic.LogicBlock
import mindustry.world.blocks.logic.MessageBlock
import mindustry.world.blocks.payloads.PayloadMassDriver
import mindustry.world.blocks.power.LightBlock
import mindustry.world.blocks.power.PowerNode
import mindustry.world.blocks.units.UnitFactory
import kotlin.reflect.KClass
import kotlin.reflect.full.isSuperclassOf
import kotlin.reflect.full.superclasses

internal class LiveHistoryExplorer(private val config: WatchdogConfig.History) : HistoryExplorer, PluginListener {
    private val positions = mutableMapOf<Int, LimitedList<HistoryEntry>>()
    private val players = mutableMapOf<String, LimitedList<HistoryEntry>>()
    private val factories = mutableMapOf<KClass<out Building>, HistoryConfig.Factory<*>>()

    override fun onPluginInit() {
        setConfigurationFactory<CanvasBlock.CanvasBuild>(CANVAS_CONFIGURATION_FACTORY)
        setConfigurationFactory<Building>(CommonConfigurationFactory)
        setConfigurationFactory<ItemBridge.ItemBridgeBuild>(ITEM_BRIDGE_CONFIGURATION_FACTORY)
        setConfigurationFactory<LightBlock.LightBuild>(LIGHT_CONFIGURATION_FACTORY)
        setConfigurationFactory<LogicBlock.LogicBuild>(LogicProcessorConfigurationFactory)
        setConfigurationFactory<MassDriver.MassDriverBuild>(MASS_DRIVER_CONFIGURATION_FACTORY)
        setConfigurationFactory<MessageBlock.MessageBuild>(MESSAGE_BLOCK_CONFIGURATION_FACTORY)
        setConfigurationFactory<PayloadMassDriver.PayloadDriverBuild>(PAYLOAD_DRIVER_CONFIGURATION_FACTORY)
        setConfigurationFactory<PowerNode.PowerNodeBuild>(POWER_NODE_CONFIGURATION_FACTORY)
        setConfigurationFactory<UnitFactory.UnitFactoryBuild>(UNIT_FACTORY_CONFIGURATION_FACTORY)
    }

    private inline fun <reified B : Building> setConfigurationFactory(factory: HistoryConfig.Factory<B>) {
        factories[B::class] = factory
    }

    override fun getHistory(
        x: Int,
        y: Int,
    ): List<HistoryEntry> {
        return positions[Point2.pack(x, y)] ?: emptyList()
    }

    override fun getHistory(uuid: String): List<HistoryEntry> {
        return players[uuid] ?: emptyList()
    }

    @EventHandler(priority = Priority.HIGH)
    fun onBlockBuildEndEvent(event: EventType.BlockBuildEndEvent) {
        if (event.unit == null || event.tile.build == null) {
            return
        }

        val block: Block =
            if (event.breaking) {
                (event.tile.build as ConstructBlock.ConstructBuild).current
            } else {
                event.tile.block()
            }
        this.addEntry(
            event.tile.build,
            block,
            event.unit.toAuthor(),
            if (event.breaking) HistoryEntry.Type.BREAK else HistoryEntry.Type.PLACE,
            event.config,
        )
    }

    @EventHandler(priority = Priority.HIGH)
    fun onBlockDestroyBeginEvent(event: EventType.BlockBuildBeginEvent) {
        if (event.unit == null) {
            return
        }
        val build = event.tile.build
        if (build is ConstructBlock.ConstructBuild) {
            this.addEntry(
                build,
                build.current,
                event.unit.toAuthor(),
                if (event.breaking) HistoryEntry.Type.BREAKING else HistoryEntry.Type.PLACING,
                build.lastConfig,
            )
        }
    }

    @EventHandler(priority = Priority.HIGH)
    fun onBLockConfigEvent(event: EventType.ConfigEvent) {
        if (event.player == null) {
            return
        }
        this.addEntry(
            event.tile,
            event.tile.block(),
            event.player.unit().toAuthor(),
            HistoryEntry.Type.CONFIGURE,
            event.value,
        )
    }

    @EventHandler(priority = Priority.HIGH)
    fun onMenuToPlayEvent(event: StateChangeEvent) {
        if (event.from == State.menu && event.to == State.playing) {
            positions.clear()
            players.clear()
        }
    }

    @EventHandler(priority = Priority.HIGH)
    fun onBlockRotateEvent(event: EventType.BuildRotateEvent) {
        if (event.unit == null || event.build.rotation == event.previous) {
            return
        }
        this.addEntry(
            event.build,
            event.build.block(),
            event.unit.toAuthor(),
            HistoryEntry.Type.ROTATE,
            event.build.config(),
        )
    }

    private fun <B : Building> getConfiguration(
        building: B,
        type: HistoryEntry.Type,
        config: Any?,
    ): HistoryConfig? {
        if (building.block().configurations.isEmpty) {
            return null
        }
        var clazz: KClass<*> = building::class
        while (Building::class.isSuperclassOf(clazz)) {
            @Suppress("UNCHECKED_CAST")
            val factory: HistoryConfig.Factory<B>? = factories[clazz] as HistoryConfig.Factory<B>?
            if (factory != null) {
                return factory.create(building, type, config)
            }
            clazz = clazz.superclasses.first()
        }
        return if (config == null) HistoryConfig.Unknown(null) else HistoryConfig.Unknown(config)
    }

    private fun addEntry(
        building: Building,
        block: Block,
        author: HistoryAuthor,
        type: HistoryEntry.Type,
        config: Any?,
    ) {
        val configuration = getConfiguration(building, type, config)
        building.tile.getLinkedTiles {
            addEntry(
                HistoryEntry(
                    it.x.toInt(),
                    it.y.toInt(),
                    building.tileX(),
                    building.tileY(),
                    author,
                    block,
                    type,
                    building.rotation,
                    configuration,
                    it.pos() != building.tile.pos(),
                ),
            )
        }
    }

    private fun addEntry(entry: HistoryEntry) {
        val entries =
            positions.computeIfAbsent(Point2.pack(entry.x, entry.y)) {
                LimitedList(config.tileEntriesLimit)
            }
        val previous: HistoryEntry? = entries.peekLast()
        // Some blocks have repeating configurations, we don't want to spam the history with them
        if (previous != null && haveSameConfiguration(previous, entry)) {
            entries.removeLast()
        }
        entries.add(entry)
        if (entry.author is HistoryAuthor.Player && !entry.virtual) {
            players
                .computeIfAbsent(entry.author.muuid.uuid) {
                    LimitedList(config.playerEntriesLimit)
                }
                .add(entry)
        }
    }

    private fun haveSameConfiguration(
        entryA: HistoryEntry,
        entryB: HistoryEntry,
    ): Boolean {
        return entryA.block == entryB.block &&
            entryA.configuration == entryB.configuration &&
            entryA.type === entryB.type
    }

    private fun Unit.toAuthor(): HistoryAuthor {
        return if (isPlayer) HistoryAuthor.Player(player) else HistoryAuthor.Unit(this)
    }
}
