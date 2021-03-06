/*
 * Copyright (C) 2016 - 2017 Aurum
 *
 * Mystery is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Mystery is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.aurum.mystery2.game;

import com.aurum.mystery2.BitConverter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import com.aurum.mystery2.ByteBuffer;
import com.aurum.mystery2.ByteOrder;
import org.ini4j.Ini;
import org.ini4j.Profile.Section;

public class RomFile {
    // Static fields
    public static RomFile current;
    
    // Information about the game
    private String romId, romName, romDescription;
    private boolean isLoaded, isJapanese;
    
    // IO stuff
    private File file;
    private ByteBuffer buffer;
    
    // Parsed data
    public List<Pokemon> pokemon;
    public List<ExclusivePokemon> exclusivePokemon;
    public List<Item> items;
    public List<Move> moves;
    public List<Area> areas;
    public List<Dungeon> dungeons;
    public List<DungeonLayout> dungeonLayouts;
    public List<DungeonPokemon> dungeonPokemon;
    public List<DungeonItems> dungeonItems;
    public List<DungeonTraps> dungeonTraps;
    public int[] starters, partners;
    public long[] moneyfactors;
    
    // Data offsets
    private int dungeonPointerOffset, pokemonPointerOffset, itemPointerOffset;
    private int dungeonMainOffset, dungeonFloorsPointerOffset, dungeonMapOffset;
    private int pokemonStartersOffset, pokemonPartnersOffset, movesOffset, moneyOffset;
    private int areasTextOffset, areasMainOffset, exclusivePokemonOffset;
    
    private int dungeonDataFloorsOffset, dungeonDataLayoutsOffset, dungeonDataItemsOffset, dungeonDataPokemonOffset, dungeonDataTrapsOffset;
    private int pokemonDataOffset, itemDataOffset, dungeonFloorsOffset;
    
    public RomFile(File f) {
        file = f;
    }
    
    public RomFile(String f) {
        this(new File(f));
    }
    
    @Override
    public String toString() {
        return romName + " (" + romId + ')';
    }
    
    public void setFile(File f) {
        file = f;
    }
    
    public File getFile() {
        return file;
    }
    
    public String getRomId() {
        return romId;
    }
    
    public String getRomName() {
        return romName;
    }
    
    public String getRomDescription() {
        return romDescription;
    }
    
    public boolean isLoaded() {
        return isLoaded;
    }
    
    public boolean isJapanese() {
        return isJapanese;
    }
    
    public void load() throws IOException {
        // load data into buffer
        buffer = new ByteBuffer((int) file.length(), ByteOrder.LITTLE_ENDIAN);
        
        try (FileInputStream in = new FileInputStream(file)) {
            in.read(buffer.getBuffer(), 0, in.available());
        }
        
        // find ROM ID and name
        buffer.seek(0xA0);
        romName = buffer.readString(0xC);
        romId = buffer.readString(0x6);
        
        // get offsets and additional information from INI file
        File ini = new File("B24_offsets.ini");
        
        if (ini.isFile() && ini.exists()) {
            Section s = new Ini(ini).get(romId);
            
            romDescription = s.get("description");
            dungeonPointerOffset = BitConverter.stringToInt(s.get("dungeonPointerOffset"));
            pokemonPointerOffset = BitConverter.stringToInt(s.get("pokemonPointerOffset"));
            itemPointerOffset = BitConverter.stringToInt(s.get("itemPointerOffset"));
            dungeonMainOffset = BitConverter.stringToInt(s.get("dungeonMainOffset"));
            dungeonFloorsPointerOffset = BitConverter.stringToInt(s.get("dungeonFloorsPointerOffset"));
            dungeonMapOffset = BitConverter.stringToInt(s.get("dungeonMapOffset"));
            pokemonStartersOffset = BitConverter.stringToInt(s.get("pokemonStartersOffset"));
            pokemonPartnersOffset = BitConverter.stringToInt(s.get("pokemonPartnersOffset"));
            movesOffset = BitConverter.stringToInt(s.get("movesOffset"));
            moneyOffset = BitConverter.stringToInt(s.get("moneyOffset"));
            areasTextOffset = BitConverter.stringToInt(s.get("areasTextOffset"));
            areasMainOffset = BitConverter.stringToInt(s.get("areasMainOffset"));
            exclusivePokemonOffset = BitConverter.stringToInt(s.get("exclusivePokemonOffset"));
            isJapanese = Boolean.parseBoolean(s.get("isJapanese"));
            isLoaded = true;
        }
        else {
            romDescription = "Unknown game";
            isJapanese = false;
            isLoaded = false;
        }
    }
    
    public void save() throws IOException {
        // major check for potential null pointers
        if (!isLoaded)
            return;
        
        // storage of data
        storeDungeons();
        storeStarters();
        storePokemon();
        storeItems();
        storeMoves();
        storeAreas();
        storeExclusivePokemon();
        storeMoneyFactors();
        
        // create file if it does not exist
        if (!(file.exists() && file.isFile())) {
            file.getParentFile().mkdirs();
            file.createNewFile();
        }
        
        // finally, write bytes to file
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(buffer.getBuffer());
            out.flush();
        }
    }
    
    public void loadStarters() {
        starters = new int[27];
        partners = new int[11];
        
        buffer.seek(pokemonStartersOffset);
        for (int i = 0 ; i < 27 ; i++)
            starters[i] = buffer.readUnsignedShort();
        
        buffer.seek(pokemonPartnersOffset);
        for (int i = 0 ; i < 11 ; i++)
            partners[i] = buffer.readUnsignedShort();
    }
    
    public void storeStarters() {
        if (starters == null || partners == null)
            return;
        
        buffer.seek(pokemonStartersOffset);
        for (int starter : starters)
            buffer.writeUnsignedShort(starter);
        
        buffer.seek(pokemonPartnersOffset);
        for (int partner : partners)
            buffer.writeUnsignedShort(partner);
    }
    
    public void loadPokemon() {
        pokemon = new ArrayList();
        
        buffer.seek(pokemonPointerOffset);
        buffer.skip(0x4);
        buffer.seek(pokemonDataOffset = buffer.readPointerAsOffset());
        
        for (int i = 0 ; i < 424; i++)
            pokemon.add(Pokemon.unpack(buffer));
    }
    
    public void storePokemon() {
        if (pokemon == null)
            return;
        
        buffer.seek(pokemonDataOffset);
        
        for (int i = 0 ; i < 424; i++)
            buffer.writeBytes(Pokemon.pack(pokemon.get(i)));
    }
    
    public void loadItems() {
        items = new ArrayList();
        
        buffer.seek(itemPointerOffset);
        buffer.skip(0x4);
        buffer.seek(itemDataOffset = buffer.readPointerAsOffset());
        
        for (int i = 0 ; i < 240 ; i++)
            items.add(Item.unpack(buffer));
    }
    
    public void storeItems() {
        if (items == null)
            return;
        
        buffer.seek(itemDataOffset);
        
        for (int i = 0 ; i < 240 ; i++)
            buffer.writeBytes(Item.pack(items.get(i)));
    }
    
    public void loadMoves() {
        moves = new ArrayList();
        
        buffer.seek(movesOffset);
        for (int i = 0 ; i < 413 ; i++)
            moves.add(Move.unpack(buffer));
    }
    
    public void storeMoves() {
        if (moves == null)
            return;
        
        buffer.seek(movesOffset);
        for (int i = 0 ; i < 413 ; i++)
            buffer.writeBytes(Move.pack(moves.get(i)));
    }
    
    public void loadAreas() {
        areas = new ArrayList();
        
        for (int i = 0 ; i < 58 ; i++) {
            // General data
            buffer.seek(areasMainOffset + i * Area.SIZE);
            Area area = Area.unpack(buffer);
            
            // Text data
            if (areasTextOffset != 0x00000000) {
                area.namePointer = buffer.readIntAt(areasTextOffset + i * 0x4);
                area.name = buffer.readStringAt(BitConverter.pointerToOffset(area.namePointer));
            }
            
            areas.add(area);
        }
    }
    
    public void storeAreas() {
        if (areas == null)
            return;
        
        buffer.seek(areasMainOffset);
        for (int i = 0 ; i < 58 ; i++)
            buffer.writeBytes(Area.pack(areas.get(i)));
    }
    
    public void loadExclusivePokemon() {
        exclusivePokemon = new ArrayList();
        
        buffer.seek(exclusivePokemonOffset);
        for (int i = 0 ; i < 12 ; i++)
            exclusivePokemon.add(ExclusivePokemon.unpack(buffer));
    }
    
    public void storeExclusivePokemon() {
        if (exclusivePokemon == null)
            return;
        
        buffer.seek(exclusivePokemonOffset);
        for (int i = 0 ; i < 12 ; i++)
            buffer.writeBytes(ExclusivePokemon.pack(exclusivePokemon.get(i)));
    }
    
    public void loadMoneyFactors() {
        moneyfactors = new long[100];
        
        buffer.seek(moneyOffset);
        for (int i = 0 ; i < 100 ; i++)
            moneyfactors[i] = buffer.readUnsignedInt();
    }
    
    public void storeMoneyFactors() {
        if (moneyfactors == null)
            return;
        
        buffer.seek(moneyOffset);
        for (long moneyfactor : moneyfactors)
            buffer.writeUnsignedInt(moneyfactor);
    }
    
    public void loadDungeons() {
        dungeons = new ArrayList();
        dungeonLayouts = new ArrayList();
        dungeonPokemon = new ArrayList();
        dungeonItems = new ArrayList();
        dungeonTraps = new ArrayList();
        
        // Main pointer offsets
        buffer.seek(dungeonPointerOffset);
        buffer.skip(0x4);
        buffer.seek(buffer.readPointerAsOffset());
        dungeonDataFloorsOffset = buffer.readPointerAsOffset();
        dungeonDataLayoutsOffset = buffer.readPointerAsOffset();
        dungeonDataItemsOffset = buffer.readPointerAsOffset();
        dungeonDataPokemonOffset = buffer.readPointerAsOffset();
        dungeonDataTrapsOffset = buffer.readPointerAsOffset();
        
        // General dungeon entries
        buffer.seek(dungeonMainOffset);
        for (int i = 0 ; i < 98 ; i++) {
            dungeons.add(Dungeon.unpack(buffer));
        }
        
        // Dungeon floor entries and map coordinates
        buffer.seek(dungeonFloorsPointerOffset);
        dungeonFloorsOffset = buffer.readPointerAsOffset();
        
        for (int i = 0 ; i < 64 ; i++) {
            Dungeon dungeon = dungeons.get(i);
            
            // Find the map points
            buffer.seek(dungeonMapOffset + i * 0x4);
            dungeon.mapX = buffer.readUnsignedShort();
            dungeon.mapY = buffer.readUnsignedShort();
            
            // Find the number of floors first
            dungeon.floorsCount = buffer.readUnsignedByteAt(dungeonFloorsOffset + i);
            
            // Load the actual floors
            buffer.seek(dungeonDataFloorsOffset + i * 0x4);
            buffer.seek(dungeon.floorsOffset = buffer.readPointerAsOffset());
            for (int f = 0 ; f < dungeon.floorsCount ; f++)
                dungeon.floors.add(Floor.unpack(buffer));
        }
        
        // Dungeon layout entries
        buffer.seek(dungeonDataLayoutsOffset);
        for (int i = 0 ; i < 1764 ; i++) {
            dungeonLayouts.add(DungeonLayout.unpack(buffer));
        }
        
        // Dungeon pokemon entries
        for (int i = 0 ; i < 839 ; i++) {
            buffer.seek(dungeonDataPokemonOffset + i * 0x4);
            buffer.seek(buffer.readPointerAsOffset());
            dungeonPokemon.add(DungeonPokemon.unpack(buffer));
        }
        
        // Dungeon items entries
        for (int i = 0 ; i < 178 ; i++) {
            buffer.seek(dungeonDataItemsOffset + i * 0x4);
            buffer.seek(buffer.readPointerAsOffset());
            dungeonItems.add(DungeonItems.unpack(buffer));
        }
        
        // Dungeon traps entries
        for (int i = 0 ; i < 148 ; i++) {
            buffer.seek(dungeonDataTrapsOffset + i * 0x4);
            buffer.seek(buffer.readPointerAsOffset());
            dungeonTraps.add(DungeonTraps.unpack(buffer));
        }
    }
    
    public void storeDungeons() {
        if (dungeons == null)
            return;
        
        // General dungeon entries
        for (int i = 0 ; i < 98 ; i++) {
            Dungeon dungeon = dungeons.get(i);
            
            // Write map coordinates for actual dungeons only
            if (i < 64) {
                buffer.seek(dungeonMapOffset + i * 0x4);
                buffer.writeUnsignedShort(dungeon.mapX);
                buffer.writeUnsignedShort(dungeon.mapY);
            }
            
            // Write floor entries if available
            if (dungeon.floors.size() >= 0) {
                buffer.seek(dungeon.floorsOffset);
                for (Floor floor : dungeon.floors)
                    buffer.writeBytes(Floor.pack(floor));
            }
            
            buffer.writeBytesAt(dungeonMainOffset + i * Dungeon.SIZE, Dungeon.pack(dungeon));
        }
        
        // Dungeon layout entries
        buffer.seek(dungeonDataLayoutsOffset);
        for (int i = 0 ; i < 1764 ; i++) {
            DungeonLayout layout = dungeonLayouts.get(i);
            buffer.writeBytes(DungeonLayout.pack(layout));
        }
        
        // Dungeon pokemon entries
        for (int i = 0 ; i < 839 ; i++) {
            DungeonPokemon dunmons = dungeonPokemon.get(i);
            buffer.writeBytesAt(dunmons.offset, DungeonPokemon.pack(dunmons));
        }
        
        // Dungeon items entries
        /*for (int i = 0 ; i < 178 ; i++) {
            DungeonItems dunitems = dungeonItems.get(i);
            buffer.writeBytesAt(dunitems.offset, DungeonItems.pack(dunitems));
        }*/
        
        // Dungeon traps entries
        for (int i = 0 ; i < 148 ; i++) {
            DungeonTraps duntraps = dungeonTraps.get(i);
            buffer.writeBytesAt(duntraps.offset, DungeonTraps.pack(duntraps));
        }
    }
}