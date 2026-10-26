package com.zymekoh.crystaltweaks.client.benchmark;

import com.zymekoh.crystaltweaks.mixin.client.ServerboundInteractPacketAccessor;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;

/** 1.21.11's attack is an interact packet whose action is the attack; see the 26.x copy. */
final class AttackHook {
    private AttackHook() {
    }

    static void sent(Packet<?> packet) {
        if (packet instanceof ServerboundInteractPacket interact && isAttack(interact)) {
            CrystalBenchmark.onAttackSent(((ServerboundInteractPacketAccessor) interact).crystalTweaks$getEntityId());
        }
    }

    private static boolean isAttack(ServerboundInteractPacket packet) {
        boolean[] attack = {false};
        packet.dispatch(new ServerboundInteractPacket.Handler() {
            @Override
            public void onInteraction(InteractionHand hand) {
            }

            @Override
            public void onInteraction(InteractionHand hand, Vec3 location) {
            }

            @Override
            public void onAttack() {
                attack[0] = true;
            }
        });
        return attack[0];
    }
}
