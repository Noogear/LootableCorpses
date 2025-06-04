package com.devConnor.lootableCorpses.instances;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.*;
import com.devConnor.lootableCorpses.LootableCorpses;
import com.devConnor.lootableCorpses.managers.PacketManager;
import lombok.Getter;
import net.minecraft.world.entity.EntityPose;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.data.type.Snow;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.devConnor.lootableCorpses.utils.VersionUtils.isVersionAtLeast;

public class CorpseEntity {
    
    private final LootableCorpses lootableCorpses;

    @Getter
    private final int id;

    @Getter
    private final UUID player;

    @Getter
    private final Location location;

    @Getter
    private final CorpseInventory corpseInventory;

    @Getter
    private final CorpseGui corpseGui;

    @Getter
    private final Long timestamp;

    private WrappedGameProfile corpse;

    private final ArrayList<PacketContainer> packets;
    private final EnumWrappers.ItemSlot[] armorSlots;

    public CorpseEntity(LootableCorpses lootableCorpses, UUID player, Location deathLocation, PlayerInventory inventory) {
        this.lootableCorpses = lootableCorpses;

        this.id = (int) (Math.random() * Integer.MAX_VALUE);
        this.player = player;
        this.location = deathLocation;
        this.corpseInventory = new CorpseInventory(inventory);
        this.corpseGui = new CorpseGui(this.id, player, this.corpseInventory);
        this.packets = new ArrayList<>();

        this.packets.add(createCorpse());
        this.packets.add(spawnCorpse());
        this.packets.add(getMetadataPacket());
        this.packets.add(getRotationPacket());

        this.armorSlots = new EnumWrappers.ItemSlot[]{
                EnumWrappers.ItemSlot.FEET,
                EnumWrappers.ItemSlot.LEGS,
                EnumWrappers.ItemSlot.CHEST,
                EnumWrappers.ItemSlot.HEAD
        };
        this.timestamp = System.currentTimeMillis();

        sendPackets();
    }

    private PacketContainer createCorpse() {
        Player corpsePlayer =  Bukkit.getPlayer(player);
        if (corpsePlayer == null) {
            return null;
        }

        WrappedGameProfile corpseProfile = WrappedGameProfile.fromPlayer(corpsePlayer);
        WrappedSignedProperty textures = corpseProfile.getProperties().get("textures").stream().findFirst().orElse(null);

        this.corpse = new WrappedGameProfile(UUID.randomUUID(), corpsePlayer.getName());
        if (textures != null) {
            corpse.getProperties().put("textures", new WrappedSignedProperty("textures", textures.getValue(), textures.getSignature()));
        }

        PlayerInfoData playerInfoData = new PlayerInfoData(
                corpse.getUUID(),
                0,
                false,
                EnumWrappers.NativeGameMode.SURVIVAL,
                corpse,
                null
        );

        PacketContainer playerInfoPacket = lootableCorpses.getProtocolManager().createPacket(PacketType.Play.Server.PLAYER_INFO);
        if (isVersionAtLeast("20.0")) {
            playerInfoPacket.getPlayerInfoActions().write(0, Collections.singleton(EnumWrappers.PlayerInfoAction.ADD_PLAYER));
        } else {
            playerInfoPacket.getPlayerInfoAction().write(0, EnumWrappers.PlayerInfoAction.ADD_PLAYER);
        }
        playerInfoPacket.getPlayerInfoDataLists().write(1, Collections.singletonList(playerInfoData));

        return playerInfoPacket;
    }

    private PacketContainer spawnCorpse() {
        boolean isVersionAbove20_2 = isVersionAtLeast("20.2");
        PacketContainer spawnEntityPacket = lootableCorpses.getProtocolManager().createPacket(
                isVersionAbove20_2 ? PacketType.Play.Server.SPAWN_ENTITY : PacketType.Play.Server.NAMED_ENTITY_SPAWN
        );
        spawnEntityPacket.getUUIDs().write(0, corpse.getUUID());
        spawnEntityPacket.getIntegers().write(0, this.id);

        Location deathLocation = location;
        spawnEntityPacket.getDoubles()
                .write(0, deathLocation.getX())
                .write(1, getHighestBlock())
                .write(2, deathLocation.getZ());
        spawnEntityPacket.getBytes()
                .write(0, (byte) ((deathLocation.getYaw() * 256.0F) / 360.0F))
                .write(1, (byte) -90);
        if (isVersionAbove20_2) {
            spawnEntityPacket.getEntityTypeModifier().write(0, EntityType.PLAYER);
        }

        return spawnEntityPacket;
    }

    private PacketContainer getMetadataPacket() {
        if (isVersionAtLeast("20.1")) {
            WrappedDataWatcher dataWatcher = new WrappedDataWatcher();
            dataWatcher.setObject(new WrappedDataWatcher.WrappedDataWatcherObject(6, WrappedDataWatcher.Registry.get(EntityPose.class)), EntityPose.b);
            dataWatcher.setObject(new WrappedDataWatcher.WrappedDataWatcherObject(17, WrappedDataWatcher.Registry.get(Byte.class)), (byte) 0x7F);

            // Prepare the metadata packet
            PacketContainer metadataPacket = lootableCorpses.getProtocolManager().createPacket(PacketType.Play.Server.ENTITY_METADATA);
            List<WrappedDataValue> wrappedDataValues = dataWatcher.getWatchableObjects().stream()
                    .map(watchableObject -> new WrappedDataValue(watchableObject.getIndex(), watchableObject.getWatcherObject().getSerializer(), watchableObject.getValue()))
                    .collect(Collectors.toList());

            // Write the entity ID and data
            metadataPacket.getIntegers().write(0, this.id);
            metadataPacket.getDataValueCollectionModifier().write(0, wrappedDataValues);

            return metadataPacket;
        } else {
            PacketContainer metadataPacket = lootableCorpses.getProtocolManager().createPacket(PacketType.Play.Server.ENTITY_METADATA);

            metadataPacket.getIntegers().write(0, this.id);
            List<WrappedDataValue> wrappedDataValues = new ArrayList<>();

            WrappedDataWatcher.WrappedDataWatcherObject poseObject = new WrappedDataWatcher.WrappedDataWatcherObject(
                    6, WrappedDataWatcher.Registry.get(EntityPose.class));
            WrappedDataValue poseValue = new WrappedDataValue(poseObject.getIndex(), poseObject.getSerializer(), EntityPose.b);
            wrappedDataValues.add(poseValue);

            WrappedDataWatcher.WrappedDataWatcherObject skinPartsObject = new WrappedDataWatcher.WrappedDataWatcherObject(
                    17, WrappedDataWatcher.Registry.get(Byte.class));
            WrappedDataValue skinPartsValue = new WrappedDataValue(skinPartsObject.getIndex(), skinPartsObject.getSerializer(), (byte) 0x7F);

            wrappedDataValues.add(skinPartsValue);

            // Attach data values to the metadata packet
            metadataPacket.getDataValueCollectionModifier().write(0, wrappedDataValues);

            return metadataPacket;
        }
    }

    private PacketContainer getRotationPacket() {
        PacketContainer rotationPacket = lootableCorpses.getProtocolManager().createPacket(PacketType.Play.Server.REL_ENTITY_MOVE_LOOK);
        rotationPacket.getIntegers().write(0, id);
        rotationPacket.getBytes()
                .write(0, (byte) ((location.getYaw() * 256.0F) / 360.0F))
                .write(1, (byte) -90);
        rotationPacket.getBooleans().write(0, true);

        return rotationPacket;
    }

    private PacketContainer getArmorPacket(EnumWrappers.ItemSlot slot, ItemStack item) {
        PacketContainer packet = lootableCorpses.getProtocolManager().createPacket(PacketType.Play.Server.ENTITY_EQUIPMENT);

        packet.getIntegers().write(0, this.id);
        Pair<EnumWrappers.ItemSlot, ItemStack> pair = new Pair<>(slot, item);
        List<Pair<EnumWrappers.ItemSlot, ItemStack>> equipmentList = List.of(pair);
        packet.getSlotStackPairLists().write(0, equipmentList);

        return packet;
    }

    private double getHighestBlock() {
        Location highestBlockLocation = location.getWorld().getHighestBlockAt(location).getLocation();

        if (highestBlockLocation.getBlockY() < location.getBlockY()) {
            return highestBlockLocation.getBlockY() + 0.9 + getOffset(highestBlockLocation);
        }

        return location.getBlockY() - 0.1 + getOffset(location);
    }

    private double getOffset(Location loc) {
        Block block = loc.getWorld().getBlockAt(loc);
        Block blockAbove = loc.getWorld().getBlockAt(loc.add(0, 1, 0));

        if (block.getBlockData() instanceof Slab) {
            return 0.5;
        } else if (blockAbove.getType() == Material.SNOW) {
            Snow snowBlock = (Snow) blockAbove.getBlockData();
            return (0.125 * snowBlock.getLayers()) - 0.1;
        }

        return 0;
    }

    private void sendPackets() {
        for (Player p : lootableCorpses.getPlayers()) {
            sendPacketToPlayer(p);
        }
    }

    private void sendArmorPacket(Player p) {
        for (int i = 0; i < armorSlots.length; i++) {
            lootableCorpses.getProtocolManager().sendServerPacket(p, getArmorPacket(armorSlots[i], corpseInventory.getArmor().getArmor()[i]));
        }
    }

    public void sendPacketToPlayer(Player p) {
        try {
            for (PacketContainer packet : packets) {
                PacketManager.sendPacketToPlayer(p, packet);
            }
            sendArmorPacket(p);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void removeArmor(int slot) {
        corpseInventory.getArmor().removeArmor(slot);
        for (Player p : lootableCorpses.getPlayers()) {
            sendArmorPacket(p);
        }
    }

    public void dropRemainingInventory() {
        if (location.getWorld() == null) return;

        ItemStack[] inventory = corpseGui.getGui().getStorageContents();
        for (ItemStack itemStack : inventory) {
            if (itemStack != null) {
                location.getWorld().dropItemNaturally(location, itemStack);
            }
        }
    }
}
