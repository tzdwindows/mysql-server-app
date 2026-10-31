package com.mysqllab;

import android.animation.ValueAnimator;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.NumberFormat;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Claude-style minimalist server dashboard: status emblem, controls, live metrics, connection info and log console. */
public class ServerPage extends Page {

    private final StatusOrb orb;
    private final TextView title;
    private final TextView sub;
    private final FancyButton startBtn;
    private final FancyButton stopBtn;
    private final FancyButton initBtn;
    private final Card hero;
    private final LinearLayout metrics;
    private final Card info;
    private final Card logCard;
    private final TextView logView;
    private final ScrollView logScroll;
    private final TextView vUptime;
    private final TextView vThreads;
    private final TextView vQuestions;
    private final TextView vQps;
    private final TextView vVersion;
    private final SparkView spark;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService poller = Executors.newSingleThreadExecutor();
    private final NumberFormat nf = NumberFormat.getIntegerInstance();
    private boolean polling;
    private long prevQuestions = -1;
    private long prevTime;
    private long shownThreads;
    private long shownQuestions;
    private ServerState state = ServerState.STOPPED;

    private final Runnable pollTick = new Runnable() {
        @Override
        public void run() {
            if (state != ServerState.RUNNING) {
                polling = false;
                return;
            }
            if (isShown()) {
                poller.execute(new Runnable() {
                    @Override
                    public void run() {
                        final List<QueryResult> rs = SqlSession.oneShot(
                                "SHOW GLOBAL STATUS WHERE Variable_name IN ('Uptime','Threads_connected','Questions')",
                                "SELECT VERSION()");
                        main.post(new Runnable() {
                            @Override
                            public void run() {
                                applyMetrics(rs);
                            }
                        });
                    }
                });
            }
            main.postDelayed(this, 2500);
        }
    };

    public ServerPage(final Context c, final AppHost host) {
        super(c, host);

        // ---- hero
        hero = new Card(c);
        hero.setGravity(Gravity.CENTER_HORIZONTAL);
        hero.setPadding(Ui.dp(c, 18), Ui.dp(c, 20), Ui.dp(c, 18), Ui.dp(c, 18));
        orb = new StatusOrb(c);
        hero.addView(orb, Ui.lp(Ui.dp(c, 96), Ui.dp(c, 96)));
        title = Ui.label(c, "已停止", 22, Ui.TEXT, true);
        title.setGravity(Gravity.CENTER);
        hero.addView(title, Ui.lpm(-2, -2, 0, Ui.dp(c, 10), 0, 0));
        sub = Ui.label(c, "", 13f, Ui.TEXT2, false);
        sub.setGravity(Gravity.CENTER);
        sub.setLineSpacing(0, 1.25f);
        hero.addView(sub, Ui.lpm(-2, -2, 0, Ui.dp(c, 4), 0, Ui.dp(c, 14)));

        LinearLayout btns = new LinearLayout(c);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        btns.setGravity(Gravity.CENTER);
        startBtn = new FancyButton(c, "启动服务器", IconView.PLAY, FancyButton.PRIMARY);
        startBtn.setTooltip("启动 mysqld (首次会自动初始化数据目录)");
        startBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                host.startServer();
            }
        });
        stopBtn = new FancyButton(c, "停止服务", IconView.STOP, FancyButton.DANGER);
        stopBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                host.stopServer();
            }
        });
        initBtn = new FancyButton(c, "重置", IconView.REFRESH, FancyButton.SECONDARY);
        initBtn.setTooltip("重置数据库：删除全部数据并重新初始化");
        initBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Dialogs.confirm(c, "重置数据库", "这会删除所有数据库与数据，并重新初始化一个全新的 MySQL 实例。此操作不可恢复。",
                        "确认重置", true, new Runnable() {
                            @Override
                            public void run() {
                                host.reinitServer();
                            }
                        });
            }
        });
        btns.addView(startBtn, Ui.lp(-2, Ui.dp(c, 40)));
        btns.addView(stopBtn, Ui.lpm(-2, Ui.dp(c, 40), Ui.dp(c, 10), 0, 0, 0));
        btns.addView(initBtn, Ui.lpm(-2, Ui.dp(c, 40), Ui.dp(c, 10), 0, 0, 0));
        hero.addView(btns, new LinearLayout.LayoutParams(-2, -2));

        // ---- metrics
        metrics = new LinearLayout(c);
        metrics.setOrientation(LinearLayout.VERTICAL);
        LinearLayout r1 = new LinearLayout(c);
        LinearLayout r2 = new LinearLayout(c);
        vUptime = Ui.label(c, "—", 20, Ui.TEXT, true);
        vThreads = Ui.label(c, "—", 20, Ui.TEXT, true);
        vQuestions = Ui.label(c, "—", 20, Ui.TEXT, true);
        vQps = Ui.label(c, "—", 20, Ui.TEXT, true);
        r1.addView(tile(c, IconView.HISTORY, Ui.ACCENT, "运行时间", vUptime, null),
                Ui.lpm(0, -2, 0, 0, Ui.dp(c, 5), 0));
        ((LinearLayout.LayoutParams) r1.getChildAt(0).getLayoutParams()).weight = 1f;
        r1.addView(tile(c, IconView.USER, Ui.BLUE, "当前连接", vThreads, null),
                Ui.lpm(0, -2, Ui.dp(c, 5), 0, 0, 0));
        ((LinearLayout.LayoutParams) r1.getChildAt(1).getLayoutParams()).weight = 1f;
        spark = new SparkView(c, Ui.GREEN);
        r2.addView(tile(c, IconView.CODE, Ui.VIOLET, "累计查询", vQuestions, null),
                Ui.lpm(0, -2, 0, 0, Ui.dp(c, 5), 0));
        ((LinearLayout.LayoutParams) r2.getChildAt(0).getLayoutParams()).weight = 1f;
        r2.addView(tile(c, IconView.BOLT, Ui.GREEN, "每秒查询 QPS", vQps, spark),
                Ui.lpm(0, -2, Ui.dp(c, 5), 0, 0, 0));
        ((LinearLayout.LayoutParams) r2.getChildAt(1).getLayoutParams()).weight = 1f;
        metrics.addView(r1, new LinearLayout.LayoutParams(-1, -2));
        metrics.addView(r2, Ui.lpm(-1, -2, 0, Ui.dp(c, 10), 0, 0));

        // ---- info
        info = new Card(c);
        info.addView(Card.headerRow(c, IconView.INFO, Ui.ACCENT, "连接信息"));
        vVersion = Ui.label(c, "—", 13f, Ui.TEXT, false);
        info.addView(infoRow(c, "版本", vVersion), Ui.lpm(-1, -2, 0, Ui.dp(c, 8), 0, 0));
        info.addView(infoRow(c, "地址", "127.0.0.1:3306"));
        info.addView(infoRow(c, "账号", "root（空密码）"));
        info.addView(infoRow(c, "架构", "arm64-v8a · Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")"));
        info.addView(infoRow(c, "引擎", "InnoDB · MySQL 8.0.40"));
        info.addView(infoRow(c, "数据目录", host.manager() == null ? "" : new java.io.File(c.getFilesDir(), "data").getAbsolutePath()));

        // ---- log
        logCard = new Card(c);
        LinearLayout lh = Card.headerRow(c, IconView.TERMINAL, Ui.ACCENT, "服务器日志");
        FancyButton copyLog = new FancyButton(c, "复制", IconView.COPY, FancyButton.SECONDARY);
        copyLog.setCompact(true);
        copyLog.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Dialogs.copy(c, "log", logView.getText().toString());
                host.toast("日志已复制");
            }
        });
        FancyButton clearLog = new FancyButton(c, "清空", IconView.TRASH, FancyButton.SECONDARY);
        clearLog.setCompact(true);
        clearLog.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                logView.setText("");
            }
        });
        lh.addView(copyLog, Ui.lpm(-2, Ui.dp(c, 30), 0, 0, Ui.dp(c, 6), 0));
        lh.addView(clearLog, Ui.lp(-2, Ui.dp(c, 30)));
        logCard.addView(lh);
        logScroll = new ScrollView(c);
        logScroll.setBackground(Ui.rect(0xFFF4F2EC, Ui.dpf(c, 10), Ui.STROKE, Math.max(1, Ui.dp(c, 1))));
        Ui.clipRound(logScroll, 10);
        logScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        logView = new TextView(c);
        logView.setTextSize(12f);
        logView.setTypeface(android.graphics.Typeface.MONOSPACE);
        logView.setTextColor(Ui.TEXT);
        logView.setTextIsSelectable(true);
        logView.setLineSpacing(0, 1.2f);
        logView.setPadding(Ui.dp(c, 12), Ui.dp(c, 10), Ui.dp(c, 12), Ui.dp(c, 10));
        logView.setHint("日志将显示在这里…");
        logView.setHintTextColor(Ui.MUTED);
        logScroll.addView(logView, new ScrollView.LayoutParams(-1, -2));
        logCard.addView(logScroll, Ui.lpm(-1, 0, 0, Ui.dp(c, 10), 0, 0));
        ((LinearLayout.LayoutParams) logScroll.getLayoutParams()).weight = 1f;

        applyState(ServerState.STOPPED, null, false);
        setWide(false);
    }

    private Card tile(Context c, int icon, int color, String label, TextView value, SparkView sp) {
        Card t = new Card(c);
        t.setPadding(Ui.dp(c, 14), Ui.dp(c, 12), Ui.dp(c, 14), Ui.dp(c, 12));
        LinearLayout top = new LinearLayout(c);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(new IconView(c, icon, color), Ui.lp(Ui.dp(c, 15), Ui.dp(c, 15)));
        top.addView(Ui.label(c, label, 12, Ui.TEXT2, false), Ui.lpm(-2, -2, Ui.dp(c, 6), 0, 0, 0));
        t.addView(top);
        t.addView(value, Ui.lpm(-2, -2, 0, Ui.dp(c, 6), 0, 0));
        if (sp != null) {
            t.addView(sp, Ui.lpm(-1, Ui.dp(c, 26), 0, Ui.dp(c, 4), 0, 0));
        } else {
            t.addView(new View(c), Ui.lp(1, Ui.dp(c, 30)));
        }
        return t;
    }

    private View infoRow(final Context c, String key, String val) {
        TextView v = Ui.label(c, val, 13f, Ui.TEXT, false);
        return infoRow(c, key, v);
    }

    private View infoRow(final Context c, String key, final TextView valueView) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(c, 8), Ui.dp(c, 8), Ui.dp(c, 8), Ui.dp(c, 8));
        float r = Ui.dpf(c, 8);
        row.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x18141413), Ui.rect(0x00000000, r, 0, 0),
                Ui.rect(0xFFFFFFFF, r, 0, 0)));
        row.setClickable(true);
        row.setFocusable(true);
        Ui.hand(row);
        row.setTooltipText("点击复制");
        TextView k = Ui.label(c, key, 12.5f, Ui.TEXT2, false);
        row.addView(k, new LinearLayout.LayoutParams(Ui.dp(c, 68), -2));
        valueView.setGravity(Gravity.END);
        valueView.setTextIsSelectable(false);
        row.addView(valueView, new LinearLayout.LayoutParams(0, -2, 1f));
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Dialogs.copy(c, "info", valueView.getText().toString());
                host.toast("已复制: " + valueView.getText());
            }
        });
        return row;
    }

    @Override
    public void setWide(boolean w) {
        super.setWide(w);
        Context c = getContext();
        removeAllViews();
        detach(hero);
        detach(metrics);
        detach(info);
        detach(logCard);
        int pad = Ui.dp(c, w ? 14 : 10);
        int gap = Ui.dp(c, 10);
        if (w) {
            LinearLayout row = new LinearLayout(c);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(pad, pad, pad, pad);
            ScrollView left = new ScrollView(c);
            left.setVerticalScrollBarEnabled(false);
            left.setOverScrollMode(View.OVER_SCROLL_NEVER);
            LinearLayout col = new LinearLayout(c);
            col.setOrientation(LinearLayout.VERTICAL);
            col.addView(hero, new LinearLayout.LayoutParams(-1, -2));
            col.addView(metrics, Ui.lpm(-1, -2, 0, gap, 0, 0));
            col.addView(info, Ui.lpm(-1, -2, 0, gap, 0, 0));
            left.addView(col, new ScrollView.LayoutParams(-1, -2));
            row.addView(left, Ui.lpm(0, -1, 0, 0, gap, 0));
            ((LinearLayout.LayoutParams) left.getLayoutParams()).weight = 1f;
            row.addView(logCard, new LinearLayout.LayoutParams(0, -1, 1.15f));
            addView(row, new LayoutParams(-1, -1));
        } else {
            ScrollView sv = new ScrollView(c);
            sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
            LinearLayout col = new LinearLayout(c);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setPadding(pad, pad, pad, pad);
            col.addView(hero, new LinearLayout.LayoutParams(-1, -2));
            col.addView(metrics, Ui.lpm(-1, -2, 0, gap, 0, 0));
            col.addView(info, Ui.lpm(-1, -2, 0, gap, 0, 0));
            col.addView(logCard, Ui.lpm(-1, Ui.dp(c, 300), 0, gap, 0, 0));
            sv.addView(col, new ScrollView.LayoutParams(-1, -2));
            addView(sv, new LayoutParams(-1, -1));
        }
    }

    private static void detach(View v) {
        if (v.getParent() instanceof android.view.ViewGroup) {
            ((android.view.ViewGroup) v.getParent()).removeView(v);
        }
    }

    @Override
    public void onShown() {
        View first = getChildAt(0);
        if (first instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) first;
            if (g instanceof ScrollView && g.getChildCount() > 0 && g.getChildAt(0) instanceof android.view.ViewGroup) {
                Ui.reveal((android.view.ViewGroup) g.getChildAt(0), 0);
            } else {
                Ui.reveal(g, 0);
            }
        }
    }

    @Override
    public void onServerState(ServerState s, String message) {
        applyState(s, message, true);
    }

    private void applyState(ServerState s, String message, boolean animate) {
        boolean changed = s != state;
        state = s;
        String t;
        int color;
        int kind;
        String subText;
        if (s == ServerState.RUNNING) {
            t = "运行中";
            color = Ui.GREEN;
            kind = StatusOrb.RUNNING;
            subText = "127.0.0.1:3306 · root 空密码\n可在「工作台」执行任意 SQL 语句";
        } else if (s == ServerState.STARTING) {
            t = "启动中";
            color = Ui.ACCENT;
            kind = StatusOrb.BUSY;
            subText = message != null ? message : "正在启动 mysqld…";
        } else if (s == ServerState.INITIALIZING) {
            t = "初始化中";
            color = Ui.BLUE;
            kind = StatusOrb.BUSY;
            subText = "首次运行需要创建数据字典，请稍候…";
        } else if (s == ServerState.ERROR) {
            t = "出错";
            color = Ui.RED;
            kind = StatusOrb.ERROR;
            subText = message != null ? message : "发生错误，请查看日志";
        } else {
            t = "已停止";
            color = Ui.MUTED;
            kind = StatusOrb.STOPPED;
            subText = "点击「启动服务器」运行 MySQL 8.0\narm64 原生 · 端口 3306";
        }
        title.setText(t);
        title.setTextColor(color == Ui.MUTED ? Ui.TEXT : color);
        sub.setText(subText);
        orb.setKind(kind, color);

        if (s == ServerState.RUNNING) {
            startBtn.setVisibility(View.GONE);
            stopBtn.setVisibility(View.VISIBLE);
            stopBtn.setEnabled(true);
            initBtn.setEnabled(false);
        } else if (s == ServerState.STARTING || s == ServerState.INITIALIZING) {
            startBtn.setVisibility(View.VISIBLE);
            startBtn.setEnabled(false);
            stopBtn.setVisibility(View.GONE);
            initBtn.setEnabled(false);
        } else {
            startBtn.setVisibility(View.VISIBLE);
            startBtn.setEnabled(true);
            stopBtn.setVisibility(View.GONE);
            initBtn.setEnabled(true);
        }
        if (animate && changed) {
            Ui.pop(title);
        }
        if (s == ServerState.RUNNING) {
            if (!polling) {
                polling = true;
                main.removeCallbacks(pollTick);
                main.post(pollTick);
            }
        } else {
            vUptime.setText("—");
            vThreads.setText("—");
            vQuestions.setText("—");
            vQps.setText("—");
            vVersion.setText("—");
            prevQuestions = -1;
        }
    }

    private void applyMetrics(List<QueryResult> rs) {
        if (state != ServerState.RUNNING || rs.isEmpty() || !rs.get(0).isSuccess()) return;
        long uptime = -1, threads = -1, questions = -1;
        for (List<String> row : rs.get(0).rows) {
            if (row.size() < 2) continue;
            try {
                long v = Long.parseLong(row.get(0).length() > 0 ? row.get(1) : "0");
                if ("Uptime".equalsIgnoreCase(row.get(0))) uptime = v;
                else if ("Threads_connected".equalsIgnoreCase(row.get(0))) threads = v;
                else if ("Questions".equalsIgnoreCase(row.get(0))) questions = v;
            } catch (NumberFormatException ignored) {
            }
        }
        if (uptime >= 0) vUptime.setText(fmtUptime(uptime));
        if (threads >= 0) {
            animateNumber(vThreads, shownThreads, threads);
            shownThreads = threads;
        }
        if (questions >= 0) {
            animateNumber(vQuestions, shownQuestions, questions);
            shownQuestions = questions;
            long now = System.currentTimeMillis();
            if (prevQuestions >= 0 && now > prevTime) {
                float qps = (questions - prevQuestions) * 1000f / (now - prevTime);
                qps = Math.max(0f, qps - 0.8f);
                vQps.setText(String.format(java.util.Locale.US, "%.1f", qps));
                spark.push(qps);
            }
            prevQuestions = questions;
            prevTime = now;
        }
        if (rs.size() > 1 && rs.get(1).isSuccess() && !rs.get(1).rows.isEmpty()) {
            vVersion.setText(rs.get(1).rows.get(0).get(0));
        }
    }

    private void animateNumber(final TextView tv, long from, long to) {
        ValueAnimator a = ValueAnimator.ofFloat((float) from, (float) to);
        a.setDuration(500);
        a.setInterpolator(new DecelerateInterpolator(1.8f));
        a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator an) {
                tv.setText(nf.format((long) (float) (Float) an.getAnimatedValue()));
            }
        });
        a.start();
    }

    private static String fmtUptime(long s) {
        long d = s / 86400;
        long h = (s % 86400) / 3600;
        long m = (s % 3600) / 60;
        long sec = s % 60;
        if (d > 0) return d + "天 " + h + "时";
        return String.format(java.util.Locale.US, "%02d:%02d:%02d", h, m, sec);
    }

    @Override
    public void onLog(String line) {
        int color = Ui.TEXT2;
        String low = line.toLowerCase(java.util.Locale.US);
        if (low.contains("[error]") || low.contains("error") || low.contains("fatal")) color = Ui.RED;
        else if (low.contains("[warning]") || low.contains("warning")) color = Ui.AMBER;
        else if (line.startsWith("[Init]") || line.startsWith("[Server]") || line.startsWith("[Setup]")) color = Ui.ACCENT;
        SpannableString sp = new SpannableString(line + "\n");
        sp.setSpan(new ForegroundColorSpan(color), 0, sp.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        logView.append(sp);
        CharSequence t = logView.getText();
        if (t instanceof Editable && t.length() > 30000) {
            ((Editable) t).delete(0, 10000);
        }
        logScroll.post(new Runnable() {
            @Override
            public void run() {
                logScroll.scrollTo(0, logView.getHeight());
            }
        });
    }
}
