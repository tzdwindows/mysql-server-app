package com.mysqllab;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Claude-style minimalist SQL workbench: editor, quick snippets, history, multi-statement results & fullscreen table viewer. */
public class WorkbenchPage extends Page {

    private static final String[][] SNIPPETS = {
            {"数据库", "SHOW DATABASES;"},
            {"建库建表", "CREATE DATABASE IF NOT EXISTS lab;\nUSE lab;\nCREATE TABLE IF NOT EXISTS benchmark (\n"
                    + "    id INT AUTO_INCREMENT PRIMARY KEY,\n    metric VARCHAR(64) NOT NULL,\n"
                    + "    score DOUBLE NOT NULL,\n    meta JSON NULL,\n"
                    + "    recorded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP\n) ENGINE=InnoDB;"},
            {"插入数据", "INSERT INTO lab.benchmark (metric, score, meta) VALUES\n"
                    + "  ('Throughput', 1420.5, JSON_OBJECT('unit','qps')),\n"
                    + "  ('Latency_p99', 4.2, JSON_OBJECT('unit','ms')),\n"
                    + "  ('Memory', 58.4, JSON_OBJECT('unit','MB'));"},
            {"查询", "SELECT * FROM lab.benchmark ORDER BY id DESC;"},
            {"事务", "START TRANSACTION;\nUPDATE lab.benchmark SET score = score * 2 WHERE metric = 'Throughput';\n"
                    + "SELECT metric, score FROM lab.benchmark;\nROLLBACK;"},
            {"窗口函数", "SELECT metric, score,\n       RANK() OVER (ORDER BY score DESC) AS rnk,\n"
                    + "       ROUND(score / SUM(score) OVER (), 4) AS ratio\nFROM lab.benchmark;"},
            {"递归 CTE", "WITH RECURSIVE seq(n, fib, nxt) AS (\n  SELECT 1, 0, 1\n  UNION ALL\n"
                    + "  SELECT n + 1, nxt, fib + nxt FROM seq WHERE n < 20\n)\nSELECT n, fib FROM seq;"},
            {"JSON", "SELECT JSON_OBJECT('author','tzdwindows 7','tags',JSON_ARRAY('mysql','android')) AS doc,\n"
                    + "       JSON_EXTRACT('{\"a\":[1,2,3]}', '$.a[1]') AS picked,\n"
                    + "       JSON_PRETTY(JSON_OBJECT('k', 1)) AS pretty;"},
            {"存储过程", "DELIMITER $$\nCREATE PROCEDURE IF NOT EXISTS lab_hello(IN who VARCHAR(32))\nBEGIN\n"
                    + "    SELECT CONCAT('Hello, ', who, '!') AS greeting, NOW() AS at;\nEND$$\nDELIMITER ;\n\n"
                    + "CALL lab_hello('MySQL');"},
            {"EXPLAIN", "EXPLAIN ANALYZE\nSELECT * FROM lab.benchmark WHERE score > 10 ORDER BY score;"},
            {"进程列表", "SHOW FULL PROCESSLIST;"},
            {"状态", "SHOW GLOBAL STATUS WHERE Variable_name IN ('Uptime','Threads_connected','Questions','Innodb_buffer_pool_pages_data');"},
            {"变量", "SHOW VARIABLES LIKE 'version%';"},
            {"引擎", "SHOW ENGINES;"},
    };

    private static final String DEFAULT_SQL = "SELECT VERSION() AS version, NOW() AS now, @@port AS port, @@datadir AS datadir;";

    private final SqlEditor editor;
    private final ResultGrid grid;
    private final LinearLayout root;
    private final LinearLayout split;
    private final LinearLayout tabsRow;
    private final TextView dbText;
    private final TextView infoText;
    private final FancyButton runBtn;
    private final FancyButton stopBtn;
    private final FancyButton copyBtn;
    private final FancyButton snippetBtn;
    private final HorizontalScrollView snippetScroll;
    private final List<String> history = new ArrayList<>();
    private final SharedPreferences prefs;
    private SqlSession.Batch lastBatch;
    private int selected;
    private int histPos = -1;
    private boolean running;

    public WorkbenchPage(final Context c, AppHost host) {
        super(c, host);
        prefs = c.getSharedPreferences("mysqllab", Context.MODE_PRIVATE);
        loadHistory();

        root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        addView(root, new LayoutParams(-1, -1));

        // ---- toolbar
        LinearLayout bar = new LinearLayout(c);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(bar, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout dbChip = new LinearLayout(c);
        dbChip.setOrientation(LinearLayout.HORIZONTAL);
        dbChip.setGravity(Gravity.CENTER_VERTICAL);
        dbChip.setPadding(Ui.dp(c, 10), 0, Ui.dp(c, 10), 0);
        dbChip.setBackground(Ui.rect(Ui.CARD, Ui.dpf(c, 10), Ui.STROKE, Math.max(1, Ui.dp(c, 1))));
        dbChip.setClickable(true);
        Ui.hand(dbChip);
        dbChip.setTooltipText("当前数据库 · 点击打开数据浏览");
        dbChip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                WorkbenchPage.this.host.showPage(2);
            }
        });
        dbChip.addView(new IconView(c, IconView.DATABASE, Ui.ACCENT), Ui.lp(Ui.dp(c, 15), Ui.dp(c, 15)));
        dbText = Ui.label(c, "未选择数据库", 12.5f, Ui.TEXT, true);
        dbText.setSingleLine(true);
        dbText.setEllipsize(android.text.TextUtils.TruncateAt.END);
        dbText.setMaxWidth(Ui.dp(c, 115));
        dbChip.addView(dbText, Ui.lpm(-2, -2, Ui.dp(c, 6), 0, 0, 0));
        bar.addView(dbChip, new LinearLayout.LayoutParams(-2, Ui.dp(c, 34)));

        View sp = new View(c);
        bar.addView(sp, new LinearLayout.LayoutParams(0, 1, 1f));

        snippetBtn = new FancyButton(c, "示例", IconView.CODE, FancyButton.SECONDARY);
        snippetBtn.setCompact(true);
        snippetBtn.setVisibility(View.GONE);
        snippetBtn.setTooltip("选择内置 SQL 示例");
        snippetBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSnippetsDialog();
            }
        });
        bar.addView(snippetBtn, Ui.lpm(-2, Ui.dp(c, 34), 0, 0, Ui.dp(c, 6), 0));

        FancyButton hist = new FancyButton(c, "", IconView.HISTORY, FancyButton.SECONDARY);
        hist.setCompact(true);
        hist.setTooltip("执行历史 (Ctrl+H)");
        hist.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showHistory();
            }
        });
        bar.addView(hist, Ui.lpm(Ui.dp(c, 34), Ui.dp(c, 34), 0, 0, Ui.dp(c, 6), 0));

        FancyButton clear = new FancyButton(c, "", IconView.TRASH, FancyButton.SECONDARY);
        clear.setCompact(true);
        clear.setTooltip("清空编辑器 (Ctrl+L)");
        clear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clearAll();
            }
        });
        bar.addView(clear, Ui.lpm(Ui.dp(c, 34), Ui.dp(c, 34), 0, 0, Ui.dp(c, 6), 0));

        stopBtn = new FancyButton(c, "", IconView.STOP, FancyButton.DANGER);
        stopBtn.setCompact(true);
        stopBtn.setTooltip("中断正在执行的查询 (KILL QUERY)");
        stopBtn.setVisibility(View.GONE);
        stopBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                WorkbenchPage.this.host.session().cancel();
                WorkbenchPage.this.host.toast("已发送中断请求");
            }
        });
        bar.addView(stopBtn, Ui.lpm(Ui.dp(c, 34), Ui.dp(c, 34), 0, 0, Ui.dp(c, 6), 0));

        runBtn = new FancyButton(c, "运行", IconView.PLAY, FancyButton.PRIMARY);
        runBtn.setCompact(true);
        runBtn.setTooltip("运行 (Ctrl+Enter / F5)");
        runBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                run();
            }
        });
        bar.addView(runBtn, new LinearLayout.LayoutParams(-2, Ui.dp(c, 34)));

        // ---- snippets (visible in portrait, accessible via popup in landscape)
        snippetScroll = new HorizontalScrollView(c);
        snippetScroll.setHorizontalScrollBarEnabled(false);
        snippetScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout chips = new LinearLayout(c);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        snippetScroll.addView(chips, new HorizontalScrollView.LayoutParams(-2, -2));
        for (int i = 0; i < SNIPPETS.length; i++) {
            final String[] sn = SNIPPETS[i];
            FancyButton b = new FancyButton(c, sn[0], -1, FancyButton.SECONDARY);
            b.setCompact(true);
            b.setMinimumHeight(Ui.dp(c, 28));
            b.setPadding(Ui.dp(c, 10), Ui.dp(c, 4), Ui.dp(c, 10), Ui.dp(c, 4));
            b.setTooltip("插入示例: " + sn[0]);
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    editor.insertAtCursor(sn[1], true);
                    editor.focusEditor();
                }
            });
            chips.addView(b, Ui.lpm(-2, Ui.dp(c, 28), 0, 0, Ui.dp(c, 6), 0));
        }
        root.addView(snippetScroll, Ui.lpm(-1, -2, 0, Ui.dp(c, 6), 0, 0));

        // ---- editor + results
        split = new LinearLayout(c);
        split.setOrientation(LinearLayout.VERTICAL);
        root.addView(split, new LinearLayout.LayoutParams(-1, 0, 1f));

        editor = new SqlEditor(c);
        editor.setSql(prefs.getString("draft", DEFAULT_SQL));
        editor.setOnChange(new Runnable() {
            @Override
            public void run() {
                histPos = -1;
            }
        });

        LinearLayout resultCard = new LinearLayout(c);
        resultCard.setOrientation(LinearLayout.VERTICAL);
        LinearLayout rh = new LinearLayout(c);
        rh.setOrientation(LinearLayout.HORIZONTAL);
        rh.setGravity(Gravity.CENTER_VERTICAL);
        HorizontalScrollView tabsScroll = new HorizontalScrollView(c);
        tabsScroll.setHorizontalScrollBarEnabled(false);
        tabsRow = new LinearLayout(c);
        tabsRow.setOrientation(LinearLayout.HORIZONTAL);
        tabsRow.setGravity(Gravity.CENTER_VERTICAL);
        tabsScroll.addView(tabsRow, new HorizontalScrollView.LayoutParams(-2, -2));
        rh.addView(tabsScroll, new LinearLayout.LayoutParams(0, Ui.dp(c, 36), 1f));
        infoText = Ui.label(c, "", 12f, Ui.TEXT2, false);
        infoText.setSingleLine(true);
        rh.addView(infoText, Ui.lpm(-2, -2, Ui.dp(c, 6), 0, Ui.dp(c, 6), 0));

        FancyButton expandBtn = new FancyButton(c, "放大", IconView.VIEW, FancyButton.SECONDARY);
        expandBtn.setCompact(true);
        expandBtn.setTooltip("全屏放大查看结果表格");
        expandBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (grid.hasData()) {
                    Dialogs.fullscreenGrid(getContext(), grid.getCurrentResult(), host);
                } else {
                    WorkbenchPage.this.host.toast("请先执行查询获得结果集后再点击放大");
                }
            }
        });
        rh.addView(expandBtn, Ui.lpm(-2, Ui.dp(c, 32), 0, 0, Ui.dp(c, 6), 0));

        copyBtn = new FancyButton(c, "复制", IconView.COPY, FancyButton.SECONDARY);
        copyBtn.setCompact(true);
        copyBtn.setTooltip("复制整张结果表 (TSV)");
        copyBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (grid.hasData()) {
                    Dialogs.copy(c, "result", grid.toTsv());
                    WorkbenchPage.this.host.toast("整表数据已复制 (TSV)");
                } else {
                    WorkbenchPage.this.host.toast("当前没有可复制的结果集");
                }
            }
        });
        rh.addView(copyBtn, new LinearLayout.LayoutParams(-2, Ui.dp(c, 32)));
        resultCard.addView(rh, new LinearLayout.LayoutParams(-1, -2));

        grid = new ResultGrid(c, host);
        resultCard.addView(grid, Ui.lpm(-1, 0, 0, Ui.dp(c, 6), 0, 0));
        ((LinearLayout.LayoutParams) grid.getLayoutParams()).weight = 1f;

        split.addView(editor, new LinearLayout.LayoutParams(-1, 0, 0.85f));
        split.addView(resultCard, new LinearLayout.LayoutParams(-1, 0, 1.15f));
        resetTabs();
        setWide(false);
    }

    private static android.graphics.drawable.Drawable chipBg(Context c, boolean selected, int color) {
        float r = Ui.dpf(c, 8);
        int fill = selected ? Ui.alpha(color, 36) : Ui.BG2;
        int stroke = selected ? color : Ui.STROKE;
        return new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x1F141413),
                Ui.rect(fill, r, stroke, Math.max(1, Ui.dp(c, 1))),
                Ui.rect(0xFFFFFFFF, r, 0, 0));
    }

    @Override
    public void setWide(boolean w) {
        super.setWide(w);
        Context c = getContext();
        int pad = Ui.dp(c, w ? 10 : 8);
        root.setPadding(pad, w ? 0 : pad, pad, pad);
        split.setOrientation(w ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        int gap = Ui.dp(c, w ? 8 : 6);
        snippetScroll.setVisibility(w ? View.GONE : View.VISIBLE);
        snippetBtn.setVisibility(w ? View.VISIBLE : View.GONE);

        LinearLayout.LayoutParams e = (LinearLayout.LayoutParams) editor.getLayoutParams();
        View result = split.getChildAt(1);
        LinearLayout.LayoutParams r = (LinearLayout.LayoutParams) result.getLayoutParams();
        if (w) {
            e.width = 0;
            e.height = -1;
            e.weight = 1f;
            e.setMargins(0, gap, gap, 0);
            r.width = 0;
            r.height = -1;
            r.weight = 1.35f;
            r.setMargins(0, gap, 0, 0);
        } else {
            e.width = -1;
            e.height = 0;
            e.weight = 0.85f;
            e.setMargins(0, gap, 0, 0);
            r.width = -1;
            r.height = 0;
            r.weight = 1.15f;
            r.setMargins(0, gap, 0, 0);
        }
        editor.setLayoutParams(e);
        result.setLayoutParams(r);
    }

    private void showSnippetsDialog() {
        String[] labels = new String[SNIPPETS.length];
        int[] icons = new int[SNIPPETS.length];
        for (int i = 0; i < SNIPPETS.length; i++) {
            labels[i] = SNIPPETS[i][0];
            icons[i] = IconView.CODE;
        }
        Dialogs.actions(getContext(), "SQL 语法与实验示例", "点击直接插入到当前编辑器光标处",
                labels, icons, new Dialogs.Pick<Integer>() {
                    @Override
                    public void picked(Integer idx) {
                        if (idx >= 0 && idx < SNIPPETS.length) {
                            editor.insertAtCursor(SNIPPETS[idx][1], true);
                            editor.focusEditor();
                        }
                    }
                });
    }

    @Override
    public void onShown() {
        Ui.reveal(root, 0);
    }

    @Override
    public void onPauseSave() {
        prefs.edit().putString("draft", editor.getSql()).apply();
    }

    public void runSql(String sql, boolean execute) {
        editor.setSql(sql);
        if (execute) run();
        else editor.focusEditor();
    }

    public void clearAll() {
        editor.clear();
        lastBatch = null;
        resetTabs();
        infoText.setText("");
        grid.showMessage(IconView.TERMINAL, Ui.MUTED, "已清空", "编写 SQL 后点击「运行」");
        editor.focusEditor();
    }

    public void run() {
        if (running) return;
        if (host.serverState() != ServerState.RUNNING) {
            host.toast("MySQL 服务器尚未运行，请先到「服务器」页启动");
            Ui.shake(runBtn);
            return;
        }
        final String sql = editor.getSelectionOrAll().trim();
        if (sql.length() == 0) {
            host.toast("请先输入 SQL");
            Ui.shake(editor);
            return;
        }
        addHistory(sql);
        setRunning(true);
        grid.showLoading();
        infoText.setText("");
        host.session().runScript(sql, new SqlSession.Callback<SqlSession.Batch>() {
            @Override
            public void done(SqlSession.Batch b) {
                setRunning(false);
                onBatch(b);
            }
        });
    }

    private void setRunning(boolean r) {
        running = r;
        runBtn.setEnabled(!r);
        runBtn.setLabel(r ? "运行中…" : "运行");
        stopBtn.setVisibility(r ? View.VISIBLE : View.GONE);
    }

    private void onBatch(SqlSession.Batch b) {
        lastBatch = b;
        dbText.setText(b.database.length() == 0 ? "未选择数据库" : b.database);
        if (b.results.isEmpty()) {
            resetTabs();
            grid.showMessage(IconView.INFO, Ui.MUTED, "没有可执行的语句", "只有注释或空白内容");
            return;
        }
        int pick = b.results.size() - 1;
        for (int i = b.results.size() - 1; i >= 0; i--) {
            QueryResult r = b.results.get(i);
            if (!r.isSuccess()) {
                pick = i;
                break;
            }
        }
        if (b.results.get(pick).isSuccess()) {
            for (int i = b.results.size() - 1; i >= 0; i--) {
                if (b.results.get(i).isResultSet) {
                    pick = i;
                    break;
                }
            }
        }
        selected = pick;
        buildTabs();
        selectResult(pick);
        if (b.results.size() > 1) {
            infoText.setText(b.statements.size() + " 条语句 · " + b.totalMs + " ms");
        }
    }

    private void resetTabs() {
        tabsRow.removeAllViews();
        Context c = getContext();
        TextView t = Ui.label(c, "结果", 14.5f, Ui.TEXT, true);
        t.setPadding(Ui.dp(c, 4), 0, 0, 0);
        tabsRow.addView(t);
    }

    private void buildTabs() {
        Context c = getContext();
        tabsRow.removeAllViews();
        for (int i = 0; i < lastBatch.results.size(); i++) {
            final int idx = i;
            QueryResult r = lastBatch.results.get(i);
            String sum;
            int color;
            if (!r.isSuccess()) {
                sum = "错误";
                color = Ui.RED;
            } else if (r.isResultSet) {
                sum = r.rows.size() + " 行";
                color = Ui.ACCENT;
            } else {
                sum = r.affectedRows + " 受影响";
                color = Ui.GREEN;
            }
            TextView t = Ui.label(c, "#" + (i + 1) + "  " + sum, 12f, Ui.TEXT, true);
            t.setPadding(Ui.dp(c, 10), 0, Ui.dp(c, 10), 0);
            t.setGravity(Gravity.CENTER);
            t.setClickable(true);
            t.setFocusable(true);
            t.setTag(color);
            Ui.hand(t);
            String stmt = lastBatch.statements.size() > i ? lastBatch.statements.get(i) : "";
            t.setTooltipText(stmt.length() > 200 ? stmt.substring(0, 200) + "…" : stmt);
            t.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    selectResult(idx);
                }
            });
            tabsRow.addView(t, Ui.lpm(-2, Ui.dp(c, 28), 0, 0, Ui.dp(c, 6), 0));
        }
    }

    private void selectResult(int i) {
        if (lastBatch == null || i < 0 || i >= lastBatch.results.size()) return;
        selected = i;
        Context c = getContext();
        for (int k = 0; k < tabsRow.getChildCount(); k++) {
            View v = tabsRow.getChildAt(k);
            Object tag = v.getTag();
            if (tag instanceof Integer) {
                v.setBackground(chipBg(c, k == i, (Integer) tag));
            }
        }
        QueryResult r = lastBatch.results.get(i);
        grid.showResult(r);
        if (!r.isSuccess()) {
            infoText.setText("出错 · " + r.executionTimeMs + " ms");
        } else if (r.isResultSet) {
            infoText.setText(r.rows.size() + " 行 · " + r.executionTimeMs + " ms");
        } else {
            infoText.setText(r.executionTimeMs + " ms");
        }
    }

    // ---- history

    private void loadHistory() {
        String raw = prefs.getString("history", "");
        if (raw.length() > 0) {
            for (String s : raw.split("\u0001")) {
                if (s.length() > 0) history.add(s);
            }
        }
    }

    private void saveHistory() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < history.size(); i++) {
            if (i > 0) sb.append('\u0001');
            sb.append(history.get(i));
        }
        prefs.edit().putString("history", sb.toString()).apply();
    }

    private void addHistory(String sql) {
        if (sql.length() > 20000) return;
        history.remove(sql);
        history.add(sql);
        while (history.size() > 60) history.remove(0);
        saveHistory();
        histPos = -1;
    }

    public void showHistory() {
        Dialogs.history(getContext(), history, new Dialogs.Pick<String>() {
            @Override
            public void picked(String sql) {
                editor.setSql(sql);
                editor.focusEditor();
            }
        }, new Runnable() {
            @Override
            public void run() {
                history.clear();
                saveHistory();
                host.toast("历史已清空");
            }
        });
    }

    public void historyNav(int dir) {
        if (history.isEmpty()) return;
        if (histPos == -1) histPos = history.size();
        histPos = Math.max(0, Math.min(history.size() - 1, histPos + dir));
        int keep = histPos;
        editor.setSql(history.get(keep));
        histPos = keep;
    }
}
