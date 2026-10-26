package com.zymekoh.crystaltweaks.mixin.client;

import com.zymekoh.crystaltweaks.client.practice.PracticePing;
import io.netty.channel.ChannelHandlerContext;
import java.util.concurrent.TimeUnit;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Crystal Practice played at a server's ping (see {@link PracticePing}): every packet the practice
 * world and its own server receive from each other is handled half the chosen round trip late, then
 * exactly as it would have been. Only the practice world's in-memory connection, in play; a real
 * server's is never touched.
 *
 * <p>The wait is on the receiving end, where Minecraft already waits for its game thread: what is
 * sent, and when the connection changes protocol, stay exactly Vanilla's. Packets keep their order:
 * none is handled before one received earlier, even as the ping changes.</p>
 */
@Mixin(Connection.class)
public abstract class PracticePingMixin {
    /** True on the thread handling a packet that has already waited. */
    @Unique
    private static final ThreadLocal<Boolean> crystalTweaks$waited = ThreadLocal.withInitial(() -> Boolean.FALSE);

    /** When the last held packet is handled, in System.nanoTime(); nothing may go before it. */
    @Unique
    private long crystalTweaks$releaseAt;

    @Shadow
    protected abstract void channelRead0(ChannelHandlerContext context, Packet<?> packet);

    @Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V",
            at = @At("HEAD"), cancellable = true)
    private void crystalTweaks$practicePing(ChannelHandlerContext context, Packet<?> packet, CallbackInfo callback) {
        if (crystalTweaks$waited.get()) {
            return;
        }
        long delay = PracticePing.oneWayNanos((Connection) (Object) this);
        long release;
        synchronized (this) {
            long now = System.nanoTime();
            if (delay <= 0L && this.crystalTweaks$releaseAt <= now) {
                return;
            }
            release = Math.max(now + delay, this.crystalTweaks$releaseAt);
            this.crystalTweaks$releaseAt = release;
        }
        callback.cancel();
        context.channel().eventLoop().schedule(() -> {
            crystalTweaks$waited.set(Boolean.TRUE);
            try {
                this.channelRead0(context, packet);
            } finally {
                crystalTweaks$waited.set(Boolean.FALSE);
            }
        }, Math.max(0L, release - System.nanoTime()), TimeUnit.NANOSECONDS);
    }
}
