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

import com.xpdustry.sentinel.util.asMap
import mindustry.ctype.UnlockableContent
import mindustry.gen.Building

internal object GenericBlockConfigFactory : BlockConfigFactory<Building> {
    override fun create(
        building: Building,
        type: HistoryEntry.Type,
        config: Any?,
    ): BlockConfig? {
        if (isContentConfigurableBlockOnly(building)) {
            if (config == null) {
                return BlockConfig.Reset
            } else if (config is UnlockableContent) {
                return BlockConfig.Content(config)
            }
        } else if (isEnablingBlockOnly(building)) {
            if (config is Boolean) {
                return BlockConfig.Enable(config)
            }
        }
        return null
    }

    private fun isContentConfigurableBlockOnly(building: Building): Boolean {
        for (configuration in building.block().configurations.keys()) {
            if (!(UnlockableContent::class.java.isAssignableFrom(configuration) ||
                configuration == Void.TYPE)) {
                return false
            }
        }
        return true
    }

    private fun isEnablingBlockOnly(building: Building): Boolean {
        val keys = building.block().configurations.asMap().keys
        return keys.size == 1 && keys.contains(Boolean::class.java)
    }
}
