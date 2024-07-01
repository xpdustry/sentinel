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

import com.xpdustry.distributor.api.player.MUUID
import mindustry.game.Team
import mindustry.gen.Nulls
import mindustry.type.UnitType

public sealed interface HistoryActor {
    public val team: Team
    public val unit: UnitType

    public class Unit(unit: mindustry.gen.Unit) : HistoryActor {
        override val team: Team = unit.team()
        override val unit: UnitType = unit.type()
    }

    public class Player(
        public val muuid: MUUID,
        override val team: Team,
        override val unit: UnitType
    ) : HistoryActor {
        public constructor(
            player: mindustry.gen.Player
        ) : this(MUUID.from(player), player.team(), player.unit().type())
    }

    public data object Server : HistoryActor {
        override val team: Team = Team.derelict
        override val unit: UnitType = Nulls.unit.type
    }
}
