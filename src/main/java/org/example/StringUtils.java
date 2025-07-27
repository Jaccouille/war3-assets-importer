package org.example;

import net.moonlightflower.wc3libs.bin.app.W3I;

public class StringUtils {
    public static String buildGameVersionInfo(W3I w3i) {
        // Format: Game Version: major.minor.rev.build
        return String.format(
                "%d.%d.%d.%d",
                w3i.getGameVersion_major(),
                w3i.getGameVersion_minor(),
                w3i.getGameVersion_rev(),
                w3i.getGameVersion_build()
        );
    }
}
