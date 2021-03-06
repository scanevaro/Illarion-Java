/*
 * This file is part of the Illarion Game Engine.
 *
 * Copyright © 2013 - Illarion e.V.
 *
 * The Illarion Game Engine is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The Illarion Game Engine is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with the Illarion Game Engine.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.illarion.engine.nifty;

import de.lessvoid.nifty.spi.render.RenderImage;
import org.illarion.engine.graphic.Color;
import org.illarion.engine.graphic.Graphics;

import javax.annotation.Nonnull;

/**
 * This is the general interface for a render image for the Nifty-GUI implementation on this engine.
 *
 * @author Martin Karing &lt;nitram@illarion.org&gt;
 */
public interface IgeRenderImage extends RenderImage {

    void renderImage(@Nonnull Graphics g, int x, int y, int width, int height, @Nonnull Color color, float imageScale);

    void renderImage(@Nonnull Graphics g, int x, int y, int w, int h, int srcX, int srcY, int srcW, int srcH,
                     @Nonnull Color color, float scale, int centerX, int centerY);
}
