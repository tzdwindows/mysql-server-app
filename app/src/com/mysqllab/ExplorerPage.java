package com.mysqllab;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Claude-style minimalist schema & data explorer: browse databases, tables/views, data, structure, DDL and indexes. */
public class ExplorerPage extends Page {

    private static final Set<String> SYS_DBS = new HashSet<>(
            Arrays.asList("information_schema", "mysql", "performance_schema", "sys"));

    private final LinearLayout split;
    private final Card leftCard;
    private final Card rightCard;
    private final LinearLayout dbRow;
    private final LinearLayout tableList;
    private final TextView tableHeader;
    private final TextView rightTitle;
    private final LinearLayout modeRow;
    private final ResultGrid grid;

    private String selectedDb = "";
    private String selectedTable = "";
    private String selectedType = "BASE TABLE";
    private int inspectMode = 0; // 0=data, 1=describe, 2=ddl, 3=index

    private final FancyButton[] modeBtns = new FancyButton[4];

    public ExplorerPage(final Context c, final AppHost host) {
        super(c, host);

        split = new LinearLayout(c);
        addView(split, new LayoutParams(-1, -1));

        // ---- Left / Top panel: databases + tables
        leftCard = new Card(c);
        LinearLayout dbHdr = Card.headerRow(c, IconView.DATABASE, Ui.ACCENT, "数据库与表");
        FancyButton newDbBtn = new FancyButton(c, "新建库", IconView.PLUS, FancyButton.SECONDARY);
        newDbBtn.setCompact(true);
        newDbBtn.setTooltip("创建新数据库 (CREATE DATABASE)");
        newDbBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                promptCreateDatabase();
            }
        });
        FancyButton refBtn = new FancyButton(c, "", IconView.REFRESH, FancyButton.GHOST);
        refBtn.setCompact(true);
        refBtn.setTooltip("刷新数据库列表");
        refBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Ui.pop(v);
                reloadDatabases();
            }
        });
        dbHdr.addView(newDbBtn, Ui.lpm(-2, Ui.dp(c, 32), 0, 0, Ui.dp(c, 4), 0));
        dbHdr.addView(refBtn, Ui.lp(Ui.dp(c, 34), Ui.dp(c, 32)));
        leftCard.addView(dbHdr);

        HorizontalScrollView dbScroll = new HorizontalScrollView(c);
        dbScroll.setHorizontalScrollBarEnabled(false);
        dbRow = new LinearLayout(c);
        dbRow.setOrientation(LinearLayout.HORIZONTAL);
        dbRow.setPadding(0, Ui.dp(c, 8), 0, Ui.dp(c, 6));
        dbScroll.addView(dbRow, new ViewGroup.LayoutParams(-2, -2));
        leftCard.addView(dbScroll, new LinearLayout.LayoutParams(-1, -2));

        tableHeader = Ui.label(c, "选择数据库以查看表与视图", 12f, Ui.TEXT2, true);
        tableHeader.setPadding(Ui.dp(c, 4), Ui.dp(c, 6), Ui.dp(c, 4), Ui.dp(c, 6));
        leftCard.addView(tableHeader);

        ScrollView tableScroll = new ScrollView(c);
        tableScroll.setFillViewport(true);
        tableList = new LinearLayout(c);
        tableList.setOrientation(LinearLayout.VERTICAL);
        tableScroll.addView(tableList, new ViewGroup.LayoutParams(-1, -2));
        leftCard.addView(tableScroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        // ---- Right / Bottom panel: table inspector & data grid
        rightCard = new Card(c);
        LinearLayout rHdr = new LinearLayout(c);
        rHdr.setOrientation(LinearLayout.HORIZONTAL);
        rHdr.setGravity(Gravity.CENTER_VERTICAL);
        rHdr.addView(new IconView(c, IconView.TABLE, Ui.ACCENT), Ui.lp(Ui.dp(c, 18), Ui.dp(c, 18)));
        rightTitle = Ui.label(c, "未选择数据表", 14.5f, Ui.TEXT, true);
        rightTitle.setSingleLine(true);
        rightTitle.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        rHdr.addView(rightTitle, new LinearLayout.LayoutParams(0, -2, 1f));
        ((LinearLayout.LayoutParams) rightTitle.getLayoutParams()).leftMargin = Ui.dp(c, 8);

        FancyButton expandBtn = new FancyButton(c, "放大", IconView.VIEW, FancyButton.SECONDARY);
        expandBtn.setCompact(true);
        expandBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (grid.hasData()) {
                    Dialogs.fullscreenGrid(getContext(), grid.getCurrentResult(), host);
                } else {
                    host.toast("当前没有可放大的结果集");
                }
            }
        });
        FancyButton toWb = new FancyButton(c, "工作台", IconView.TERMINAL, FancyButton.SECONDARY);
        toWb.setCompact(true);
        toWb.setTooltip("将当前表查询发送到 SQL 工作台");
        toWb.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (selectedDb.isEmpty() || selectedTable.isEmpty()) {
                    host.toast("请先选择一张表");
                    return;
                }
                String sql = "USE `" + esc(selectedDb) + "`;\nSELECT * FROM `" + esc(selectedTable) + "` LIMIT 100;";
                host.runInWorkbench(sql, false);
            }
        });
        FancyButton moreBtn = new FancyButton(c, "", IconView.BOLT, FancyButton.GHOST);
        moreBtn.setCompact(true);
        moreBtn.setTooltip("更多表操作");
        moreBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showTableActions();
            }
        });
        rHdr.addView(expandBtn, Ui.lpm(-2, Ui.dp(c, 32), Ui.dp(c, 4), 0, Ui.dp(c, 4), 0));
        rHdr.addView(toWb, Ui.lpm(-2, Ui.dp(c, 32), 0, 0, Ui.dp(c, 4), 0));
        rHdr.addView(moreBtn, Ui.lp(Ui.dp(c, 34), Ui.dp(c, 32)));
        rightCard.addView(rHdr);

        // Mode switcher: 数据 | 结构 | DDL | 索引
        HorizontalScrollView modeScroll = new HorizontalScrollView(c);
        modeScroll.setHorizontalScrollBarEnabled(false);
        modeRow = new LinearLayout(c);
        modeRow.setOrientation(LinearLayout.HORIZONTAL);
        modeRow.setPadding(0, Ui.dp(c, 8), 0, Ui.dp(c, 8));
        String[] labels = {"数据预览", "表结构", "建表 DDL", "索引信息"};
        int[] icons = {IconView.TABLE, IconView.CHIP, IconView.CODE, IconView.BOLT};
        for (int i = 0; i < labels.length; i++) {
            final int mode = i;
            FancyButton b = new FancyButton(c, labels[i], icons[i],
                    i == 0 ? FancyButton.PRIMARY : FancyButton.SECONDARY);
            b.setCompact(true);
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    setInspectMode(mode);
                }
            });
            modeBtns[i] = b;
            modeRow.addView(b, Ui.lpm(-2, Ui.dp(c, 32), 0, 0, Ui.dp(c, 8), 0));
        }
        modeScroll.addView(modeRow, new ViewGroup.LayoutParams(-2, -2));
        rightCard.addView(modeScroll, new LinearLayout.LayoutParams(-1, -2));

        grid = new ResultGrid(c, host);
        grid.showMessage(IconView.DATABASE, Ui.MUTED, "数据库资源管理器",
                "从上方/左侧选择数据库与数据表，即可浏览数据、字段结构、建表语句与索引");
        rightCard.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1f));

        split.addView(leftCard);
        split.addView(rightCard);
        applyLayout(false);
    }

    @Override
    public void setWide(boolean wide) {
        if (this.wide == wide) return;
        super.setWide(wide);
        applyLayout(wide);
    }

    private void applyLayout(boolean wide) {
        Context c = getContext();
        int gap = Ui.dp(c, 10);
        split.setOrientation(wide ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(wide ? 0 : -1, wide ? -1 : 0, wide ? 0.85f : 0.78f);
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(wide ? 0 : -1, wide ? -1 : 0, wide ? 1.45f : 1.22f);
        if (wide) {
            lp1.setMargins(0, 0, gap, 0);
            lp2.setMargins(0, 0, 0, 0);
        } else {
            lp1.setMargins(0, 0, 0, gap);
            lp2.setMargins(0, 0, 0, 0);
        }
        leftCard.setLayoutParams(lp1);
        rightCard.setLayoutParams(lp2);
    }

    @Override
    public void onShown() {
        Ui.reveal(split, 0);
        if (host.serverState() == ServerState.RUNNING) {
            reloadDatabases();
        } else {
            grid.showMessage(IconView.POWER, Ui.AMBER, "MySQL 未运行", "请先在「服务器」页面启动实例");
        }
    }

    @Override
    public void onServerState(ServerState state, String message) {
        if (state == ServerState.RUNNING && isShown()) {
            reloadDatabases();
        }
    }

    private void setInspectMode(int mode) {
        inspectMode = mode;
        for (int i = 0; i < modeBtns.length; i++) {
            modeBtns[i].setStyle(i == mode ? FancyButton.PRIMARY : FancyButton.SECONDARY);
        }
        if (!selectedDb.isEmpty() && !selectedTable.isEmpty()) {
            inspectSelectedTable();
        }
    }

    public void reloadDatabases() {
        if (host.serverState() != ServerState.RUNNING) {
            host.toast("请先启动 MySQL 服务器");
            return;
        }
        host.session().query("SHOW DATABASES", new SqlSession.Callback<QueryResult>() {
            @Override
            public void done(QueryResult r) {
                if (r == null || !r.isSuccess()) return;
                List<String> dbs = new ArrayList<>();
                for (List<String> row : r.rows) {
                    if (!row.isEmpty() && row.get(0) != null) dbs.add(row.get(0));
                }
                renderDatabases(dbs);
            }
        });
    }

    private void renderDatabases(List<String> dbs) {
        Context c = getContext();
        dbRow.removeAllViews();
        if (dbs.isEmpty()) return;

        if (selectedDb.isEmpty() || !dbs.contains(selectedDb)) {
            selectedDb = dbs.get(0);
            for (String d : dbs) {
                if (!SYS_DBS.contains(d.toLowerCase())) {
                    selectedDb = d;
                    break;
                }
            }
        }

        for (final String db : dbs) {
            final boolean sel = db.equals(selectedDb);
            final boolean sys = SYS_DBS.contains(db.toLowerCase());
            TextView chip = Ui.label(c, db, 12.5f, sel ? Ui.TEXT : Ui.TEXT2, sel);
            chip.setTypeface(Typeface.create(Typeface.MONOSPACE, sel ? Typeface.BOLD : Typeface.NORMAL));
            chip.setPadding(Ui.dp(c, 12), 0, Ui.dp(c, 12), 0);
            chip.setGravity(Gravity.CENTER);
            chip.setClickable(true);
            chip.setFocusable(true);
            Ui.hand(chip);
            int accent = sys ? Ui.BLUE : Ui.ACCENT;
            chip.setBackground(chipDrawable(c, sel, accent));
            chip.setTooltipText(sys ? "系统库: " + db : "用户库: " + db + " (长按管理)");
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    selectDatabase(db);
                }
            });
            chip.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    showDatabaseMenu(db, sys);
                    return true;
                }
            });
            dbRow.addView(chip, Ui.lpm(-2, Ui.dp(c, 30), 0, 0, Ui.dp(c, 6), 0));
        }

        loadTables(selectedDb);
    }

    private void selectDatabase(String db) {
        selectedDb = db;
        selectedTable = "";
        reloadDatabases();
    }

    private void loadTables(final String db) {
        tableHeader.setText("数据库 `" + db + "` 的对象列表");
        String sql = "SHOW FULL TABLES FROM `" + esc(db) + "`";
        host.session().query(sql, new SqlSession.Callback<QueryResult>() {
            @Override
            public void done(QueryResult r) {
                if (r == null) return;
                if (!r.isSuccess()) {
                    grid.showResult(r);
                    return;
                }
                renderTables(db, r.rows);
            }
        });
    }

    private void renderTables(final String db, List<List<String>> rows) {
        Context c = getContext();
        tableList.removeAllViews();
        tableHeader.setText("`" + db + "` · 共 " + rows.size() + " 个对象");

        if (rows.isEmpty()) {
            TextView empty = Ui.label(c, "该数据库暂无数据表，可在 SQL 工作台中建表", 13f, Ui.MUTED, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(Ui.dp(c, 16), Ui.dp(c, 22), Ui.dp(c, 16), Ui.dp(c, 22));
            tableList.addView(empty);
            return;
        }

        for (int i = 0; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            final String tName = !row.isEmpty() ? row.get(0) : "";
            final String tType = row.size() > 1 ? row.get(1) : "BASE TABLE";
            final boolean isView = tType != null && tType.toUpperCase().contains("VIEW");
            final boolean sel = tName.equals(selectedTable);

            LinearLayout item = new LinearLayout(c);
            item.setOrientation(LinearLayout.HORIZONTAL);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding(Ui.dp(c, 12), Ui.dp(c, 9), Ui.dp(c, 12), Ui.dp(c, 9));
            item.setClickable(true);
            item.setFocusable(true);
            Ui.hand(item);
            item.setBackground(rowDrawable(c, sel));

            IconView ic = new IconView(c, isView ? IconView.VIEW : IconView.TABLE,
                    sel ? Ui.ACCENT : (isView ? Ui.BLUE : Ui.TEXT2));
            item.addView(ic, Ui.lp(Ui.dp(c, 16), Ui.dp(c, 16)));

            TextView name = Ui.label(c, tName, 13.5f, Ui.TEXT, sel);
            name.setTypeface(Typeface.create(Typeface.MONOSPACE, sel ? Typeface.BOLD : Typeface.NORMAL));
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(0, -2, 1f);
            nlp.leftMargin = Ui.dp(c, 10);
            item.addView(name, nlp);

            TextView badge = Ui.label(c, isView ? "VIEW" : "TABLE", 10f,
                    isView ? Ui.BLUE : Ui.ACCENT, true);
            badge.setPadding(Ui.dp(c, 6), Ui.dp(c, 2), Ui.dp(c, 6), Ui.dp(c, 2));
            badge.setBackground(Ui.rect(isView ? 0x182D5B88 : 0x18D97757, Ui.dpf(c, 5), 0, 0));
            item.addView(badge);

            item.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    selectedTable = tName;
                    selectedType = tType;
                    loadTables(db);
                    inspectSelectedTable();
                }
            });
            item.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    selectedTable = tName;
                    selectedType = tType;
                    showTableActions();
                    return true;
                }
            });
            tableList.addView(item, Ui.lpm(-1, -2, 0, 0, 0, Ui.dp(c, 5)));
        }

        if (selectedTable.isEmpty() && !rows.isEmpty()) {
            selectedTable = rows.get(0).get(0);
            selectedType = rows.get(0).size() > 1 ? rows.get(0).get(1) : "BASE TABLE";
            inspectSelectedTable();
            loadTables(db);
        }
    }

    private void inspectSelectedTable() {
        if (selectedDb.isEmpty() || selectedTable.isEmpty()) return;
        String fq = "`" + esc(selectedDb) + "`.`" + esc(selectedTable) + "`";
        rightTitle.setText(selectedDb + "." + selectedTable);
        String sql;
        switch (inspectMode) {
            case 1:
                sql = "DESCRIBE " + fq;
                break;
            case 2:
                sql = (selectedType != null && selectedType.toUpperCase().contains("VIEW")
                        ? "SHOW CREATE VIEW " : "SHOW CREATE TABLE ") + fq;
                break;
            case 3:
                sql = "SHOW INDEX FROM " + fq;
                break;
            case 0:
            default:
                sql = "SELECT * FROM " + fq + " LIMIT 200";
                break;
        }
        grid.showLoading();
        host.session().query(sql, new SqlSession.Callback<QueryResult>() {
            @Override
            public void done(QueryResult r) {
                if (r != null) {
                    grid.showResult(r);
                }
            }
        });
    }

    private void promptCreateDatabase() {
        Dialogs.prompt(getContext(), "新建数据库", "输入数据库名称 (例如 research_db)", "创建",
                new Dialogs.Pick<String>() {
                    @Override
                    public void picked(final String name) {
                        String sql = "CREATE DATABASE `" + esc(name) + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci";
                        host.session().query(sql, new SqlSession.Callback<QueryResult>() {
                            @Override
                            public void done(QueryResult r) {
                                if (r != null && r.isSuccess()) {
                                    host.toast("已创建数据库 " + name);
                                    selectedDb = name;
                                    selectedTable = "";
                                    reloadDatabases();
                                } else if (r != null) {
                                    host.toast(r.errorMessage);
                                }
                            }
                        });
                    }
                });
    }

    private void showDatabaseMenu(final String db, boolean sys) {
        String[] labels = sys
                ? new String[]{"切换到该库并打开工作台", "查看库字符集与排序规则"}
                : new String[]{"切换到该库并打开工作台", "查看库字符集与排序规则", "删除数据库 `" + db + "`"};
        int[] icons = sys
                ? new int[]{IconView.TERMINAL, IconView.INFO}
                : new int[]{IconView.TERMINAL, IconView.INFO, IconView.TRASH};
        Dialogs.actions(getContext(), "数据库 `" + db + "`", sys ? "系统内置数据库" : "用户数据库",
                labels, icons, new Dialogs.Pick<Integer>() {
                    @Override
                    public void picked(Integer idx) {
                        if (idx == 0) {
                            host.runInWorkbench("USE `" + esc(db) + "`;\nSHOW TABLES;", true);
                        } else if (idx == 1) {
                            host.runInWorkbench("SHOW CREATE DATABASE `" + esc(db) + "`;", true);
                        } else if (idx == 2) {
                            Dialogs.confirm(getContext(), "确认删除数据库",
                                    "即将执行 DROP DATABASE `" + db + "`，此操作不可恢复！", "删除", true,
                                    new Runnable() {
                                        @Override
                                        public void run() {
                                            host.session().query("DROP DATABASE `" + esc(db) + "`",
                                                    new SqlSession.Callback<QueryResult>() {
                                                        @Override
                                                        public void done(QueryResult r) {
                                                            host.toast("已删除数据库 " + db);
                                                            selectedDb = "";
                                                            selectedTable = "";
                                                            reloadDatabases();
                                                        }
                                                    });
                                        }
                                    });
                        }
                    }
                });
    }

    private void showTableActions() {
        if (selectedDb.isEmpty() || selectedTable.isEmpty()) {
            host.toast("请先选择一张表或视图");
            return;
        }
        final String fq = "`" + esc(selectedDb) + "`.`" + esc(selectedTable) + "`";
        final boolean isView = selectedType != null && selectedType.toUpperCase().contains("VIEW");
        String[] labels = {
                "统计总行数 (COUNT(*))",
                "在工作台生成 SELECT 模板",
                "在工作台生成 INSERT 模板",
                isView ? "删除视图 (DROP VIEW)" : "删除数据表 (DROP TABLE)"
        };
        int[] icons = {IconView.BOLT, IconView.TERMINAL, IconView.PLUS, IconView.TRASH};
        Dialogs.actions(getContext(), selectedDb + "." + selectedTable,
                isView ? "视图对象 (VIEW)" : "数据表对象 (TABLE)", labels, icons,
                new Dialogs.Pick<Integer>() {
                    @Override
                    public void picked(Integer idx) {
                        if (idx == 0) {
                            grid.showLoading();
                            host.session().query("SELECT COUNT(*) AS total_rows FROM " + fq,
                                    new SqlSession.Callback<QueryResult>() {
                                        @Override
                                        public void done(QueryResult r) {
                                            if (r != null) grid.showResult(r);
                                        }
                                    });
                        } else if (idx == 1) {
                            host.runInWorkbench("SELECT * FROM " + fq + " LIMIT 100;", true);
                        } else if (idx == 2) {
                            host.runInWorkbench("INSERT INTO " + fq + " VALUES ();", false);
                        } else if (idx == 3) {
                            final String dropSql = (isView ? "DROP VIEW " : "DROP TABLE ") + fq;
                            Dialogs.confirm(getContext(), "确认删除",
                                    "即将执行:\n" + dropSql, "确认删除", true, new Runnable() {
                                        @Override
                                        public void run() {
                                            host.session().query(dropSql, new SqlSession.Callback<QueryResult>() {
                                                @Override
                                                public void done(QueryResult r) {
                                                    host.toast("已删除 " + selectedTable);
                                                    selectedTable = "";
                                                    loadTables(selectedDb);
                                                }
                                            });
                                        }
                                    });
                        }
                    }
                });
    }

    private static String esc(String id) {
        return id == null ? "" : id.replace("`", "``");
    }

    private static Drawable chipDrawable(Context c, boolean sel, int accent) {
        float r = Ui.dpf(c, 8);
        int fill = sel ? Ui.alpha(accent, 34) : Ui.BG2;
        int stroke = sel ? accent : Ui.STROKE;
        StateListDrawable sld = new StateListDrawable();
        sld.addState(new int[]{android.R.attr.state_hovered},
                Ui.rect(Ui.alpha(accent, 28), r, accent, Math.max(1, Ui.dp(c, 1))));
        sld.addState(new int[]{android.R.attr.state_focused},
                Ui.rect(fill, r, Ui.ACCENT, Math.max(1, Ui.dp(c, 2))));
        sld.addState(new int[]{}, Ui.rect(fill, r, stroke, Math.max(1, Ui.dp(c, 1))));
        return new RippleDrawable(ColorStateList.valueOf(0x18141413), sld, Ui.rect(0xFFFFFFFF, r, 0, 0));
    }

    private static Drawable rowDrawable(Context c, boolean sel) {
        float r = Ui.dpf(c, 10);
        int fill = sel ? 0x1FD97757 : Ui.BG;
        int stroke = sel ? Ui.ACCENT : Ui.STROKE;
        StateListDrawable sld = new StateListDrawable();
        sld.addState(new int[]{android.R.attr.state_hovered},
                Ui.rect(0x16D97757, r, Ui.ACCENT, Math.max(1, Ui.dp(c, 1))));
        sld.addState(new int[]{android.R.attr.state_focused},
                Ui.rect(fill, r, Ui.ACCENT, Math.max(1, Ui.dp(c, 2))));
        sld.addState(new int[]{}, Ui.rect(fill, r, stroke, Math.max(1, Ui.dp(c, 1))));
        return new RippleDrawable(ColorStateList.valueOf(0x18141413), sld, Ui.rect(0xFFFFFFFF, r, 0, 0));
    }
}
