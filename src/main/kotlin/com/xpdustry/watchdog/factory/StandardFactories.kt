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

import arc.math.geom.Point2
import com.xpdustry.watchdog.api.history.HistoryConfig
import com.xpdustry.watchdog.api.history.HistoryEntry
import mindustry.Vars
import mindustry.type.UnitType
import mindustry.world.blocks.distribution.ItemBridge
import mindustry.world.blocks.distribution.MassDriver
import mindustry.world.blocks.logic.CanvasBlock.CanvasBuild
import mindustry.world.blocks.logic.MessageBlock
import mindustry.world.blocks.payloads.PayloadMassDriver
import mindustry.world.blocks.power.LightBlock
import mindustry.world.blocks.power.PowerNode
import mindustry.world.blocks.units.UnitFactory
import java.awt.Color

internal val CANVAS_CONFIGURATION_FACTORY =
    HistoryConfig.Factory<CanvasBuild> { _, _, config ->
        if (config is ByteArray) HistoryConfig.Canvas(config) else null
    }

internal val LIGHT_CONFIGURATION_FACTORY =
    HistoryConfig.Factory<LightBlock.LightBuild> { _, _, config ->
        if (config is Int) HistoryConfig.Light(Color(config, true)) else null
    }

internal val MESSAGE_BLOCK_CONFIGURATION_FACTORY =
    HistoryConfig.Factory<MessageBlock.MessageBuild> { _, _, config ->
        if (config is String) HistoryConfig.Text(config, HistoryConfig.Text.Type.MESSAGE) else null
    }

internal val UNIT_FACTORY_CONFIGURATION_FACTORY =
    object : HistoryConfig.Factory<UnitFactory.UnitFactoryBuild> {
        override fun create(
            building: UnitFactory.UnitFactoryBuild,
            type: HistoryEntry.Type,
            config: Any?,
        ): HistoryConfig? {
            val plans = (building.block as UnitFactory).plans
            if (config is Int) {
                return if (config > 0 && config < plans.size) {
                    HistoryConfig.Content(plans[config].unit)
                } else {
                    HistoryConfig.Content(null)
                }
            } else if (config is UnitType) {
                return create(building, type, plans.indexOf { plan -> plan.unit == config })
            }
            return null
        }
    }

internal val ITEM_BRIDGE_CONFIGURATION_FACTORY =
    object : LinkableBlockConfigurationFactory<ItemBridge.ItemBridgeBuild>() {
        override fun isLinkValid(
            building: ItemBridge.ItemBridgeBuild,
            x: Int,
            y: Int,
        ): Boolean {
            return (building.block() as ItemBridge).linkValid(
                building.tile(),
                Vars.world.tile(x, y),
            )
        }
    }

internal val MASS_DRIVER_CONFIGURATION_FACTORY =
    object : LinkableBlockConfigurationFactory<MassDriver.MassDriverBuild>() {
        override fun isLinkValid(
            building: MassDriver.MassDriverBuild,
            x: Int,
            y: Int,
        ): Boolean {
            if (Point2.pack(x, y) == -1) {
                return false
            }
            val other = Vars.world.build(Point2.pack(x, y))
            return other is MassDriver.MassDriverBuild &&
                building.block === other.block &&
                building.team === other.team &&
                building.within(other, (building.block as MassDriver).range)
        }
    }

internal val POWER_NODE_CONFIGURATION_FACTORY =
    object : LinkableBlockConfigurationFactory<PowerNode.PowerNodeBuild>() {
        override fun isLinkValid(
            building: PowerNode.PowerNodeBuild,
            x: Int,
            y: Int,
        ): Boolean {
            return building.power().links.contains(Point2.pack(x, y))
        }
    }

internal val PAYLOAD_DRIVER_CONFIGURATION_FACTORY =
    object : LinkableBlockConfigurationFactory<PayloadMassDriver.PayloadDriverBuild>() {
        override fun isLinkValid(
            building: PayloadMassDriver.PayloadDriverBuild,
            x: Int,
            y: Int,
        ): Boolean {
            val other = Vars.world.build(Point2.pack(x, y))
            return other is MassDriver.MassDriverBuild &&
                building.block === other.block &&
                building.team === other.team &&
                building.within(other, (building.block as PayloadMassDriver).range)
        }
    }
