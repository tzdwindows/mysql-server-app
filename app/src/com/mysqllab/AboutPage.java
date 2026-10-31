package com.mysqllab;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Claude-style minimalist About page: Author credit (tzdwindows 7), architecture overview, keyboard & mouse guide. */
public class AboutPage extends Page {

    public static final String AUTHOR = "tzdwindows 7";

    private final LinearLayout content;
    private final LinearLayout columns;
    private final LinearLayout colLeft;
    private final LinearLayout colRight;
    private final Card heroCard;
    private final Card authorCard;
    private final Card shortcutCard;
    private final Card archCard;

    public AboutPage(final Context c, final AppHost host) {
        super(c, host);

        ScrollView sv = new ScrollView(c);
        sv.setFillViewport(true);
        sv.setVerticalScrollBarEnabled(false);
        addView(sv, new LayoutParams(-1, -1));

        content = new LinearLayout(c);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, 0, 0, Ui.dp(c, 16));
        sv.addView(content, new ViewGroup.LayoutParams(-1, -2));

        columns = new LinearLayout(c);
        colLeft = new LinearLayout(c);
        colLeft.setOrientation(LinearLayout.VERTICAL);
        colRight = new LinearLayout(c);
        colRight.setOrientation(LinearLayout.VERTICAL);
        columns.addView(colLeft);
        columns.addView(colRight);
        content.addView(columns, new LinearLayout.LayoutParams(-1, -2));

        // ---- 1. Hero App Card
        heroCard = new Card(c);
        heroCard.setGravity(Gravity.CENTER_HORIZONTAL);
        heroCard.setPadding(Ui.dp(c, 20), Ui.dp(c, 22), Ui.dp(c, 20), Ui.dp(c, 20));

        LinearLayout badgeCircle = new LinearLayout(c);
        badgeCircle.setGravity(Gravity.CENTER);
        badgeCircle.setBackground(Ui.rect(0x1FD97757, Ui.dpf(c, 18), 0x55D97757, Math.max(1, Ui.dp(c, 1))));
        badgeCircle.addView(new IconView(c, IconView.DATABASE, Ui.ACCENT), Ui.lp(Ui.dp(c, 32), Ui.dp(c, 32)));
        heroCard.addView(badgeCircle, Ui.lp(Ui.dp(c, 64), Ui.dp(c, 64)));

        TextView appTitle = Ui.label(c, "MySQL 8.0 实验研究室", 21f, Ui.TEXT, true);
        appTitle.setGravity(Gravity.CENTER);
        heroCard.addView(appTitle, Ui.lpm(-2, -2, 0, Ui.dp(c, 12), 0, 0));

        TextView appSub = Ui.label(c, "Android 原生 MySQL 8.0.40 独立服务器与简约工作台", 13f, Ui.TEXT2, false);
        appSub.setGravity(Gravity.CENTER);
        heroCard.addView(appSub, Ui.lpm(-2, -2, 0, Ui.dp(c, 6), 0, Ui.dp(c, 14)));

        LinearLayout tags = new LinearLayout(c);
        tags.setOrientation(LinearLayout.HORIZONTAL);
        tags.setGravity(Gravity.CENTER);
        tags.addView(makeBadge(c, "MySQL 8.0.40", Ui.ACCENT), Ui.lpm(-2, -2, Ui.dp(c, 4), 0, Ui.dp(c, 4), 0));
        tags.addView(makeBadge(c, "arm64-v8a", Ui.BLUE), Ui.lpm(-2, -2, Ui.dp(c, 4), 0, Ui.dp(c, 4), 0));
        tags.addView(makeBadge(c, "InnoDB", Ui.GREEN), Ui.lpm(-2, -2, Ui.dp(c, 4), 0, Ui.dp(c, 4), 0));
        heroCard.addView(tags);

        // ---- 2. Prominent Author Card (作者：tzdwindows 7)
        authorCard = new Card(c);
        LinearLayout aHdr = Card.headerRow(c, IconView.USER, Ui.ACCENT, "关于作者");
        authorCard.addView(aHdr);

        LinearLayout authorBanner = new LinearLayout(c);
        authorBanner.setOrientation(LinearLayout.HORIZONTAL);
        authorBanner.setGravity(Gravity.CENTER_VERTICAL);
        int pad = Ui.dp(c, 14);
        authorBanner.setPadding(pad, pad, pad, pad);
        authorBanner.setBackground(Ui.rect(Ui.BG, Ui.dpf(c, 12), Ui.STROKE, Math.max(1, Ui.dp(c, 1))));
        authorBanner.setClickable(true);
        Ui.hand(authorBanner);

        LinearLayout avatar = new LinearLayout(c);
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackground(Ui.rect(Ui.ACCENT, Ui.dpf(c, 12), 0, 0));
        avatar.addView(new IconView(c, IconView.USER, 0xFFFFFFFF), Ui.lp(Ui.dp(c, 24), Ui.dp(c, 24)));
        authorBanner.addView(avatar, Ui.lp(Ui.dp(c, 46), Ui.dp(c, 46)));

        LinearLayout aInfo = new LinearLayout(c);
        aInfo.setOrientation(LinearLayout.VERTICAL);
        TextView aLabel = Ui.label(c, "开发者 / 作者 (Author)", 11.5f, Ui.ACCENT, true);
        TextView aName = Ui.label(c, "作者：" + AUTHOR, 18f, Ui.TEXT, true);
        aName.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        TextView aDesc = Ui.label(c, "移动端原生数据库移植 · 内核交叉编译 · 简约交互设计", 12f, Ui.TEXT2, false);
        aInfo.addView(aLabel);
        aInfo.addView(aName, Ui.lpm(-2, -2, 0, Ui.dp(c, 2), 0, Ui.dp(c, 2)));
        aInfo.addView(aDesc);

        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(0, -2, 1f);
        alp.leftMargin = Ui.dp(c, 12);
        authorBanner.addView(aInfo, alp);

        FancyButton copyAuthor = new FancyButton(c, "复制", IconView.COPY, FancyButton.SECONDARY);
        copyAuthor.setCompact(true);
        copyAuthor.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Dialogs.copy(c, "author", AUTHOR);
                host.toast("已复制 作者：" + AUTHOR);
            }
        });
        authorBanner.addView(copyAuthor, Ui.lpm(-2, Ui.dp(c, 32), Ui.dp(c, 8), 0, 0, 0));
        authorBanner.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                host.toast("作者：" + AUTHOR + " · 感谢使用 MySQL 8.0 实验研究室！");
            }
        });
        authorCard.addView(authorBanner, Ui.lpm(-1, -2, 0, Ui.dp(c, 10), 0, 0));

        // ---- 3. Keyboard & Mouse Support Card
        shortcutCard = new Card(c);
        shortcutCard.addView(Card.headerRow(c, IconView.KEYBOARD, Ui.ACCENT, "表格操作与外接键鼠指南"));
        TextView kmIntro = Ui.label(c,
                "全面支持手机触控、平板横屏分栏模式，以及外接物理键盘与鼠标操作：",
                13f, Ui.TEXT2, false);
        kmIntro.setLineSpacing(0, 1.25f);
        shortcutCard.addView(kmIntro, Ui.lpm(-1, -2, 0, Ui.dp(c, 8), 0, Ui.dp(c, 10)));

        String[][] keys = {
                {"轻触表格单元格", "立即复制该单元格内容到剪贴板"},
                {"长按表格单元格", "复制内容并打开该行完整字段详情面板"},
                {"右上角「放大」", "一键进入 100% 全屏表格浏览视图"},
                {"Ctrl + Enter / F5", "立即运行选中或全部 SQL 脚本"},
                {"Tab / Ctrl + /", "插入 4 空格缩进 / 切换当前行 `--` 注释"},
                {"Ctrl + L / H", "清空 SQL 编辑器 / 打开执行历史"},
                {"Ctrl + 1 ~ 4", "在「服务器 / 工作台 / 数据浏览 / 关于」间切换"},
                {"Esc / 返回键", "关闭全屏表格视图或当前弹窗"},
        };
        for (int i = 0; i < keys.length; i++) {
            shortcutCard.addView(makeKeyRow(c, keys[i][0], keys[i][1], i % 2 == 0),
                    Ui.lpm(-1, -2, 0, 0, 0, Ui.dp(c, 5)));
        }

        // ---- 4. Technical Architecture & Full SQL Demo Card
        archCard = new Card(c);
        archCard.addView(Card.headerRow(c, IconView.CHIP, Ui.GREEN, "架构特性与全语法支持"));

        String[][] specs = {
                {"开发者署名", "tzdwindows 7"},
                {"内核版本", "Oracle MySQL Community Server 8.0.40 (Official Source)"},
                {"二进制架构", "Android NDK r27c · aarch64-linux-android29 (ELF 64-bit PIE)"},
                {"通信协议", "MySQL 4.1 Native Wire Protocol (127.0.0.1:3306 持久会话)"},
                {"支持特性", "InnoDB 事务、外键、窗口函数、递归 CTE、JSON、存储过程、触发器、视图、EXPLAIN ANALYZE"},
                {"当前设备", Build.MANUFACTURER + " " + Build.MODEL + " (Android " + Build.VERSION.RELEASE + ", API " + Build.VERSION.SDK_INT + ")"},
        };
        for (int i = 0; i < specs.length; i++) {
            archCard.addView(makeSpecRow(c, specs[i][0], specs[i][1]),
                    Ui.lpm(-1, -2, 0, Ui.dp(c, 6), 0, 0));
        }

        FancyButton demoBtn = new FancyButton(c, "在工作台运行作者演示 SQL", IconView.PLAY, FancyButton.PRIMARY);
        demoBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String sql = "-- MySQL 8.0 实验研究室 · 作者: tzdwindows 7\n"
                        + "SELECT 'tzdwindows 7' AS author,\n"
                        + "       VERSION() AS mysql_version,\n"
                        + "       @@version_compile_os AS target_os,\n"
                        + "       @@version_compile_machine AS arch,\n"
                        + "       NOW() AS current_time;";
                host.runInWorkbench(sql, true);
            }
        });
        archCard.addView(demoBtn, Ui.lpm(-1, Ui.dp(c, 42), 0, Ui.dp(c, 14), 0, 0));

        applyLayout(false);
    }

    @Override
    public void setWide(boolean wide) {
        if (this.wide == wide && colLeft.getChildCount() + colRight.getChildCount() > 0) return;
        super.setWide(wide);
        applyLayout(wide);
    }

    private void applyLayout(boolean wide) {
        Context c = getContext();
        int gap = Ui.dp(c, 10);
        colLeft.removeAllViews();
        colRight.removeAllViews();

        if (wide) {
            columns.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams lpL = new LinearLayout.LayoutParams(0, -2, 1f);
            lpL.setMargins(0, 0, gap / 2, 0);
            LinearLayout.LayoutParams lpR = new LinearLayout.LayoutParams(0, -2, 1.15f);
            lpR.setMargins(gap / 2, 0, 0, 0);
            colLeft.setLayoutParams(lpL);
            colRight.setLayoutParams(lpR);

            colLeft.addView(heroCard, Ui.lpm(-1, -2, 0, 0, 0, gap));
            colLeft.addView(authorCard, Ui.lpm(-1, -2, 0, 0, 0, gap));
            colLeft.addView(archCard, Ui.lpm(-1, -2, 0, 0, 0, 0));

            colRight.addView(shortcutCard, Ui.lpm(-1, -2, 0, 0, 0, 0));
        } else {
            columns.setOrientation(LinearLayout.VERTICAL);
            colLeft.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
            colRight.setLayoutParams(new LinearLayout.LayoutParams(-1, 0));

            colLeft.addView(heroCard, Ui.lpm(-1, -2, 0, 0, 0, gap));
            colLeft.addView(authorCard, Ui.lpm(-1, -2, 0, 0, 0, gap));
            colLeft.addView(shortcutCard, Ui.lpm(-1, -2, 0, 0, 0, gap));
            colLeft.addView(archCard, Ui.lpm(-1, -2, 0, 0, 0, 0));
        }
    }

    @Override
    public void onShown() {
        Ui.reveal(colLeft, 0);
        if (wide) Ui.reveal(colRight, 80);
    }

    private static TextView makeBadge(Context c, String text, int color) {
        TextView t = Ui.label(c, text, 11.5f, color, true);
        t.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        t.setPadding(Ui.dp(c, 10), Ui.dp(c, 4), Ui.dp(c, 10), Ui.dp(c, 4));
        t.setBackground(Ui.rect(Ui.alpha(color, 26), Ui.dpf(c, 999),
                Ui.alpha(color, 80), Math.max(1, Ui.dp(c, 1))));
        return t;
    }

    private static LinearLayout makeKeyRow(Context c, String key, String desc, boolean alt) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(c, 10), Ui.dp(c, 8), Ui.dp(c, 10), Ui.dp(c, 8));
        row.setBackground(Ui.rect(alt ? Ui.BG : Ui.CARD, Ui.dpf(c, 8), Ui.STROKE, 1));

        TextView k = Ui.label(c, key, 12f, Ui.ACCENT, true);
        k.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        k.setPadding(Ui.dp(c, 8), Ui.dp(c, 3), Ui.dp(c, 8), Ui.dp(c, 3));
        k.setBackground(Ui.rect(0x18D97757, Ui.dpf(c, 6), 0x44D97757, 1));
        row.addView(k, new LinearLayout.LayoutParams(-2, -2));

        TextView d = Ui.label(c, desc, 12.5f, Ui.TEXT, false);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(0, -2, 1f);
        dlp.leftMargin = Ui.dp(c, 10);
        row.addView(d, dlp);
        return row;
    }

    private static LinearLayout makeSpecRow(Context c, String label, String val) {
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setPadding(Ui.dp(c, 12), Ui.dp(c, 8), Ui.dp(c, 12), Ui.dp(c, 8));
        r.setBackground(Ui.rect(Ui.BG, Ui.dpf(c, 8), Ui.STROKE, 1));
        TextView k = Ui.label(c, label, 11.5f, Ui.TEXT2, true);
        TextView v = Ui.label(c, val, 12.5f, Ui.TEXT, false);
        v.setTypeface(Typeface.MONOSPACE);
        r.addView(k);
        r.addView(v, Ui.lpm(-1, -2, 0, Ui.dp(c, 2), 0, 0));
        return r;
    }
}
