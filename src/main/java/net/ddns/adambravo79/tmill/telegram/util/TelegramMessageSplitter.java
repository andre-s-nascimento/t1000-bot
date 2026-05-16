/* (c) 2026 | 15/05/2026 */
package net.ddns.adambravo79.tmill.telegram.util;

import java.util.ArrayList;
import java.util.List;

public class TelegramMessageSplitter {

    private static final int TELEGRAM_LIMIT = 3900;

    private TelegramMessageSplitter() {}

    public static List<String> split(String text) {

        List<String> parts = new ArrayList<>();

        if (text == null || text.isBlank()) {
            return parts;
        }

        int start = 0;

        while (start < text.length()) {

            int end = Math.min(start + TELEGRAM_LIMIT, text.length());

            if (end < text.length()) {

                int lastBreak = text.lastIndexOf("\n", end);

                if (lastBreak > start + 1000) {
                    end = lastBreak;
                }
            }

            parts.add(text.substring(start, end).trim());

            start = end;
        }

        return parts;
    }
}
