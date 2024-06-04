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

import com.xpdustry.distributor.api.player.MUUID
import mindustry.game.Team
import mindustry.gen.Nulls
import mindustry.type.UnitType

public sealed interface HistoryAuthor {
    public val team: Team
    public val unit: UnitType

    public class Unit(unit: mindustry.gen.Unit) : HistoryAuthor {
        override val team: Team = unit.team()
        override val unit: UnitType = unit.type()
    }

    public class Player(player: mindustry.gen.Player) : HistoryAuthor {
        public val muuid: MUUID = MUUID.from(player)
        override val team: Team = player.team()
        override val unit: UnitType = player.unit().type()
    }

    public data object Server : HistoryAuthor {
        override val team: Team = Team.derelict
        override val unit: UnitType = Nulls.unit.type
    }
}
