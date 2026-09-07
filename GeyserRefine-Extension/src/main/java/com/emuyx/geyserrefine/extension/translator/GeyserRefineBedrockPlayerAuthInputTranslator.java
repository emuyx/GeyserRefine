/*
 * Copyright (c) 2024 GeyserMC. http://geysermc.org
 * ... (保留版权声明)
 */

package com.emuyx.geyserrefine.extension.translator;

import com.emuyx.geyserrefine.extension.freecam.FreecamHandler;
import com.emuyx.geyserrefine.extension.storage.ToastSettingsStorage;
import org.cloudburstmc.math.GenericMath;
import org.cloudburstmc.math.vector.Vector2f;
import org.cloudburstmc.math.vector.Vector3d;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.InputInteractionModel;
import org.cloudburstmc.protocol.bedrock.data.InputMode;
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.ItemUseTransaction;
import org.cloudburstmc.protocol.bedrock.packet.AnimatePacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlaySoundPacket;
import org.geysermc.geyser.entity.EntitySpectateHelper;
import org.geysermc.geyser.entity.type.BoatEntity;
import org.geysermc.geyser.entity.type.Entity;
import org.geysermc.geyser.entity.type.living.animal.horse.AbstractHorseEntity;
import org.geysermc.geyser.entity.type.living.animal.horse.LlamaEntity;
import org.geysermc.geyser.entity.type.living.animal.nautilus.AbstractNautilusEntity;
import org.geysermc.geyser.entity.type.player.PlayerEntity;
import org.geysermc.geyser.entity.type.player.SessionPlayerEntity;
import org.geysermc.geyser.entity.vehicle.ClientVehicle;
import org.geysermc.geyser.entity.vehicle.HorseVehicleComponent;
import org.geysermc.geyser.level.physics.BoundingBox;
import org.geysermc.geyser.network.GameProtocol;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.translator.protocol.PacketTranslator;
import org.geysermc.geyser.translator.protocol.Translator;
import org.geysermc.geyser.util.CooldownUtils;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.Pose;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.GameMode;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PlayerState;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundClientTickEndPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundMoveVehiclePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundPlayerAbilitiesPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundPlayerCommandPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundSpectatorActionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundSwingPacket;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;

@Translator(packet = PlayerAuthInputPacket.class)
public final class GeyserRefineBedrockPlayerAuthInputTranslator extends PacketTranslator<PlayerAuthInputPacket> {

    private static ToastSettingsStorage storage;

    public static void setStorage(ToastSettingsStorage s) {
        storage = s;
    }

    private static final Method BEDROCK_MOVE_PLAYER_TRANSLATE;

    static {
        try {
            Class<?> clazz = Class.forName("org.geysermc.geyser.translator.protocol.bedrock.entity.player.input.BedrockMovePlayer");
            BEDROCK_MOVE_PLAYER_TRANSLATE = clazz.getDeclaredMethod("translate", GeyserSession.class, PlayerAuthInputPacket.class);
            BEDROCK_MOVE_PLAYER_TRANSLATE.setAccessible(true);
        } catch (Exception e) {
            throw new RuntimeException("Failed to access BedrockMovePlayer.translate", e);
        }
    }

    @Override
    public void translate(GeyserSession session, PlayerAuthInputPacket packet) {
        SessionPlayerEntity entity = session.getPlayerEntity();

        // ====== 灵魂出窍 (Freecam) 模式 ======
        // 使用假 MovePlayerPacket 方案：每 tick 发送假位置到 Bedrock 客户端，
        // 让客户端在摄像机位置渲染玩家，摄像机自然跟随。
        // 这里 return 跳过下方所有逻辑，确保移动包不发送到 Java 服务器。
        if (FreecamHandler.isActive(session)) {
            session.setClientTicks(packet.getTick());

            // 物品交互（确保双击背包菜单仍然可用）
            if (packet.getInputData().contains(PlayerAuthInputData.PERFORM_ITEM_INTERACTION)) {
                processItemUseTransaction(session, packet.getItemUseTransaction());
            }
            if (packet.getInputData().contains(PlayerAuthInputData.PERFORM_ITEM_STACK_REQUEST)) {
                session.getPlayerInventoryHolder().translateRequests(List.of(packet.getItemStackRequest()));
            }

            // 读取输入 → 计算新摄像机位置 → 发送假 MovePlayerPacket 给客户端
            FreecamHandler.onPlayerAuthInput(session, packet);

            if (session.isSpawned()) {
                session.sendDownstreamGamePacket(ServerboundClientTickEndPacket.INSTANCE);
            }
            return; // ← 不调用 BedrockMovePlayer → Java 收不到玩家移动
        }
        // ====== 灵魂出窍模式结束 ======

        session.setClientTicks(packet.getTick());
        session.setInClientPredictedVehicle(packet.getInputData().contains(PlayerAuthInputData.IN_CLIENT_PREDICTED_IN_VEHICLE) && entity.getVehicle() != null && GameProtocol.is26_10orHigher(session.protocolVersion()));

        boolean wasJumping = session.getInputCache().wasJumping();
        session.getInputCache().processInputs(entity, packet);
        if (EntitySpectateHelper.isSpectating(session)) {
            if (session.isSpawned()) {
                session.sendDownstreamGamePacket(ServerboundClientTickEndPacket.INSTANCE);
            }
            return;
        }
        session.getBlockBreakHandler().handlePlayerAuthInputPacket(packet);

        ServerboundPlayerCommandPacket sprintPacket = null;
        boolean forceSprint = storage != null && storage.getForceSprint(session.xuid());

        Set<PlayerAuthInputData> inputData = packet.getInputData();
        Set<PlayerAuthInputData> leftOverInputData = new HashSet<>(packet.getInputData());
        for (PlayerAuthInputData input : inputData) {
            leftOverInputData.remove(input);
            switch (input) {
                case PERFORM_ITEM_INTERACTION -> processItemUseTransaction(session, packet.getItemUseTransaction());
                case PERFORM_ITEM_STACK_REQUEST -> session.getPlayerInventoryHolder().translateRequests(List.of(packet.getItemStackRequest()));
                case START_SWIMMING -> entity.setFlag(EntityFlag.SWIMMING, true);
                case STOP_SWIMMING -> entity.setFlag(EntityFlag.SWIMMING, false);
                case START_CRAWLING -> entity.setFlag(EntityFlag.CRAWLING, true);
                case STOP_CRAWLING -> entity.setFlag(EntityFlag.CRAWLING, false);
                case START_SPRINTING -> {
                    if (!leftOverInputData.contains(PlayerAuthInputData.STOP_SPRINTING)) {
                        if (!session.isSprinting()) {
                            sprintPacket = new ServerboundPlayerCommandPacket(entity.javaId(), PlayerState.START_SPRINTING);
                            session.setSprinting(true);
                        }
                    }
                }
                case STOP_SPRINTING -> {
                    if (!leftOverInputData.contains(PlayerAuthInputData.START_SPRINTING) && session.isSprinting()) {
                        sprintPacket = new ServerboundPlayerCommandPacket(entity.javaId(), PlayerState.STOP_SPRINTING);
                        session.setSprinting(false);
                    }
                }
                case START_FLYING -> {
                    if (session.isCanFly()) {
                        if (session.getGameMode() == GameMode.SPECTATOR) {
                            session.sendAdventureSettings();
                            break;
                        }
                        if (session.getPlayerEntity().getFlag(EntityFlag.SWIMMING) && session.getCollisionManager().isPlayerInWater()) {
                            session.sendAdventureSettings();
                            break;
                        }
                        session.setFlying(true);
                        session.sendDownstreamGamePacket(new ServerboundPlayerAbilitiesPacket(true));
                    } else {
                        session.setFlying(false);
                        session.sendAdventureSettings();
                    }
                }
                case STOP_FLYING -> {
                    session.setFlying(false);
                    session.sendDownstreamGamePacket(new ServerboundPlayerAbilitiesPacket(false));
                }
                case START_GLIDING -> {
                    if (!leftOverInputData.contains(PlayerAuthInputData.STOP_GLIDING)) {
                        if (entity.canStartGliding()) {
                            if (session.isFlying()) {
                                session.setFlying(false);
                                session.sendDownstreamGamePacket(new ServerboundPlayerAbilitiesPacket(false));
                            }
                            entity.setFlag(EntityFlag.GLIDING, true);
                            session.sendDownstreamGamePacket(new ServerboundPlayerCommandPacket(entity.getEntityId(), PlayerState.START_ELYTRA_FLYING));
                        } else {
                            entity.forceFlagUpdate();
                            entity.setFlag(EntityFlag.GLIDING, false);
                            if (session.isFlying()) {
                                session.sendAdventureSettings();
                            }
                        }
                    }
                }
                case START_SPIN_ATTACK -> entity.setFlag(EntityFlag.DAMAGE_NEARBY_MOBS, true);
                case STOP_SPIN_ATTACK -> entity.setFlag(EntityFlag.DAMAGE_NEARBY_MOBS, false);
                case STOP_GLIDING -> {
                    boolean shouldBeGliding = entity.isGliding() && entity.canStartGliding();
                    entity.forceFlagUpdate();
                    entity.setFlag(EntityFlag.GLIDING, shouldBeGliding);
                }
                case MISSED_SWING -> {
                    session.setLastAirHitTick(session.getTicks());

                    if (session.getGameMode() == GameMode.SPECTATOR) {
                        session.sendDownstreamGamePacket(new ServerboundSpectatorActionPacket(OptionalInt.empty()));
                    } else if (session.getArmAnimationTicks() != 0 && session.getArmAnimationTicks() != 1) {
                        session.sendDownstreamGamePacket(new ServerboundSwingPacket(Hand.MAIN_HAND));
                        session.activateArmAnimationTicking();
                    }

                    // ====== 自定义逻辑 ======
                    if (storage != null && !storage.getMuteNodamageSound(session.xuid())) {
                        PlaySoundPacket nodamageSound = new PlaySoundPacket();
                        nodamageSound.setSound("game.player.attack.nodamage");
                        Vector3f headPos = session.getPlayerEntity().getPosition().add(0, 1.62f, 0);
                        nodamageSound.setPosition(headPos);
                        nodamageSound.setVolume(1.5f);
                        nodamageSound.setPitch(1.0f);
                        session.sendUpstreamPacket(nodamageSound);
                    }

                    if (storage != null && storage.getTouchSwing(session.xuid()) && packet.getInputMode().equals(InputMode.TOUCH)) {
                        AnimatePacket animatePacket = new AnimatePacket();
                        animatePacket.setAction(AnimatePacket.Action.SWING_ARM);
                        animatePacket.setRuntimeEntityId(session.getPlayerEntity().geyserId());
                        session.sendUpstreamPacket(animatePacket);
                    }
                    // ====== 自定义逻辑结束 ======

                    CooldownUtils.setCooldownHitTime(session);
                }
            }
        }

        final Pose pose = entity.getDesiredPose();
        if (pose != session.getPose()) {
            session.setPose(pose);
            entity.setDimensionsFromPose(session.getPose());
            entity.updateBedrockMetadata();
        }

        processVehicleInput(session, packet, wasJumping);

        // 强制疾跑：前进时自动疾跑，停止前进或潜行时停止疾跑
        if (forceSprint) {
            boolean sneaking = inputData.contains(PlayerAuthInputData.SNEAKING);
            boolean shouldSprint = packet.getMotion().getY() > 0.1f && !sneaking;
            if (shouldSprint != session.isSprinting()) {
                sprintPacket = new ServerboundPlayerCommandPacket(
                        entity.javaId(),
                        shouldSprint ? PlayerState.START_SPRINTING : PlayerState.STOP_SPRINTING
                );
                session.setSprinting(shouldSprint);
            }
        }

        if (sprintPacket != null) {
            session.sendDownstreamGamePacket(sprintPacket);
        }

        // 反射调用 BedrockMovePlayer.translate
        try {
            BEDROCK_MOVE_PLAYER_TRANSLATE.invoke(null, session, packet);
        } catch (Exception e) {
            session.getGeyser().getLogger().error("Failed to invoke BedrockMovePlayer.translate", e);
        }

        if (session.isSpawned()) {
            session.sendDownstreamGamePacket(ServerboundClientTickEndPacket.INSTANCE);
        }

        if (entity.getVehicle() instanceof BoatEntity && session.isInClientPredictedVehicle()) {
            boolean up = inputData.contains(PlayerAuthInputData.UP);
            session.setSteeringLeft(up || inputData.contains(PlayerAuthInputData.PADDLE_RIGHT));
            session.setSteeringRight(up || inputData.contains(PlayerAuthInputData.PADDLE_LEFT));
        }
    }

    private static void processItemUseTransaction(GeyserSession session, ItemUseTransaction transaction) {
        if (transaction.getActionType() == 2) {
            session.setLastBlockPlaced(null);
            session.setLastBlockPlacePosition(null);
        } else {
            session.getGeyser().getLogger().error("Unhandled item use transaction type!");
            if (session.getGeyser().getLogger().isDebug()) {
                session.getGeyser().getLogger().debug(transaction);
            }
        }
    }

    private static void processVehicleInput(GeyserSession session, PlayerAuthInputPacket packet, boolean wasJumping) {
        Entity vehicle = session.getPlayerEntity().getVehicle();
        if (vehicle == null) {
            return;
        }

        boolean inClientPredictedVehicle = session.isInClientPredictedVehicle();
        if (vehicle instanceof ClientVehicle) {
            boolean isMobileAndClassicMovement = packet.getInputMode() == InputMode.TOUCH && packet.getInputInteractionModel() == InputInteractionModel.CLASSIC;
            if (isMobileAndClassicMovement && vehicle instanceof BoatEntity) {
                boolean left = packet.getInputData().contains(PlayerAuthInputData.PADDLE_LEFT);
                boolean right = packet.getInputData().contains(PlayerAuthInputData.PADDLE_RIGHT);
                if (left && right) {
                    session.getPlayerEntity().setVehicleInput(Vector2f.UNIT_Y);
                } else {
                    session.getPlayerEntity().setVehicleInput(Vector2f.UNIT_X.mul(left ? -1 : right ? 1 : 0));
                }
            } else {
                session.getPlayerEntity().setVehicleInput(packet.getMotion());
            }
        }

        boolean sendMovement = false;
        if (vehicle instanceof AbstractHorseEntity && !(vehicle instanceof LlamaEntity)) {
            sendMovement = inClientPredictedVehicle;
        } else if (vehicle instanceof BoatEntity) {
            sendMovement = inClientPredictedVehicle && (vehicle.getPassengers().size() == 1 || session.getPlayerEntity().isRidingInFront());
        }

        if ((vehicle instanceof AbstractHorseEntity || vehicle instanceof AbstractNautilusEntity) && !vehicle.getFlag(EntityFlag.HAS_DASH_COOLDOWN)) {
            int currentJumpingTicks = session.getInputCache().getJumpingTicks();
            if (currentJumpingTicks < 0) {
                session.getInputCache().setJumpingTicks(++currentJumpingTicks);
                if (currentJumpingTicks == 0) {
                    session.getInputCache().setJumpScale(0);
                }
            }

            boolean holdingJump = packet.getInputData().contains(PlayerAuthInputData.JUMPING);
            if (wasJumping && !holdingJump) {
                int finalVehicleJumpStrength = GenericMath.floor(session.getInputCache().getJumpScale() * 100f);
                session.sendDownstreamGamePacket(new ServerboundPlayerCommandPacket(session.getPlayerEntity().getEntityId(),
                        PlayerState.START_HORSE_JUMP, finalVehicleJumpStrength));
                session.getInputCache().setJumpingTicks(-10);
                session.getPlayerEntity().setVehicleJumpStrength(finalVehicleJumpStrength);

                if (vehicle instanceof AbstractHorseEntity horse && horse.getVehicleComponent() instanceof HorseVehicleComponent horseVehicleComponent) {
                    horseVehicleComponent.setAllowStandSliding(true);
                }
            } else if (!wasJumping && holdingJump) {
                session.getInputCache().setJumpingTicks(0);
                session.getInputCache().setJumpScale(0);
            } else if (holdingJump) {
                session.getInputCache().setJumpingTicks(++currentJumpingTicks);
                if (currentJumpingTicks < 10) {
                    session.getInputCache().setJumpScale(session.getInputCache().getJumpingTicks() * 0.1F);
                } else {
                    session.getInputCache().setJumpScale(0.8f + 2.0f / (currentJumpingTicks - 9) * 0.1f);
                }
            }
        } else {
            session.getInputCache().setJumpScale(0);
        }

        if (sendMovement) {
            Vector3f position = vehicle.position();
            final BoundingBox box = new BoundingBox(
                    position.up(vehicle.getBoundingBoxHeight() / 2f).toDouble(),
                    vehicle.getBoundingBoxWidth(), vehicle.getBoundingBoxHeight(), vehicle.getBoundingBoxWidth()
            );

            Vector3d movement = session.getPlayerEntity().getLastTickEndVelocity().toDouble();
            Vector3d correctedMovement = session.getCollisionManager().correctMovementForCollisions(movement, box, true, false);
            vehicle.setOnGround(correctedMovement.getY() != movement.getY() && session.getPlayerEntity().getLastTickEndVelocity().getY() < 0);

            Vector3f vehiclePosition = packet.getPosition().down(vehicle.getOffset());
            Vector2f vehicleRotation = packet.getVehicleRotation();
            if (vehicleRotation == null) {
                return;
            }

            if (session.getWorldBorder().isPassingIntoBorderBoundaries(vehiclePosition)) {
                vehicle.moveAbsoluteRaw(position, vehicle instanceof BoatEntity ? vehicle.getYaw() - 90 : vehicle.getYaw(), vehicle.getPitch(), vehicle.getHeadYaw(), vehicle.isOnGround(), true);

                final PlayerEntity playerEntity = session.getPlayerEntity();
                playerEntity.moveAbsoluteRaw(playerEntity.position(), playerEntity.getYaw(), playerEntity.getPitch(), playerEntity.getHeadYaw(), playerEntity.isOnGround(), playerEntity.getVehicle() == null);
                return;
            }

            vehicle.setPosition(vehiclePosition);
            ServerboundMoveVehiclePacket moveVehiclePacket = new ServerboundMoveVehiclePacket(
                    vehiclePosition.toDouble(),
                    vehicle instanceof BoatEntity ? vehicleRotation.getY() - 90 : vehicleRotation.getY(), vehiclePosition.getX(),
                    vehicle.isOnGround()
            );
            session.sendDownstreamGamePacket(moveVehiclePacket);
        }
    }
}