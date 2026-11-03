package pl.fejzu.itemcopier.client.config;

public enum InsertMode {
    INSERT("insert"),
    DRAG_ONLY("drag_only"),
    DETAILS("details");

    public final String id;

    InsertMode(String id) {
        this.id = id;
    }

    public InsertMode next() {
        InsertMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public static InsertMode fromId(String id) {
        for (InsertMode mode : values()) {
            if (mode.id.equalsIgnoreCase(id)) {
                return mode;
            }
        }
        return INSERT;
    }
}
