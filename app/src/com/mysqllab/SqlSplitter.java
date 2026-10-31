package com.mysqllab;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits a SQL script into statements. Understands quotes, backticks, comments and the
 * mysql-client style "DELIMITER xx" command (needed for stored procedures / triggers).
 */
public final class SqlSplitter {
    private SqlSplitter() {}

    public static List<String> split(String script) {
        List<String> out = new ArrayList<>();
        if (script == null) return out;
        String delim = ";";
        StringBuilder cur = new StringBuilder();
        boolean hasCode = false;
        int n = script.length();
        int i = 0;
        boolean lineStart = true;
        while (i < n) {
            char ch = script.charAt(i);

            if (lineStart) {
                int j = i;
                while (j < n && (script.charAt(j) == ' ' || script.charAt(j) == '\t')) j++;
                if (script.regionMatches(true, j, "delimiter", 0, 9)
                        && (j + 9 >= n || Character.isWhitespace(script.charAt(j + 9)))) {
                    int e = script.indexOf('\n', j);
                    if (e < 0) e = n;
                    String d = script.substring(j + 9, e).trim();
                    if (d.length() > 0) delim = d;
                    i = e;
                    lineStart = false;
                    continue;
                }
            }
            lineStart = false;

            if (ch == '\n') {
                cur.append(ch);
                i++;
                lineStart = true;
                continue;
            }

            if (ch == '\'' || ch == '"' || ch == '`') {
                int j = i + 1;
                while (j < n) {
                    char d = script.charAt(j);
                    if (d == '\\' && ch != '`') {
                        j += 2;
                        continue;
                    }
                    if (d == ch) {
                        if (j + 1 < n && script.charAt(j + 1) == ch) {
                            j += 2;
                            continue;
                        }
                        break;
                    }
                    j++;
                }
                j = Math.min(n - 1, j);
                cur.append(script, i, j + 1);
                hasCode = true;
                i = j + 1;
                continue;
            }

            if (ch == '#' || (ch == '-' && i + 1 < n && script.charAt(i + 1) == '-'
                    && (i + 2 >= n || Character.isWhitespace(script.charAt(i + 2))))) {
                int e = script.indexOf('\n', i);
                if (e < 0) e = n;
                cur.append(script, i, e);
                i = e;
                continue;
            }

            if (ch == '/' && i + 1 < n && script.charAt(i + 1) == '*') {
                int e = script.indexOf("*/", i + 2);
                e = e < 0 ? n : e + 2;
                // executable comments /*! ... */ count as code
                if (i + 2 < n && script.charAt(i + 2) == '!') hasCode = true;
                cur.append(script, i, e);
                i = e;
                continue;
            }

            if (script.startsWith(delim, i)) {
                if (hasCode) out.add(cur.toString().trim());
                cur.setLength(0);
                hasCode = false;
                i += delim.length();
                continue;
            }

            if (!Character.isWhitespace(ch)) hasCode = true;
            cur.append(ch);
            i++;
        }
        if (hasCode) out.add(cur.toString().trim());
        return out;
    }
}
