package com.hiveworkshop.war3assetsimporter.core.util;

import net.moonlightflower.wc3libs.bin.app.W3I;

/**
 * Formatting utilities for Warcraft 3 data types.
 */
public class StringUtils {

    public static String buildGameVersionInfo(W3I w3i) {
        return String.format(
                "%d.%d.%d.%d",
                w3i.getGameVersion_major(),
                w3i.getGameVersion_minor(),
                w3i.getGameVersion_rev(),
                w3i.getGameVersion_build()
        );
    }
}
