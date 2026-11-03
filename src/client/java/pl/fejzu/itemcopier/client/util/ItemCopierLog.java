package pl.fejzu.itemcopier.client.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ItemCopierLog {
    private static final Logger LOGGER = LoggerFactory.getLogger("ItemCopier");
    private static volatile boolean debugEnabled;

    private ItemCopierLog() {
    }

    public static void setDebugEnabled(boolean enabled) {
        debugEnabled = enabled;
    }

    public static boolean isDebugEnabled() {
        return debugEnabled;
    }

    public static void debug(String message, Object... args) {
        if (debugEnabled) {
            LOGGER.info("[debug] " + message, args);
        }
    }

    public static void info(String message, Object... args) {
        LOGGER.info(message, args);
    }

    public static void warn(String message, Object... args) {
        LOGGER.warn(message, args);
    }

    public static void error(String message, Throwable t) {
        LOGGER.error(message, t);
    }
}
