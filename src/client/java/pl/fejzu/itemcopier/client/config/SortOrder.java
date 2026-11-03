package pl.fejzu.itemcopier.client.config;

import pl.fejzu.itemcopier.client.item.CopiedItem;

import java.util.Comparator;

public enum SortOrder {
    NEWEST("newest"),
    OLDEST("oldest"),
    NAME("name");

    public final String id;

    SortOrder(String id) {
        this.id = id;
    }

    public Comparator<CopiedItem> comparator() {
        return switch (this) {
            case OLDEST -> Comparator.comparing(CopiedItem::copiedAt);
            case NAME -> Comparator.comparing(
                    (CopiedItem item) -> item.plainDisplayName().toLowerCase(java.util.Locale.ROOT))
                    .thenComparing(Comparator.comparing(CopiedItem::copiedAt).reversed());
            default -> Comparator.comparing(CopiedItem::copiedAt).reversed();
        };
    }

    public SortOrder next() {
        SortOrder[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public static SortOrder fromId(String id) {
        for (SortOrder order : values()) {
            if (order.id.equalsIgnoreCase(id)) {
                return order;
            }
        }
        return NEWEST;
    }
}
