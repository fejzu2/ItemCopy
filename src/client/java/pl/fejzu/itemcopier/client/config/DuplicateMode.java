package pl.fejzu.itemcopier.client.config;

public enum DuplicateMode {
    ALLOW("allow"),
    SKIP("skip"),
    REFRESH("refresh");

    public final String id;

    DuplicateMode(String id) {
        this.id = id;
    }

    public DuplicateMode next() {
        DuplicateMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public static DuplicateMode fromId(String id) {
        for (DuplicateMode mode : values()) {
            if (mode.id.equalsIgnoreCase(id)) {
                return mode;
            }
        }
        return SKIP;
    }
}
