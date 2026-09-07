package com.emuyx.geyserrefine.paper.cape;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.component.builtin.item.ItemProfile;
import com.github.retrooper.packetevents.protocol.entity.EntityPositionData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.item.type.ItemTypes;
import com.github.retrooper.packetevents.protocol.player.Equipment;
import com.github.retrooper.packetevents.protocol.player.EquipmentSlot;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientAttack;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.server.*;
import com.emuyx.geyserrefine.paper.GeyserRefinePlugin;
import com.emuyx.geyserrefine.paper.FloodgateUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.util.*;

public class MannequinCapeWorkaround extends PacketListenerAbstract {
    private final Map<Integer, UUID> players = new HashMap<>();
    private final Map<Integer, Integer> mannequinToPlayer = new HashMap<>();
    private final List<Integer> mannequins = new ArrayList<>();
    private final List<Integer> singlePlayerMounts = new ArrayList<>();
    private int nextMannequinId = 1_000_000;

    private static final List<com.github.retrooper.packetevents.protocol.entity.type.EntityType> SINGLE_PLAYER_MOUNTS =
            List.of(
                    EntityTypes.MINECART,
                    EntityTypes.PIG,
                    EntityTypes.HORSE,
                    EntityTypes.ABSTRACT_HORSE,
                    EntityTypes.CHESTED_HORSE,
                    EntityTypes.ZOMBIE_HORSE,
                    EntityTypes.SKELETON_HORSE,
                    EntityTypes.DONKEY,
                    EntityTypes.MULE,
                    EntityTypes.HOGLIN,
                    EntityTypes.STRIDER
            );

    public MannequinCapeWorkaround() {
        PacketEvents.getAPI().getEventManager().registerListener(this);
    }

    public void disable() {
        PacketEvents.getAPI().getEventManager().unregisterListener(this);
    }

    public void refreshCapeForPlayer(UUID playerUUID) {
        if (playerUUID == null) return;

        Integer mannequinId = null;
        for (Map.Entry<Integer, Integer> entry : mannequinToPlayer.entrySet()) {
            UUID uuid = players.get(entry.getValue());
            if (uuid != null && uuid.equals(playerUUID)) {
                mannequinId = entry.getKey();
                break;
            }
        }
        if (mannequinId == null) return;

        Player bukkitPlayer = Bukkit.getPlayer(playerUUID);
        if (bukkitPlayer == null) return;
        UUID capeUUID = CapeLoader.getCapeUUID(bukkitPlayer);
        if (capeUUID == null) return;
        ItemProfile profile = CapeLoader.getAsItemProfile(capeUUID);
        if (profile == null) return;

        List<EntityData<?>> metadata = new ArrayList<>();
        metadata.add(new EntityData<>(17, EntityDataTypes.RESOLVABLE_PROFILE, profile));
        WrapperPlayServerEntityMetadata metaPacket = new WrapperPlayServerEntityMetadata(mannequinId, metadata);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getUniqueId().equals(playerUUID)) continue;
            // 只发送给 Java 玩家
            if (FloodgateUtil.isBedrockPlayer(p)) continue;
            User user = PacketEvents.getAPI().getPlayerManager().getUser(p);
            if (user != null) {
                user.sendPacketSilently(metaPacket);
            }
        }
    }

    private void sendToJavaPlayersExcept(UUID excludeUUID, Object packet) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getUniqueId().equals(excludeUUID)) continue;
            // 只发送给 Java 玩家
            if (FloodgateUtil.isBedrockPlayer(p)) continue;
            User targetUser = PacketEvents.getAPI().getPlayerManager().getUser(p);
            if (targetUser != null) {
                targetUser.sendPacketSilently(packet);
            }
        }
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        User user = event.getUser();
        if (user == null) return;

        UUID userUUID = user.getUUID();
        if (userUUID == null) return;

        Player receiver = Bukkit.getPlayer(userUUID);
        if (receiver == null) return;

        // 如果接收者是基岩版玩家，则不处理任何 mannequin 包
        if (FloodgateUtil.isBedrockPlayer(receiver)) return;

        if (event.getPacketType() == com.github.retrooper.packetevents.protocol.packettype.PacketType.Play.Server.SPAWN_ENTITY) {
            WrapperPlayServerSpawnEntity packet = new WrapperPlayServerSpawnEntity(event);
            if (packet.getEntityType() == EntityTypes.PLAYER) {
                UUID playerUUID = packet.getUUID().orElse(null);
                if (playerUUID != null) {
                    Player bukkitPlayer = Bukkit.getPlayer(playerUUID);
                    if (bukkitPlayer != null && FloodgateUtil.isBedrockPlayer(bukkitPlayer)) {
                        UUID capeUUID = CapeLoader.getCapeUUID(bukkitPlayer);
                        if (capeUUID != null && CapeLoader.exists(capeUUID)) {
                            int playerEntityId = packet.getEntityId();
                            players.put(playerEntityId, playerUUID);

                            int mannequinId = nextMannequinId++;
                            mannequinToPlayer.put(mannequinId, playerEntityId);
                            mannequins.add(mannequinId);

                            WrapperPlayServerSpawnEntity mannequin = new WrapperPlayServerSpawnEntity(
                                    mannequinId,
                                    Optional.of(UUID.randomUUID()),
                                    EntityTypes.MANNEQUIN,
                                    packet.getPosition(),
                                    packet.getPitch(),
                                    packet.getYaw(),
                                    packet.getHeadYaw(),
                                    packet.getData(),
                                    packet.getVelocity()
                            );
                            // 只发送给 Java 玩家
                            sendToJavaPlayersExcept(playerUUID, mannequin);

                            ItemProfile profile = CapeLoader.getAsItemProfile(capeUUID);
                            if (profile != null) {
                                List<EntityData<?>> metadata = new ArrayList<>();
                                metadata.add(new EntityData<>(17, EntityDataTypes.RESOLVABLE_PROFILE, profile));
                                WrapperPlayServerEntityMetadata metaPacket = new WrapperPlayServerEntityMetadata(mannequinId, metadata);
                                sendToJavaPlayersExcept(playerUUID, metaPacket);
                            }
                        }
                    }
                }
            } else if (SINGLE_PLAYER_MOUNTS.contains(packet.getEntityType())) {
                singlePlayerMounts.add(packet.getEntityId());
            }
        } else if (event.getPacketType() == com.github.retrooper.packetevents.protocol.packettype.PacketType.Play.Server.DESTROY_ENTITIES) {
            WrapperPlayServerDestroyEntities wrapper = new WrapperPlayServerDestroyEntities(event);
            List<Integer> ents = new ArrayList<>();
            for (int id : wrapper.getEntityIds()) {
                ents.add(id);
                if (players.containsKey(id)) {
                    Integer mannequinId = null;
                    for (Map.Entry<Integer, Integer> entry : mannequinToPlayer.entrySet()) {
                        if (entry.getValue().equals(id)) {
                            mannequinId = entry.getKey();
                            break;
                        }
                    }
                    if (mannequinId != null) {
                        UUID playerUUID = players.get(id);
                        if (playerUUID != null) {
                            sendToJavaPlayersExcept(playerUUID, new WrapperPlayServerDestroyEntities(mannequinId));
                        }
                        mannequinToPlayer.remove(mannequinId);
                        mannequins.remove(mannequinId);
                    }
                    players.remove(id);
                } else if (singlePlayerMounts.contains(id)) {
                    singlePlayerMounts.remove(Integer.valueOf(id));
                }
            }
            wrapper.setEntityIds(ents.stream().mapToInt(Integer::intValue).toArray());
            event.markForReEncode(true);
        } else if (event.getPacketType() == com.github.retrooper.packetevents.protocol.packettype.PacketType.Play.Server.ENTITY_EQUIPMENT) {
            WrapperPlayServerEntityEquipment wrapper = new WrapperPlayServerEntityEquipment(event);
            Integer playerEntityId = null;
            for (Map.Entry<Integer, Integer> entry : mannequinToPlayer.entrySet()) {
                if (entry.getValue().equals(wrapper.getEntityId())) {
                    playerEntityId = wrapper.getEntityId();
                    break;
                }
            }
            if (playerEntityId != null) {
                Integer mannequinId = null;
                for (Map.Entry<Integer, Integer> entry : mannequinToPlayer.entrySet()) {
                    if (entry.getValue().equals(playerEntityId)) {
                        mannequinId = entry.getKey();
                        break;
                    }
                }
                if (mannequinId == null) return;

                UUID playerUUID = players.get(playerEntityId);
                if (playerUUID == null) return;

                for (Equipment eq : wrapper.getEquipment()) {
                    if (eq.getSlot() == EquipmentSlot.CHEST_PLATE) {
                        if (eq.getItem().getType() == ItemTypes.ELYTRA) {
                            ItemStack.Builder air = new ItemStack.Builder();
                            eq.setItem(air.type(ItemTypes.AIR).amount(0).build());
                            event.markForReEncode(true);
                            ItemStack.Builder elytra = new ItemStack.Builder();
                            WrapperPlayServerEntityEquipment mannequinEq = new WrapperPlayServerEntityEquipment(
                                    mannequinId,
                                    List.of(new Equipment(EquipmentSlot.CHEST_PLATE, elytra.type(ItemTypes.ELYTRA).amount(1).build()))
                            );
                            sendToJavaPlayersExcept(playerUUID, mannequinEq);
                        } else {
                            ItemStack.Builder air = new ItemStack.Builder();
                            WrapperPlayServerEntityEquipment mannequinEq = new WrapperPlayServerEntityEquipment(
                                    mannequinId,
                                    List.of(new Equipment(EquipmentSlot.CHEST_PLATE, air.type(ItemTypes.AIR).amount(0).build()))
                            );
                            sendToJavaPlayersExcept(playerUUID, mannequinEq);
                            event.markForReEncode(false);
                        }
                        break;
                    }
                }
            }
        } else if (event.getPacketType() == com.github.retrooper.packetevents.protocol.packettype.PacketType.Play.Server.ENTITY_METADATA) {
            WrapperPlayServerEntityMetadata meta = new WrapperPlayServerEntityMetadata(event);
            if (players.containsKey(meta.getEntityId())) {
                UUID playerUUID = players.get(meta.getEntityId());
                if (playerUUID == null) return;
                Player bukkitPlayer = Bukkit.getPlayer(playerUUID);
                if (bukkitPlayer == null) return;
                UUID capeUUID = CapeLoader.getCapeUUID(bukkitPlayer);
                if (capeUUID == null) return;
                ItemProfile profile = CapeLoader.getAsItemProfile(capeUUID);
                if (profile == null) return;

                Integer mannequinId = null;
                for (Map.Entry<Integer, Integer> entry : mannequinToPlayer.entrySet()) {
                    if (entry.getValue().equals(meta.getEntityId())) {
                        mannequinId = entry.getKey();
                        break;
                    }
                }
                if (mannequinId == null) return;

                List<EntityData<?>> metadata = new ArrayList<>();
                metadata.add(new EntityData<>(17, EntityDataTypes.RESOLVABLE_PROFILE, profile));
                WrapperPlayServerEntityMetadata mannequinMeta = new WrapperPlayServerEntityMetadata(mannequinId, metadata);
                sendToJavaPlayersExcept(playerUUID, mannequinMeta);
            }
        } else if (event.getPacketType() == com.github.retrooper.packetevents.protocol.packettype.PacketType.Play.Server.ENTITY_MOVEMENT ||
                event.getPacketType() == com.github.retrooper.packetevents.protocol.packettype.PacketType.Play.Server.ENTITY_POSITION_SYNC) {
            WrapperPlayServerEntityPositionSync wrapper = new WrapperPlayServerEntityPositionSync(event);
            if (players.containsKey(wrapper.getId())) {
                Integer mannequinId = null;
                for (Map.Entry<Integer, Integer> entry : mannequinToPlayer.entrySet()) {
                    if (entry.getValue().equals(wrapper.getId())) {
                        mannequinId = entry.getKey();
                        break;
                    }
                }
                if (mannequinId != null) {
                    UUID playerUUID = players.get(wrapper.getId());
                    if (playerUUID == null) return;
                    WrapperPlayServerEntityPositionSync mannequin = new WrapperPlayServerEntityPositionSync(
                            mannequinId,
                            new EntityPositionData(wrapper.getValues().getPosition(), wrapper.getValues().getDeltaMovement(), wrapper.getValues().getYaw(), wrapper.getValues().getPitch()),
                            wrapper.isOnGround()
                    );
                    sendToJavaPlayersExcept(playerUUID, mannequin);
                }
            }
        } else if (event.getPacketType() == com.github.retrooper.packetevents.protocol.packettype.PacketType.Play.Server.ENTITY_RELATIVE_MOVE) {
            WrapperPlayServerEntityRelativeMove wrapper = new WrapperPlayServerEntityRelativeMove(event);
            if (players.containsKey(wrapper.getEntityId())) {
                Integer mannequinId = null;
                for (Map.Entry<Integer, Integer> entry : mannequinToPlayer.entrySet()) {
                    if (entry.getValue().equals(wrapper.getEntityId())) {
                        mannequinId = entry.getKey();
                        break;
                    }
                }
                if (mannequinId != null) {
                    UUID playerUUID = players.get(wrapper.getEntityId());
                    if (playerUUID == null) return;
                    WrapperPlayServerEntityRelativeMove mannequin = new WrapperPlayServerEntityRelativeMove(
                            mannequinId,
                            wrapper.getDeltaX(), wrapper.getDeltaY(), wrapper.getDeltaZ(),
                            wrapper.isOnGround()
                    );
                    sendToJavaPlayersExcept(playerUUID, mannequin);
                }
            }
        } else if (event.getPacketType() == com.github.retrooper.packetevents.protocol.packettype.PacketType.Play.Server.ENTITY_RELATIVE_MOVE_AND_ROTATION) {
            WrapperPlayServerEntityRelativeMoveAndRotation wrapper = new WrapperPlayServerEntityRelativeMoveAndRotation(event);
            if (players.containsKey(wrapper.getEntityId())) {
                Integer mannequinId = null;
                for (Map.Entry<Integer, Integer> entry : mannequinToPlayer.entrySet()) {
                    if (entry.getValue().equals(wrapper.getEntityId())) {
                        mannequinId = entry.getKey();
                        break;
                    }
                }
                if (mannequinId != null) {
                    UUID playerUUID = players.get(wrapper.getEntityId());
                    if (playerUUID == null) return;
                    WrapperPlayServerEntityRelativeMoveAndRotation mannequin = new WrapperPlayServerEntityRelativeMoveAndRotation(
                            mannequinId,
                            wrapper.getDeltaX(), wrapper.getDeltaY(), wrapper.getDeltaZ(),
                            wrapper.getYaw(), wrapper.getPitch(),
                            wrapper.isOnGround()
                    );
                    sendToJavaPlayersExcept(playerUUID, mannequin);
                }
            }
        } else if (event.getPacketType() == com.github.retrooper.packetevents.protocol.packettype.PacketType.Play.Server.ENTITY_HEAD_LOOK) {
            WrapperPlayServerEntityHeadLook wrapper = new WrapperPlayServerEntityHeadLook(event);
            if (players.containsKey(wrapper.getEntityId())) {
                Integer mannequinId = null;
                for (Map.Entry<Integer, Integer> entry : mannequinToPlayer.entrySet()) {
                    if (entry.getValue().equals(wrapper.getEntityId())) {
                        mannequinId = entry.getKey();
                        break;
                    }
                }
                if (mannequinId != null) {
                    UUID playerUUID = players.get(wrapper.getEntityId());
                    if (playerUUID == null) return;
                    WrapperPlayServerEntityHeadLook mannequin = new WrapperPlayServerEntityHeadLook(
                            mannequinId,
                            wrapper.getHeadYaw()
                    );
                    sendToJavaPlayersExcept(playerUUID, mannequin);
                }
            }
        } else if (event.getPacketType() == com.github.retrooper.packetevents.protocol.packettype.PacketType.Play.Server.ENTITY_ROTATION) {
            WrapperPlayServerEntityRotation wrapper = new WrapperPlayServerEntityRotation(event);
            if (players.containsKey(wrapper.getEntityId())) {
                Integer mannequinId = null;
                for (Map.Entry<Integer, Integer> entry : mannequinToPlayer.entrySet()) {
                    if (entry.getValue().equals(wrapper.getEntityId())) {
                        mannequinId = entry.getKey();
                        break;
                    }
                }
                if (mannequinId != null) {
                    UUID playerUUID = players.get(wrapper.getEntityId());
                    if (playerUUID == null) return;
                    WrapperPlayServerEntityRotation mannequin = new WrapperPlayServerEntityRotation(
                            mannequinId,
                            wrapper.getYaw(), wrapper.getPitch(),
                            wrapper.isOnGround()
                    );
                    sendToJavaPlayersExcept(playerUUID, mannequin);
                }
            }
        } else if (event.getPacketType() == com.github.retrooper.packetevents.protocol.packettype.PacketType.Play.Server.ENTITY_VELOCITY) {
            WrapperPlayServerEntityVelocity wrapper = new WrapperPlayServerEntityVelocity(event);
            if (players.containsKey(wrapper.getEntityId())) {
                Integer mannequinId = null;
                for (Map.Entry<Integer, Integer> entry : mannequinToPlayer.entrySet()) {
                    if (entry.getValue().equals(wrapper.getEntityId())) {
                        mannequinId = entry.getKey();
                        break;
                    }
                }
                if (mannequinId != null) {
                    UUID playerUUID = players.get(wrapper.getEntityId());
                    if (playerUUID == null) return;
                    WrapperPlayServerEntityVelocity mannequin = new WrapperPlayServerEntityVelocity(
                            mannequinId,
                            wrapper.getVelocity()
                    );
                    sendToJavaPlayersExcept(playerUUID, mannequin);
                }
            }
        } else if (event.getPacketType() == com.github.retrooper.packetevents.protocol.packettype.PacketType.Play.Server.ENTITY_TELEPORT) {
            WrapperPlayServerEntityTeleport wrapper = new WrapperPlayServerEntityTeleport(event);
            if (players.containsKey(wrapper.getEntityId())) {
                Integer mannequinId = null;
                for (Map.Entry<Integer, Integer> entry : mannequinToPlayer.entrySet()) {
                    if (entry.getValue().equals(wrapper.getEntityId())) {
                        mannequinId = entry.getKey();
                        break;
                    }
                }
                if (mannequinId != null) {
                    UUID playerUUID = players.get(wrapper.getEntityId());
                    if (playerUUID == null) return;
                    WrapperPlayServerEntityTeleport mannequin = new WrapperPlayServerEntityTeleport(
                            mannequinId,
                            wrapper.getValues(),
                            wrapper.getRelativeFlags(),
                            wrapper.isOnGround()
                    );
                    sendToJavaPlayersExcept(playerUUID, mannequin);
                }
            }
        } else if (event.getPacketType() == com.github.retrooper.packetevents.protocol.packettype.PacketType.Play.Server.SET_PASSENGERS) {
            WrapperPlayServerSetPassengers wrapper = new WrapperPlayServerSetPassengers(event);
            if (singlePlayerMounts.contains(wrapper.getEntityId())) {
                List<Integer> ents = new ArrayList<>();
                for (int id : wrapper.getPassengers()) {
                    ents.add(id);
                    if (players.containsKey(id)) {
                        Integer mannequinId = null;
                        for (Map.Entry<Integer, Integer> entry : mannequinToPlayer.entrySet()) {
                            if (entry.getValue().equals(id)) {
                                mannequinId = entry.getKey();
                                break;
                            }
                        }
                        if (mannequinId != null) {
                            ents.add(mannequinId);
                        }
                    }
                }
                wrapper.setPassengers(ents.stream().mapToInt(Integer::intValue).toArray());
                event.markForReEncode(true);
            } else {
                for (int id : wrapper.getPassengers()) {
                    if (players.containsKey(id)) {
                        Integer mannequinId = null;
                        for (Map.Entry<Integer, Integer> entry : mannequinToPlayer.entrySet()) {
                            if (entry.getValue().equals(id)) {
                                mannequinId = entry.getKey();
                                break;
                            }
                        }
                        if (mannequinId != null) {
                            UUID playerUUID = players.get(id);
                            if (playerUUID != null) {
                                sendToJavaPlayersExcept(playerUUID, new WrapperPlayServerDestroyEntities(mannequinId));
                            }
                            mannequins.remove(mannequinId);
                            mannequinToPlayer.remove(mannequinId);
                            players.remove(id);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == com.github.retrooper.packetevents.protocol.packettype.PacketType.Play.Client.INTERACT_ENTITY) {
            WrapperPlayClientInteractEntity wrapper = new WrapperPlayClientInteractEntity(event);
            int originalId = wrapper.getEntityId();
            if (mannequinToPlayer.containsKey(originalId)) {
                int realId = mannequinToPlayer.get(originalId);
                wrapper.setEntityId(realId);
                try {
                    Field packetField = PacketReceiveEvent.class.getDeclaredField("packet");
                    packetField.setAccessible(true);
                    packetField.set(event, wrapper);
                } catch (Exception ignored) {}
            }
        } else if (event.getPacketType() == com.github.retrooper.packetevents.protocol.packettype.PacketType.Play.Client.ATTACK) {
            WrapperPlayClientAttack wrapper = new WrapperPlayClientAttack(event);
            int originalId = wrapper.getEntityId();
            if (mannequinToPlayer.containsKey(originalId)) {
                int realId = mannequinToPlayer.get(originalId);
                wrapper.setEntityId(realId);
                try {
                    Field packetField = PacketReceiveEvent.class.getDeclaredField("packet");
                    packetField.setAccessible(true);
                    packetField.set(event, wrapper);
                } catch (Exception ignored) {}
            }
        }
    }
}