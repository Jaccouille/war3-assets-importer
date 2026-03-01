package com.hiveworkshop.war3assetsimporter.core.util;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses Warcraft 3 WTS trigger-string blocks and resolves TRIGSTR_### references
 * to their string values.
 */
public class TrigStrReplacer {

    /**
     * Replaces all {@code TRIGSTR_###} tokens in {@code input} with the corresponding
     * string value from {@code stringBlock}.
     *
     * @param input       text containing TRIGSTR_### references
     * @param stringBlock raw WTS block content
     * @return text with all known TRIGSTR references substituted
     */
    public static String replaceTrigStr(String input, String stringBlock) {
        // Parse STRING blocks into map
        Map<Integer, String> trigStrMap = new HashMap<>();
        Pattern stringPattern = Pattern.compile("STRING\\s+(\\d+)\\s*\\{\\s*(.*?)\\s*\\}", Pattern.DOTALL);
        Matcher matcher = stringPattern.matcher(stringBlock);

        while (matcher.find()) {
            int id = Integer.parseInt(matcher.group(1));
            String value = matcher.group(2).trim();
            trigStrMap.put(id, value);
        }

        // Replace TRIGSTR_### using the map
        Pattern trigstrPattern = Pattern.compile("TRIGSTR_(\\d+)");
        Matcher trigMatcher = trigstrPattern.matcher(input);
        StringBuffer sb = new StringBuffer();

        while (trigMatcher.find()) {
            int id = Integer.parseInt(trigMatcher.group(1));
            String replacement = trigStrMap.getOrDefault(id, trigMatcher.group());
            trigMatcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }

        trigMatcher.appendTail(sb);
        return sb.toString();
    }
}
