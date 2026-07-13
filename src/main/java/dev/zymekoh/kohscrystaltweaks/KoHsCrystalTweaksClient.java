package dev.zymekoh.kohscrystaltweaks;

import dev.zymekoh.kohscrystaltweaks.config.KoHsCrystalTweaksConfig;
import dev.zymekoh.kohscrystaltweaks.core.CrystalPredictor;
import dev.zymekoh.kohscrystaltweaks.network.OptOutPacket;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.Items;
import net.minecraft.util.ActionResult;

public final class KoHsCrystalTweaksClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        KoHsCrystalTweaksConfig config = KoHsCrystalTweaksConfig.get();
        CrystalPredictor.setEnabled(config.clientSideCrystalsEnabled);

        PayloadTypeRegistry.playC2S().register(OptOutPacket.ID, OptOutPacket.CODEC);

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> CrystalPredictor.reset());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> CrystalPredictor.reset());

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (!(world instanceof ClientWorld)) {
                return ActionResult.PASS;
            }
            if (!CrystalPredictor.isEnabled()) {
                return ActionResult.PASS;
            }
            if (!player.getStackInHand(hand).isOf(Items.END_CRYSTAL)) {
                return ActionResult.PASS;
            }
            CrystalPredictor.onUseBlock(hitResult);
            return ActionResult.PASS;
        });

        ClientEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (CrystalPredictor.isEnabled()) {
                CrystalPredictor.onEntityLoaded(entity);
            }
        });
        ClientEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
            if (CrystalPredictor.isEnabled()) {
                CrystalPredictor.onEntityUnloaded(entity);
            }
        });

        ClientTickEvents.START_CLIENT_TICK.register(client -> CrystalPredictor.clientTick());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> CrystalPredictor.clearAll());
    }
}
