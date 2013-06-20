/*
 * This file is part of the Illarion Client.
 *
 * Copyright © 2012 - Illarion e.V.
 *
 * The Illarion Client is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The Illarion Client is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with the Illarion Client.  If not, see <http://www.gnu.org/licenses/>.
 */
package illarion.client.world;

import illarion.client.net.server.TileUpdate;
import illarion.client.resources.TileFactory;
import illarion.common.graphics.TileInfo;
import illarion.common.types.Location;
import org.apache.log4j.Logger;
import org.illarion.engine.Engine;
import org.illarion.engine.EngineException;
import org.illarion.engine.GameContainer;
import org.illarion.engine.graphic.WorldMap;
import org.illarion.engine.graphic.WorldMapDataProvider;
import org.illarion.engine.graphic.WorldMapDataProviderCallback;
import org.illarion.engine.nifty.IgeMiniMapRenderImage;
import org.illarion.engine.nifty.IgeRenderImage;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.NotThreadSafe;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * This class stores a reduced version of the full map the character knows. The map data is packed to a minimized and
 * fast readable size that can be stored on the hard disk.
 *
 * @author Martin Karing &lt;nitram@illarion.org&gt;
 */
@NotThreadSafe
public final class GameMiniMap implements WorldMapDataProvider {
    /**
     * The height of the world map in tiles.
     */
    public static final int WORLDMAP_HEIGHT = 1024;

    /**
     * The width of the world map in tiles.
     */
    public static final int WORLDMAP_WIDTH = 1024;

    /**
     * The bytes that are reserved for one tile in the internal storage.
     */
    private static final int BYTES_PER_TILE = 2;

    /**
     * The log file handler that takes care for the logging output of this class.
     */
    @Nonnull
    private static final Logger LOGGER = Logger.getLogger(GameMiniMap.class);

    /**
     * Indicated how many bits the blocked bit is shifted.
     */
    private static final int SHIFT_BLOCKED = 10;

    /**
     * Indicated how many bit the value of the overlay tile is shifted.
     */
    private static final int SHIFT_OVERLAY = 5;

    /**
     * The radius of the mini map.
     */
    private static final int MINI_RADIUS = 81;

    /**
     * This flag is {@code true} while not map is loaded.
     */
    private boolean noMapLoaded;

    /**
     * This variable stores if the map is currently loaded.
     */
    private boolean loadingMap;

    /**
     * The data storage for the map data that was loaded in this mini map.
     */
    @Nonnull
    @GuardedBy("mapData")
    private final ByteBuffer mapData;

    /**
     * The level of the current world map.
     */
    private int mapLevel;

    /**
     * The origin x coordinate of this world map.
     */
    private int mapOriginX;

    /**
     * The origin y coordinate of this world map.
     */
    private int mapOriginY;

    /**
     * The engine implementation of the world map.
     */
    @Nonnull
    private final WorldMap worldMap;

    /**
     * The image of the mini map as its rendered by the Nifty-GUI.
     */
    @Nonnull
    private final IgeRenderImage miniMapImage;

    /**
     * Constructor of the game map that sets up all instance variables.
     */
    public GameMiniMap(@Nonnull final Engine engine) throws EngineException {
        worldMap = engine.getAssets().createWorldMap(this);
        miniMapImage = new IgeMiniMapRenderImage(engine, worldMap, MINI_RADIUS);

        mapData = ByteBuffer.allocate(WorldMap.WORLD_MAP_WIDTH * WorldMap.WORLD_MAP_HEIGHT * BYTES_PER_TILE);
        mapData.order(ByteOrder.nativeOrder());
        noMapLoaded = true;
    }

    /**
     * Get the entire origin of the current world map. The origin is stored in a {@link Location} class instance that
     * is newly fetched from the buffer. In case its not used anymore it should be put back into the buffer. <p> The
     * server X and Y coordinate are the coordinates of the current origin of the world map. The Z coordinate is the
     * current level. </p> <p> For details on each coordinate see the functions that request the single coordinates.
     * </p>
     *
     * @return the location of the overview map origin
     * @see #getMapLevel()
     * @see #getMapOriginX()
     * @see #getMapOriginY()
     */
    @Nonnull
    public Location getMapOrigin() {
        return getMapOrigin(new Location());
    }

    /**
     * This function is similar to {@link #getMapOrigin()}. The only difference is that this function does not fetch a
     * new instance of the location class, it rather uses the instance set as argument and fills it with the data.
     *
     * @param loc the location instance that is filled with the origin information
     * @return the same instance of the {@link Location} class that was set as parameter
     * @throws NullPointerException in case the parameter {@code loc} is set to {@code null}
     * @see #getMapOrigin()
     */
    @Nonnull
    public Location getMapOrigin(@Nonnull final Location loc) {
        loc.setSC(mapOriginX, mapOriginY, mapLevel);

        return loc;
    }

    /**
     * Get the render image used to display the mini map.
     *
     * @return the render image used to display the mini map on the GUI
     */
    @Nonnull
    public IgeRenderImage getMiniMap() {
        return miniMapImage;
    }

    /**
     * Get the level of the mini/world-map that is currently displayed. This equals the server Z coordinate of the
     * current player location.
     *
     * @return the level of the maps
     */
    public int getMapLevel() {
        return mapLevel;
    }

    /**
     * Get the X coordinate of the origin of the current world map. This coordinate depends on the area the player is
     * currently at. <p> This coordinate is calculated the following way:<br /> {@code originX = floor(playerX / {@link
     * #WORLDMAP_WIDTH}) * {@link #WORLDMAP_WIDTH}} </p>
     *
     * @return the X coordinate of the map origin
     */
    public int getMapOriginX() {
        return mapOriginX;
    }

    /**
     * Get the Y coordinate of the origin of the current world map. This coordinate depends on the area the player is
     * currently at. <p> This coordinate is calculated the following way:<br /> {@code originY = floor(playerY / {@link
     * #WORLDMAP_HEIGHT}) * {@link #WORLDMAP_HEIGHT}} </p>
     *
     * @return the Y coordinate of the map origin
     */
    public int getMapOriginY() {
        return mapOriginY;
    }

    /**
     * Update the world map texture.
     */
    public void render(@Nonnull final GameContainer container) {
        worldMap.render(container);
    }

    /**
     * Encode a server location to the index in the map data buffer.
     *
     * @param x the x coordinate of the location on the map
     * @param y the y coordinate of the location on the map
     * @return the index of the location in the map data buffer
     */
    private int encodeLocation(final int x, final int y) {
        if ((y < mapOriginY) || (y >= (mapOriginY + WORLDMAP_HEIGHT))) {
            throw new IllegalArgumentException("y out of range");
        }
        if ((x < mapOriginX) || (x >= (mapOriginX + WORLDMAP_WIDTH))) {
            throw new IllegalArgumentException("x out of range");
        }
        return ((y - mapOriginY) * WORLDMAP_WIDTH * BYTES_PER_TILE) + ((x - mapOriginX) * BYTES_PER_TILE);
    }

    /**
     * The mask to fetch the ID of the tile.
     */
    private static final int MASK_TILE_ID = 0x1F;

    /**
     * The mask to fetch the ID of the overlay.
     */
    private static final int MASK_OVERLAY_ID = 0x3E0;

    /**
     * The mask to fetch the blocked flag.
     */
    private static final int MASK_BLOCKED = 0x400;

    @Override
    public void requestTile(@Nonnull final Location location, @Nonnull final WorldMapDataProviderCallback callback) {
        if (location.getScZ() != mapLevel) {
            callback.setTile(WorldMap.NO_TILE, WorldMap.NO_TILE, false);
            return;
        }
        final int tileData = mapData.getShort(encodeLocation(location));

        if (tileData == 0) {
            callback.setTile(WorldMap.NO_TILE, WorldMap.NO_TILE, false);
        } else {
            final int tileId = tileData & MASK_TILE_ID;
            final int overlayId = tileData & MASK_OVERLAY_ID;
            final boolean blocked = (tileData & MASK_BLOCKED) > 0;

            final int tileMapColor;
            final int overlayMapColor;
            if (TileFactory.getInstance().hasTemplate(tileId)) {
                tileMapColor = TileFactory.getInstance().getTemplate(tileId).getTileInfo().getMapColor();
                if (TileFactory.getInstance().hasTemplate(overlayId)) {
                    overlayMapColor = TileFactory.getInstance().getTemplate(overlayId).getTileInfo().getMapColor();
                } else {
                    overlayMapColor = WorldMap.NO_TILE;
                }
            } else {
                tileMapColor = WorldMap.NO_TILE;
                overlayMapColor = WorldMap.NO_TILE;
            }

            callback.setTile(tileMapColor, overlayMapColor, blocked);
        }
    }

    /**
     * Set the location of the player. This tells the world map handler what map it needs to draw.
     *
     * @param playerLoc the location of the player
     */
    public void setPlayerLocation(@Nonnull final Location playerLoc) {
        final int newMapLevel = playerLoc.getScZ();

        final int newMapOriginX;
        if (playerLoc.getScX() >= 0) {
            newMapOriginX = (playerLoc.getScX() / WORLDMAP_WIDTH) * WORLDMAP_WIDTH;
        } else {
            newMapOriginX = ((playerLoc.getScX() / WORLDMAP_WIDTH) * WORLDMAP_WIDTH) - WORLDMAP_WIDTH;
        }

        final int newMapOriginY;
        if (playerLoc.getScY() >= 0) {
            newMapOriginY = (playerLoc.getScY() / WORLDMAP_HEIGHT) * WORLDMAP_HEIGHT;
        } else {
            newMapOriginY = ((playerLoc.getScY() / WORLDMAP_HEIGHT) * WORLDMAP_HEIGHT) - WORLDMAP_HEIGHT;
        }

        if (noMapLoaded || (newMapLevel != mapLevel) || (newMapOriginX != mapOriginX) || (newMapOriginY != mapOriginY)
                ) {
            saveMap();

            mapLevel = newMapLevel;
            mapOriginX = newMapOriginX;
            mapOriginY = newMapOriginY;

            worldMap.setMapOrigin(new Location(mapOriginX, mapOriginY, mapLevel));

            loadMap();
        }
        worldMap.setPlayerLocation(playerLoc);
    }

    /**
     * Save the current map to its file.
     */
    @SuppressWarnings("nls")
    public void saveMap() {
        while (loadingMap) {
            try {
                Thread.sleep(1);
            } catch (@Nonnull final InterruptedException e) {
                // nothing
            }
        }
        if (noMapLoaded) {
            return;
        }
        final File mapFile = getCurrentMapFilename();
        if (mapFile.exists() && !mapFile.canWrite()) {
            LOGGER.error("mapfile File locked, can't write the" + " name table.");
            return;
        }
        WritableByteChannel outChannel = null;
        try {
            final FileOutputStream outStream = new FileOutputStream(mapFile);
            final GZIPOutputStream gOutStream = new GZIPOutputStream(outStream);
            outChannel = Channels.newChannel(gOutStream);
            synchronized (mapData) {
                mapData.rewind();
                int toWrite = mapData.remaining();
                while (toWrite > 0) {
                    toWrite -= outChannel.write(mapData);
                }
            }
        } catch (@Nonnull final FileNotFoundException e) {
            LOGGER.error("Target file not found", e);
        } catch (@Nonnull final IOException e) {
            LOGGER.error("Error while writing minimap file", e);
        } finally {
            if (outChannel != null) {
                try {
                    outChannel.close();
                } catch (@Nonnull final IOException e) {
                    LOGGER.error("Failed closing the file stream.");
                }
            }
        }
    }

    /**
     * Get the full path string to the file for the currently selected map. This file needs to be used to store and
     * load
     * the map data.
     *
     * @return the path and the filename of the map file
     */
    @SuppressWarnings("nls")
    @Nonnull
    private File getCurrentMapFilename() {
        final StringBuilder builder = new StringBuilder();
        builder.setLength(0);
        builder.append("map");
        builder.append(mapOriginX / WORLDMAP_WIDTH);
        builder.append(mapOriginY / WORLDMAP_HEIGHT);
        builder.append(mapLevel);
        builder.append(".dat");
        return new File(World.getPlayer().getPath(), builder.toString());
    }

    /**
     * Load a map from its file. Any other formerly loaded map is discarded when this happens.
     */
    @SuppressWarnings("nls")
    public void loadMap() {
        loadingMap = true;
        final File mapFile = getCurrentMapFilename();

        if (!mapFile.exists()) {
            loadEmptyMap();
            return;
        }

        InputStream inStream = null;
        try {
            inStream = new GZIPInputStream(new FileInputStream(mapFile));
            final ReadableByteChannel inChannel = Channels.newChannel(inStream);

            synchronized (mapData) {
                mapData.rewind();

                int read = 1;
                while (read > 0) {
                    read = inChannel.read(mapData);
                }
                inChannel.close();
            }

            performFullUpdate();
        } catch (@Nonnull final IOException e) {
            LOGGER.error("Failed loading the map data from its file.", e);
            loadEmptyMap();
        } finally {
            if (inStream != null) {
                try {
                    inStream.close();
                } catch (@Nonnull final IOException e) {
                    LOGGER.error("Failed closing the file stream.");
                }
            }
        }
        noMapLoaded = false;
        loadingMap = false;
    }

    /**
     * Once this function is called the mini map will be rendered completely again.
     */
    public void performFullUpdate() {
        worldMap.setMapChanged();
    }

    /**
     * Load a empty map in case it was not possible to load it from a file. This creates a full new map and ensures
     * that there are no remaining from the last loaded map in.
     */
    private void loadEmptyMap() {
        loadingMap = true;
        synchronized (mapData) {
            mapData.rewind();
            while (mapData.remaining() > 0) {
                mapData.put((byte) 0);
            }
        }
        noMapLoaded = false;
        loadingMap = false;
        worldMap.clear();
    }

    /**
     * Update one tile of the overview map.
     *
     * @param updateData the data that is needed for the update
     */
    public void update(@Nonnull final TileUpdate updateData) {
        final Location tileLoc = updateData.getLocation();

        if ((tileLoc.getScX() < mapOriginX) || (tileLoc.getScX() >= (mapOriginX + WORLDMAP_WIDTH)) || (tileLoc.getScY
                () < mapOriginY) || (tileLoc.getScY() >= (mapOriginY + WORLDMAP_HEIGHT)) || (tileLoc.getScZ() !=
                mapLevel)) {
            return;
        }

        if (saveTile(tileLoc, updateData.getTileId(), updateData.isBlocked())) {
            worldMap.setTileChanged(tileLoc);
        }
    }

    /**
     * Save the information about a tile within the map data. This will overwrite any existing data about a tile.
     *
     * @param loc     the location of the tile
     * @param tileID  the ID of tile that is located at the position
     * @param blocked true in case this tile is not passable
     * @return {@code true} in case the new ID of the tile and the already set ID are not equal and the ID did change
     *         this way
     */
    @SuppressWarnings("BooleanMethodNameMustStartWithQuestion")
    private boolean saveTile(@Nonnull final Location loc, final int tileID, final boolean blocked) {
        final int index = encodeLocation(loc);

        if (tileID == MapTile.ID_NONE) {
            synchronized (mapData) {
                if (mapData.getShort(index) == 0) {
                    return false;
                }
                mapData.putShort(index, (short) 0);
            }
            return true;
        }

        short encodedTileValue = (short) TileInfo.getBaseID(tileID);
        encodedTileValue += TileInfo.getOverlayID(tileID) << SHIFT_OVERLAY;
        if (blocked) {
            encodedTileValue += 1 << SHIFT_BLOCKED;
        }


        synchronized (mapData) {
            if (mapData.getShort(index) == encodedTileValue) {
                return false;
            }
            mapData.putShort(index, encodedTileValue);
        }
        return true;
    }

    /**
     * Encode a server location to the index in the map data buffer.
     *
     * @param loc the server location that shall be encoded
     * @return the index of the location in the map data buffer
     */
    private int encodeLocation(@Nonnull final Location loc) {
        return encodeLocation(loc.getScX(), loc.getScY());
    }
}
