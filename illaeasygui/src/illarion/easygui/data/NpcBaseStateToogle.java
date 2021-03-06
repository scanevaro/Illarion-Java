/*
 * This file is part of the Illarion easyGUI Editor.
 *
 * Copyright © 2011 - Illarion e.V.
 *
 * The Illarion easyGUI Editor is free software: you can redistribute i and/or
 * modify it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * The Illarion easyGUI Editor is distributed in the hope that it will be
 * useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * the Illarion easyGUI Editor. If not, see <http://www.gnu.org/licenses/>.
 */
package illarion.easygui.data;

/**
 * This enumerator contains the values used to change the base npc state.
 * 
 * @author Martin Karing
 * @since 1.00
 */
public enum NpcBaseStateToogle {
    /**
     * Set the NPC into the busy talking modus. While this modus is activated
     * the NPC will not talk to anyone else.
     */
    begin,

    /**
     * Set the NPC back into the normal modus.
     */
    end;
}
