package com.emuyx.geyserrefine.paper.packet;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.particle.Particle;
import com.github.retrooper.packetevents.protocol.particle.type.ParticleTypes;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerParticle;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSoundEffect;
import com.emuyx.geyserrefine.paper.FloodgateUtil;
import org.bukkit.Location;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class BedrockPacketListener extends PacketListenerAbstract {

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPlayer() == null) return;
        Player receiver = (Player) event.getPlayer();

        boolean isSweepParticle = false;
        boolean isDamageIndicator = false;
        boolean isSweepSound = false;
        Location particleLocation = null;

        if (event.getPacketType() == PacketType.Play.Server.PARTICLE) {
            WrapperPlayServerParticle wrapper = new WrapperPlayServerParticle(event);
            Particle<?> particle = wrapper.getParticle();
            if (particle != null) {
                if (particle.getType() == ParticleTypes.SWEEP_ATTACK) {
                    isSweepParticle = true;
                    particleLocation = new Location(
                            receiver.getWorld(),
                            wrapper.getPosition().getX(),
                            wrapper.getPosition().getY(),
                            wrapper.getPosition().getZ()
                    );
                } else if (particle.getType() == ParticleTypes.DAMAGE_INDICATOR) {
                    isDamageIndicator = true;
                    // 伤害指示粒子位于受伤实体位置，使用接收者位置匹配攻击者
                    particleLocation = receiver.getLocation();
                }
            }
        } else if (event.getPacketType() == PacketType.Play.Server.SOUND_EFFECT ||
                event.getPacketType() == PacketType.Play.Server.NAMED_SOUND_EFFECT) {
            WrapperPlayServerSoundEffect wrapper = new WrapperPlayServerSoundEffect(event);
            if (wrapper.getSound() != null && wrapper.getSound().getSoundId() != null) {
                String soundName = wrapper.getSound().getSoundId().toString();
                if (soundName.contains("entity.player.attack.sweep")) {
                    isSweepSound = true;
                }
            }
        }

        if (!isSweepParticle && !isDamageIndicator && !isSweepSound) return;

        // 获取攻击者
        Player attacker = null;
        if (isSweepParticle && particleLocation != null) {
            attacker = AttackBlocker.getInstance().getAttackerNearLocation(particleLocation, 5.0);
        } else if (isDamageIndicator && particleLocation != null) {
            attacker = AttackBlocker.getInstance().getAttackerNearLocation(particleLocation, 5.0);
        } else if (isSweepSound) {
            // 音效没有精确位置，使用接收者位置附近
            attacker = AttackBlocker.getInstance().getAttackerNearLocation(receiver.getLocation(), 5.0);
        }

        if (attacker == null) return;

        // 判断屏蔽条件：基岩版攻击者且无横扫附魔
        if (FloodgateUtil.isBedrockPlayer(attacker)) {
            ItemStack weapon = attacker.getInventory().getItemInMainHand();
            int sweepLevel = weapon.getEnchantmentLevel(Enchantment.SWEEPING_EDGE);
            if (sweepLevel == 0) {
                event.setCancelled(true);
            }
        }
    }
}