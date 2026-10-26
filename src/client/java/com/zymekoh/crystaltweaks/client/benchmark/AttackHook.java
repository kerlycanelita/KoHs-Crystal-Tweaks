package com.zymekoh.crystaltweaks.client.benchmark;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;

/**
 * Times an attack the moment its packet leaves, for the benchmark. Minecraft 1.21.11 sends attacks
 * as an interact packet instead, so it has its own copy of this class under {@code src/legacy}.
 */
final class AttackHook {
    private AttackHook() {
    }

    static void sent(Packet<?> packet) {
        if (packet instanceof ServerboundAttackPacket attack) {
            CrystalBenchmark.onAttackSent(attack.entityId());
        }
    }
}
