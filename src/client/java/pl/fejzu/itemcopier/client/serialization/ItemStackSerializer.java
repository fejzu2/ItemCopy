package pl.fejzu.itemcopier.client.serialization;

import com.mojang.serialization.DataResult;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.SnbtPrinterTagVisitor;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;

public final class ItemStackSerializer {
    private ItemStackSerializer() {
    }

    public static class SerializationException extends Exception {
        public SerializationException(String message) {
            super(message);
        }
    }

    private static RegistryOps<Tag> ops(HolderLookup.Provider registries) {
        return RegistryOps.create(NbtOps.INSTANCE, registries);
    }

    public static CompoundTag encode(ItemStack stack, HolderLookup.Provider registries)
            throws SerializationException {
        if (stack == null || stack.isEmpty()) {
            throw new SerializationException("stack is empty");
        }
        DataResult<Tag> result = ItemStack.CODEC.encodeStart(ops(registries), stack);
        Tag tag = result.resultOrPartial(error -> {
        }).orElse(null);
        if (!(tag instanceof CompoundTag compound)) {
            throw new SerializationException(
                    result.error().map(DataResult.Error::message).orElse("codec produced no compound tag"));
        }
        if (result.isError()) {
            throw new SerializationException(
                    result.error().map(DataResult.Error::message).orElse("unknown encode error"));
        }
        return compound;
    }

    public static ItemStack decode(Tag tag, HolderLookup.Provider registries) throws SerializationException {
        if (tag == null) {
            throw new SerializationException("no item data stored");
        }
        DataResult<ItemStack> result = ItemStack.CODEC.parse(ops(registries), tag);
        if (result.isError()) {
            throw new SerializationException(
                    result.error().map(DataResult.Error::message).orElse("unknown decode error"));
        }
        ItemStack stack = result.result().orElse(ItemStack.EMPTY);
        if (stack.isEmpty()) {
            throw new SerializationException("decoded to an empty stack");
        }
        return stack;
    }

    public static String registryId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    public static String toSnbt(Tag tag) {
        return tag.toString();
    }

    public static String toPrettySnbt(Tag tag) {
        return new SnbtPrinterTagVisitor().visit(tag);
    }
}
