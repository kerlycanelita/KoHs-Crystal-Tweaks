package com.zymekoh.crystaltweaks.core;

import com.zymekoh.crystaltweaks.mixin.client.ServerboundInteractPacketAccessor;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;

/** 1.21.11's attack is an interact packet whose action is the attack; 26.x has a packet of its own. */
final class LegacyAttacks {
    private LegacyAttacks() {
    }

    /** The entity {@code packet} attacks, or -1 when it is not an attack. */
    static int attacked(Packet<?> packet) {
        if (!(packet instanceof ServerboundInteractPacket interact)) {
            return -1;
        }
        boolean[] attack = {false};
        interact.dispatch(new ServerboundInteractPacket.Handler() {
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
        return attack[0] ? ((ServerboundInteractPacketAccessor) interact).crystalTweaks$getEntityId() : -1;
    }
}
