package com.mysqllab;

import android.content.Context;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.InputType;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Claude-style minimalist SQL code editor:
 * Warm code paper surface (#F4F2EC), 12dp hardware-clipped rounded corners (zero square corner bleed),
 * subtle 1dp stone divider for line numbers, and editorial syntax highlighting.
 */
public class SqlEditor extends FrameLayout {

    static class SyntaxSpan extends ForegroundColorSpan {
        SyntaxSpan(int color) {
            super(color);
        }
    }

    private static final String KEYWORDS =
            "SELECT|FROM|WHERE|GROUP|BY|HAVING|ORDER|LIMIT|OFFSET|INSERT|INTO|VALUES|VALUE|UPDATE|SET|DELETE|REPLACE|"
                    + "CREATE|ALTER|DROP|TRUNCATE|RENAME|TABLE|TABLES|DATABASE|DATABASES|SCHEMA|SCHEMAS|INDEX|VIEW|TRIGGER|PROCEDURE|FUNCTION|EVENT|"
                    + "AS|ON|USING|JOIN|INNER|LEFT|RIGHT|OUTER|CROSS|NATURAL|UNION|ALL|DISTINCT|EXCEPT|INTERSECT|"
                    + "AND|OR|NOT|NULL|IS|IN|EXISTS|BETWEEN|LIKE|REGEXP|RLIKE|CASE|WHEN|THEN|ELSE|END|IF|"
                    + "PRIMARY|KEY|FOREIGN|REFERENCES|UNIQUE|DEFAULT|AUTO_INCREMENT|CONSTRAINT|CHECK|COMMENT|ENGINE|CHARSET|CHARACTER|COLLATE|"
                    + "INT|INTEGER|BIGINT|SMALLINT|TINYINT|MEDIUMINT|DECIMAL|NUMERIC|FLOAT|DOUBLE|BIT|BOOLEAN|BOOL|"
                    + "CHAR|VARCHAR|TEXT|TINYTEXT|MEDIUMTEXT|LONGTEXT|BLOB|LONGBLOB|BINARY|VARBINARY|ENUM|JSON|"
                    + "DATE|TIME|DATETIME|TIMESTAMP|YEAR|UNSIGNED|SIGNED|ZEROFILL|GENERATED|ALWAYS|VIRTUAL|STORED|"
                    + "SHOW|DESCRIBE|DESC|ASC|EXPLAIN|ANALYZE|USE|GRANT|REVOKE|FLUSH|PRIVILEGES|USER|IDENTIFIED|WITH|OPTION|"
                    + "BEGIN|START|TRANSACTION|COMMIT|ROLLBACK|SAVEPOINT|LOCK|UNLOCK|WINDOW|OVER|PARTITION|ROWS|RANGE|RECURSIVE|"
                    + "DECLARE|CURSOR|OPEN|FETCH|CLOSE|LOOP|WHILE|DO|REPEAT|UNTIL|LEAVE|ITERATE|RETURN|RETURNS|CALL|DELIMITER|"
                    + "FOR|EACH|ROW|BEFORE|AFTER|INSTEAD|OF|TEMPORARY|ADD|COLUMN|MODIFY|CHANGE|FIRST|AFTER|STATUS|VARIABLES|PROCESSLIST|KILL|"
                    + "LATERAL|ESCAPE|INTERVAL|TRUE|FALSE|UNKNOWN|DUAL|IGNORE|DUPLICATE|SHARE|MODE|NOWAIT|SKIP|LOCKED";

    private static final Pattern TOKEN = Pattern.compile(
            "(--[ \\t][^\\n]*|--$|#[^\\n]*|/\\*[\\s\\S]*?(?:\\*/|$))"          // 1 comment
                    + "|('(?:[^'\\\\]|\\\\[\\s\\S]|'')*'?|\"(?:[^\"\\\\]|\\\\[\\s\\S])*\"?)" // 2 string
                    + "|(`[^`]*`?)"                                              // 3 identifier
                    + "|(\\b\\d+(?:\\.\\d+)?\\b)"                                // 4 number
                    + "|(\\b(?:" + KEYWORDS + ")\\b)"                            // 5 keyword
                    + "|(\\b[A-Za-z_][A-Za-z_0-9]*(?=\\())"                      // 6 function
                    + "|(@@?[A-Za-z_][A-Za-z_0-9.]*)",                           // 7 variable
            Pattern.CASE_INSENSITIVE);

    private static final int EDITOR_BG = 0xFFF4F2EC;
    private static final int EDITOR_BG_FOCUSED = 0xFFFFFFFF;

    private final EditText edit;
    private final TextView gutter;
    private final ScrollView scroll;
    private boolean internal;
    private int lastLines = -1;
    private Runnable onChange;

    public SqlEditor(Context c) {
        super(c);
        int bPad = Math.max(1, Ui.dp(c, 1));
        setPadding(bPad, bPad, bPad, bPad);
        setBackground(Ui.rect(EDITOR_BG, Ui.dpf(c, 12), Ui.STROKE, bPad));
        Ui.clipRound(this, 12);

        scroll = new ScrollView(c);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(true);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        scroll.addView(row, new FrameLayout.LayoutParams(-1, -2));

        int padV = Ui.dp(c, 12);
        gutter = new TextView(c);
        gutter.setTypeface(Typeface.MONOSPACE);
        gutter.setTextSize(13.5f);
        gutter.setIncludeFontPadding(false);
        gutter.setLineSpacing(0f, 1.25f);
        gutter.setTextColor(Ui.MUTED);
        gutter.setGravity(Gravity.TOP | Gravity.END);
        gutter.setPadding(Ui.dp(c, 10), padV, Ui.dp(c, 8), padV);
        gutter.setMinWidth(Ui.dp(c, 38));
        // Transparent background so the gutter never paints a rectangular corner over the rounded container
        gutter.setBackgroundColor(0x00000000);
        gutter.setText("1");
        row.addView(gutter, new LinearLayout.LayoutParams(-2, -1));

        // 1dp vertical stone divider between line numbers and SQL code
        View divider = new View(c);
        divider.setBackgroundColor(Ui.STROKE);
        row.addView(divider, new LinearLayout.LayoutParams(Math.max(1, Ui.dp(c, 1)), -1));

        edit = new EditText(c);
        edit.setBackground(null);
        edit.setTypeface(Typeface.MONOSPACE);
        edit.setTextSize(13.5f);
        edit.setIncludeFontPadding(false);
        edit.setLineSpacing(0f, 1.25f);
        edit.setTextColor(Ui.TEXT);
        edit.setHintTextColor(Ui.MUTED);
        edit.setHint("-- 在此编写 SQL，点击右上角「运行」或按 Ctrl+Enter\n-- 支持多语句、DELIMITER、事务、窗口函数、CTE、JSON");
        edit.setGravity(Gravity.TOP | Gravity.START);
        edit.setHorizontallyScrolling(true);
        edit.setMinLines(5);
        edit.setPadding(Ui.dp(c, 12), padV, Ui.dp(c, 14), padV);
        edit.setHighlightColor(0x33D97757);
        edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        edit.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        row.addView(edit, new LinearLayout.LayoutParams(0, -1, 1f));

        edit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int d) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int d) {
            }

            @Override
            public void afterTextChanged(Editable e) {
                if (internal) return;
                highlight(e);
                updateGutter(e);
                if (onChange != null) onChange.run();
            }
        });

        edit.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent ev) {
                if (ev.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (keyCode == KeyEvent.KEYCODE_TAB && !ev.isCtrlPressed() && !ev.isAltPressed()) {
                    insertAtCursor("    ", false);
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_SLASH && ev.isCtrlPressed()) {
                    toggleComment();
                    return true;
                }
                return false;
            }
        });

        edit.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean has) {
                Context ctx = v.getContext();
                setBackground(Ui.rect(has ? EDITOR_BG_FOCUSED : EDITOR_BG, Ui.dpf(ctx, 12),
                        has ? Ui.ACCENT : Ui.STROKE, Math.max(has ? 2 : 1, Ui.dp(ctx, has ? 1.5f : 1f))));
                Ui.clipRound(SqlEditor.this, 12);
            }
        });
        updateGutter(edit.getText());
    }

    public void setOnChange(Runnable r) {
        this.onChange = r;
    }

    public EditText getEditText() {
        return edit;
    }

    public String getSql() {
        return edit.getText().toString();
    }

    public String getSelectionOrAll() {
        int s = edit.getSelectionStart();
        int e = edit.getSelectionEnd();
        if (s >= 0 && e >= 0 && s != e) {
            return edit.getText().subSequence(Math.min(s, e), Math.max(s, e)).toString();
        }
        return getSql();
    }

    public void setSql(String sql) {
        edit.setText(sql);
        edit.setSelection(edit.getText().length());
    }

    public void clear() {
        edit.setText("");
    }

    public void focusEditor() {
        edit.requestFocus();
    }

    public void insertAtCursor(String text, boolean asBlock) {
        Editable e = edit.getText();
        int s = Math.max(0, Math.min(edit.getSelectionStart(), edit.getSelectionEnd()));
        int en = Math.max(0, Math.max(edit.getSelectionStart(), edit.getSelectionEnd()));
        String ins = text;
        if (asBlock) {
            boolean needLeadNl = s > 0 && e.charAt(s - 1) != '\n';
            ins = (needLeadNl ? "\n" : "") + text + "\n";
        }
        e.replace(s, en, ins);
        edit.setSelection(Math.min(e.length(), s + ins.length()));
    }

    private void toggleComment() {
        Editable e = edit.getText();
        String all = e.toString();
        int s = Math.max(0, Math.min(edit.getSelectionStart(), edit.getSelectionEnd()));
        int en = Math.max(0, Math.max(edit.getSelectionStart(), edit.getSelectionEnd()));
        int ls = all.lastIndexOf('\n', Math.max(0, s - 1));
        ls = (s == 0 || ls < 0) ? 0 : ls + 1;
        int le = all.indexOf('\n', en);
        if (le < 0) le = all.length();
        String block = all.substring(ls, le);
        String[] lines = block.split("\n", -1);
        boolean allCommented = true;
        for (String ln : lines) {
            if (ln.trim().length() > 0 && !ln.trim().startsWith("--")) {
                allCommented = false;
                break;
            }
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            String ln = lines[i];
            if (allCommented) {
                int k = ln.indexOf("--");
                if (k >= 0) {
                    int cut = k + 2;
                    if (cut < ln.length() && ln.charAt(cut) == ' ') cut++;
                    ln = ln.substring(0, k) + ln.substring(cut);
                }
            } else if (ln.trim().length() > 0) {
                ln = "-- " + ln;
            }
            sb.append(ln);
            if (i < lines.length - 1) sb.append('\n');
        }
        e.replace(ls, le, sb.toString());
        edit.setSelection(ls, ls + sb.length());
    }

    private void updateGutter(Editable e) {
        int lines = 1;
        for (int i = 0; i < e.length(); i++) if (e.charAt(i) == '\n') lines++;
        if (lines == lastLines) return;
        lastLines = lines;
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= lines; i++) {
            sb.append(i);
            if (i < lines) sb.append('\n');
        }
        gutter.setText(sb);
    }

    private void highlight(Editable e) {
        internal = true;
        try {
            SyntaxSpan[] old = e.getSpans(0, e.length(), SyntaxSpan.class);
            for (SyntaxSpan sp : old) e.removeSpan(sp);
            if (e.length() > 40000) return;
            Matcher m = TOKEN.matcher(e);
            while (m.find()) {
                int color;
                if (m.group(1) != null) color = 0xFF8C8A82;      // Warm grey comment
                else if (m.group(2) != null) color = 0xFF2E7D5B; // Sage green string
                else if (m.group(3) != null) color = 0xFF2D5B88; // Slate blue identifier
                else if (m.group(4) != null) color = 0xFF9E5A1B; // Warm bronze number
                else if (m.group(5) != null) color = 0xFFB84A2E; // Terracotta rust keyword
                else if (m.group(6) != null) color = 0xFF6B4F8A; // Muted plum function
                else color = 0xFF9E5A1B;                         // System variable
                e.setSpan(new SyntaxSpan(color), m.start(), m.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        } finally {
            internal = false;
        }
    }
}
