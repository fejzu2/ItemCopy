package pl.fejzu.itemcopier.client.serialization;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.ItemStack;
import pl.fejzu.itemcopier.client.util.ItemCopierLog;

public final class PacketSafetyCheck {
    public static final int MIN_SLOT = 1;
    public static final int MAX_SLOT = 45;

    private static final int MAX_PAYLOAD_BYTES = 1024 * 1024;

    private PacketSafetyCheck() {
    }

    public record Result(boolean safe, String reason, int encodedBytes) {
        public static Result ok(int bytes) {
            return new Result(true, null, bytes);
        }

        public static Result fail(String reason) {
            return new Result(false, reason, -1);
        }
    }

    public static Result validate(
            ItemStack stack,
            int slot,
            RegistryAccess registries,
            FeatureFlagSet enabledFeatures
    ) {
        if (stack == null || stack.isEmpty()) {
            return Result.fail("item could not be read on this version");
        }
        if (slot < MIN_SLOT || slot > MAX_SLOT) {
            return Result.fail("slot " + slot + " is outside the range the server accepts");
        }
        if (stack.getCount() > stack.getMaxStackSize()) {
            return Result.fail("stack of " + stack.getCount() + " exceeds the max stack size of "
                    + stack.getMaxStackSize() + " - the server would drop it");
        }
        if (enabledFeatures != null && !stack.isItemEnabled(enabledFeatures)) {
            return Result.fail("this item is not enabled by the server feature flags");
        }

        RegistryFriendlyByteBuf buf = null;
        try {
            buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
            ServerboundSetCreativeModeSlotPacket packet =
                    new ServerboundSetCreativeModeSlotPacket(slot, stack);
            ServerboundSetCreativeModeSlotPacket.STREAM_CODEC.encode(buf, packet);
            int bytes = buf.writerIndex();
            if (bytes > MAX_PAYLOAD_BYTES) {
                return Result.fail("serialized item is " + bytes
                        + " bytes, over the " + MAX_PAYLOAD_BYTES + " byte safety limit");
            }
            return Result.ok(bytes);
        } catch (Throwable t) {
            ItemCopierLog.debug("Creative slot packet encode check failed: {}", t.toString());
            String message = t.getMessage();
            return Result.fail("item cannot be encoded for the network: "
                    + (message != null ? message : t.getClass().getSimpleName()));
        } finally {
            if (buf != null) {
                buf.release();
            }
        }
    }
}
