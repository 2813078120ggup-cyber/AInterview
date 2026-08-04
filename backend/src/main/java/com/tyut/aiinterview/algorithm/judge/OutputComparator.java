package com.tyut.aiinterview.algorithm.judge;

/**
 * 判题输出比较：忽略行尾空白与末尾空行，逐行精确比较。
 */
public final class OutputComparator {
    private OutputComparator() {}

    public static boolean matches(String actual, String expected) {
        return normalize(actual).equals(normalize(expected));
    }

    static String normalize(String value) {
        if (value == null) return "";
        String[] lines = value.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        StringBuilder builder = new StringBuilder();
        for (String line : lines) {
            String trimmed = stripTrailing(line);
            if (!trimmed.isEmpty()) {
                if (builder.length() > 0) builder.append('\n');
                builder.append(trimmed);
            }
        }
        return builder.toString();
    }

    private static String stripTrailing(String line) {
        int end = line.length();
        while (end > 0 && Character.isWhitespace(line.charAt(end - 1))) {
            end--;
        }
        return line.substring(0, end);
    }
}
