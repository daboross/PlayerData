/*
 * Copyright (C) 2013-2014 Dabo Ross <http://www.daboross.net/>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.daboross.bukkitdev.playerdata.helpers.comparators;

import java.util.Comparator;
import net.daboross.bukkitdev.playerdata.api.PlayerData;

/**
 * @author daboross
 */
public class PlayerDataFirstJoinComparator implements Comparator<PlayerData> {

    @Override
    public int compare(PlayerData o1, PlayerData o2) {
        return Long.compare(o1.getAllLogins().get(0).getDate(), o2.getAllLogins().get(0).getDate());
    }
}
