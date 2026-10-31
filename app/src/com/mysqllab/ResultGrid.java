package com.mysqllab;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Claude-style minimalist SQL result table with:
 * - True 2D scrolling (sticky column header + vertical ScrollView + HorizontalScrollView)
 * - Real interactive TextView cells:
 *     - Single tap on any cell -> immediately copies that cell's text to clipboard + Toast
 *     - Long press on any cell -> copies cell text + haptic feedback + opens Row Detail sheet
 * - Hardware-clipped 12dp rounded card border (zero square corner bleed).
 */
public class ResultGrid extends FrameLayout {

    public interface Host {
        void toast(String msg);
    }

    private static final Pattern NUM = Pattern.compile("^-?\\d+(\\.\\d+)?$");
    private static final int MAX_RENDER_ROWS = 250;

    /** Outer HorizontalScrollView that only intercepts clearly horizontal drags (dx > dy). */
    static class BiHorizontalScroll extends HorizontalScrollView {
        private float startX, startY;
        private final int slop;

        BiHorizontalScroll(Context c) {
            super(c);
            slop = ViewConfiguration.get(c).getScaledTouchSlop();
            setOverScrollMode(View.OVER_SCROLL_NEVER);
            setHorizontalScrollBarEnabled(true);
            setFillViewport(true);
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent ev) {
            int action = ev.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                startX = ev.getX();
                startY = ev.getY();
                super.onInterceptTouchEvent(ev);
                return false;
            }
            if (action == MotionEvent.ACTION_MOVE) {
                float dx = Math.abs(ev.getX() - startX);
                float dy = Math.abs(ev.getY() - startY);
                if (dy > dx) {
                    return false;
                }
                if (dx > slop && dx > dy) {
                    return true;
                }
                return false;
            }
            return super.onInterceptTouchEvent(ev);
        }
    }

    /** Inner Vertical ScrollView that claims vertical drags so parent HorizontalScrollView never steals them. */
    static class BiVerticalScroll extends ScrollView {
        private float startX, startY;
        private final int slop;

        BiVerticalScroll(Context c) {
            super(c);
            slop = ViewConfiguration.get(c).getScaledTouchSlop();
            setOverScrollMode(View.OVER_SCROLL_NEVER);
            setVerticalScrollBarEnabled(true);
            setFillViewport(true);
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent ev) {
            int action = ev.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                startX = ev.getX();
                startY = ev.getY();
                super.onInterceptTouchEvent(ev);
                return false;
            }
            if (action == MotionEvent.ACTION_MOVE) {
                float dx = Math.abs(ev.getX() - startX);
                float dy = Math.abs(ev.getY() - startY);
                if (dy > slop && dy >= dx) {
                    if (getParent() != null) {
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    return true;
                }
                return false;
            }
            return super.onInterceptTouchEvent(ev);
        }
    }

    private final Host host;
    private final BiHorizontalScroll hs;
    private final LinearLayout tableWrap;
    private final LinearLayout headerRow;
    private final BiVerticalScroll vs;
    private final LinearLayout bodyCol;

    private final ScrollView msgScroll;
    private final LinearLayout msgBox;
    private final IconView msgIcon;
    private final TextView msgTitle;
    private final TextView msgDetail;
    private final ProgressBar spinner;

    private QueryResult current;
    private String[] cols = new String[0];
    private String[][] data = new String[0][];
    private int[] natural = new int[0];
    private int[] widths = new int[0];
    private boolean[] numeric = new boolean[0];

    public ResultGrid(Context c, Host host) {
        super(c);
        this.host = host;

        int bPad = Math.max(1, Ui.dp(c, 1));
        setPadding(bPad, bPad, bPad, bPad);
        setBackground(Ui.rect(Ui.CARD, Ui.dpf(c, 12), Ui.STROKE, bPad));
        Ui.clipRound(this, 12);

        hs = new BiHorizontalScroll(c);
        addView(hs, new FrameLayout.LayoutParams(-1, -1));

        tableWrap = new LinearLayout(c);
        tableWrap.setOrientation(LinearLayout.VERTICAL);
        hs.addView(tableWrap, new FrameLayout.LayoutParams(-2, -1));

        headerRow = new LinearLayout(c);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setBackgroundColor(0xFFF4F2EC);
        tableWrap.addView(headerRow, new LinearLayout.LayoutParams(-2, -2));

        View headerDiv = new View(c);
        headerDiv.setBackgroundColor(Ui.STROKE);
        tableWrap.addView(headerDiv, new LinearLayout.LayoutParams(-1, Math.max(1, Ui.dp(c, 1))));

        vs = new BiVerticalScroll(c);
        bodyCol = new LinearLayout(c);
        bodyCol.setOrientation(LinearLayout.VERTICAL);
        vs.addView(bodyCol, new FrameLayout.LayoutParams(-2, -2));
        tableWrap.addView(vs, new LinearLayout.LayoutParams(-2, 0, 1f));

        msgScroll = new ScrollView(c);
        msgScroll.setFillViewport(true);
        msgScroll.setVerticalScrollBarEnabled(false);
        msgScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);

        msgBox = new LinearLayout(c);
        msgBox.setOrientation(LinearLayout.VERTICAL);
        msgBox.setGravity(Gravity.CENTER);
        int p = Ui.dp(c, 18);
        msgBox.setPadding(p, p, p, p);
        msgIcon = new IconView(c, IconView.TERMINAL, Ui.MUTED);
        msgBox.addView(msgIcon, Ui.lp(Ui.dp(c, 32), Ui.dp(c, 32)));
        msgTitle = Ui.label(c, "", 15.5f, Ui.TEXT, true);
        msgTitle.setGravity(Gravity.CENTER);
        msgBox.addView(msgTitle, Ui.lpm(-2, -2, 0, Ui.dp(c, 8), 0, 0));
        msgDetail = new TextView(c);
        msgDetail.setTextSize(13f);
        msgDetail.setTextColor(Ui.TEXT2);
        msgDetail.setGravity(Gravity.CENTER);
        msgDetail.setLineSpacing(Ui.dpf(c, 3), 1f);
        msgDetail.setIncludeFontPadding(true);
        msgDetail.setTextIsSelectable(true);
        msgBox.addView(msgDetail, Ui.lpm(-2, -2, 0, Ui.dp(c, 6), 0, 0));

        spinner = new ProgressBar(c);
        spinner.setVisibility(View.GONE);
        msgBox.addView(spinner, Ui.lpm(Ui.dp(c, 28), Ui.dp(c, 28), 0, Ui.dp(c, 12), 0, 0));

        msgScroll.addView(msgBox, new FrameLayout.LayoutParams(-1, -1));
        addView(msgScroll, new FrameLayout.LayoutParams(-1, -1));

        showMessage(IconView.TERMINAL, Ui.MUTED, "等待执行",
                "编写 SQL 后点击「运行」\n轻触任意单元格立即复制 · 长按单元格查看整行详情");
    }

    public void showLoading() {
        hs.setVisibility(View.GONE);
        msgScroll.setVisibility(View.VISIBLE);
        msgIcon.setVisibility(View.GONE);
        msgTitle.setText("正在执行查询…");
        msgTitle.setTextColor(Ui.ACCENT);
        msgDetail.setText("正在与本地 MySQL 8.0 交互，请稍候");
        spinner.setVisibility(View.VISIBLE);
    }

    public void showMessage(int icon, int color, String title, String detail) {
        current = null;
        hs.setVisibility(View.GONE);
        msgScroll.setVisibility(View.VISIBLE);
        spinner.setVisibility(View.GONE);
        msgIcon.setVisibility(View.VISIBLE);
        msgIcon.setIcon(icon);
        msgIcon.setColor(color);
        msgTitle.setText(title);
        msgTitle.setTextColor(color == Ui.MUTED || color == Ui.TEXT2 ? Ui.TEXT : color);
        msgDetail.setText(detail);
    }

    public void showResult(QueryResult r) {
        if (!r.isSuccess()) {
            String m = (r.errorCode > 0 ? "ERROR " + r.errorCode + "\n" : "") + r.errorMessage;
            showMessage(IconView.ALERT, Ui.RED, "执行出错", m);
            return;
        }
        if (!r.isResultSet) {
            String d = r.message + (r.lastInsertId > 0 ? "\nLAST_INSERT_ID = " + r.lastInsertId : "");
            showMessage(IconView.CHECK, Ui.GREEN, "执行成功", d);
            return;
        }
        if (r.rows.isEmpty()) {
            showMessage(IconView.TABLE, Ui.AMBER, "结果为空 · 0 行", "列: " + join(r.columnNames));
            return;
        }
        current = r;
        msgScroll.setVisibility(View.GONE);
        hs.setVisibility(View.VISIBLE);
        prepare(r);
        computeWidths();
        rebuildTableViews();
        hs.scrollTo(0, 0);
        vs.scrollTo(0, 0);
    }

    private static String join(List<String> l) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < l.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(l.get(i));
        }
        return sb.toString();
    }

    private void prepare(QueryResult r) {
        Context c = getContext();
        int n = r.columnNames.size();
        cols = r.columnNames.toArray(new String[0]);
        data = new String[r.rows.size()][];
        numeric = new boolean[n];
        natural = new int[n];

        Paint hp = new Paint(Paint.ANTI_ALIAS_FLAG);
        hp.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        hp.setTextSize(Ui.sp(c, 14f));

        Paint mp = new Paint(Paint.ANTI_ALIAS_FLAG);
        mp.setTypeface(Typeface.MONOSPACE);
        mp.setTextSize(Ui.sp(c, 14f));

        int pad = Ui.dp(c, 28);
        int minW = Ui.dp(c, 108);
        int maxW = Ui.dp(c, 420);
        boolean[] allNum = new boolean[n];
        for (int i = 0; i < n; i++) {
            allNum[i] = true;
            natural[i] = (int) hp.measureText(cols[i]);
        }
        for (int ri = 0; ri < r.rows.size(); ri++) {
            List<String> row = r.rows.get(ri);
            String[] cells = new String[n];
            for (int i = 0; i < n; i++) {
                String v = i < row.size() ? row.get(i) : "";
                if (v == null) v = "NULL";
                if (v.length() > 300) v = v.substring(0, 300) + "…";
                v = v.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ');
                cells[i] = v;
                if (ri < 150) {
                    int w = (int) mp.measureText(v);
                    if (w > natural[i]) natural[i] = w;
                    if (!"NULL".equals(v) && !NUM.matcher(v).matches()) allNum[i] = false;
                }
            }
            data[ri] = cells;
        }
        for (int i = 0; i < n; i++) {
            numeric[i] = allNum[i];
            natural[i] = Math.max(minW, Math.min(maxW, natural[i] + pad));
        }
    }

    private void computeWidths() {
        if (cols.length == 0) return;
        int total = 0;
        for (int w : natural) total += w;
        int avail = getWidth() - getPaddingLeft() - getPaddingRight();
        widths = natural.clone();
        if (avail > total && cols.length > 0) {
            int extra = (avail - total) / cols.length;
            for (int i = 0; i < widths.length; i++) {
                widths[i] += extra;
            }
        }
    }

    private void rebuildTableViews() {
        final Context c = getContext();
        headerRow.removeAllViews();
        bodyCol.removeAllViews();
        if (cols.length == 0) return;

        int divW = Math.max(1, Ui.dp(c, 1));
        int hdrH = Ui.dp(c, 42);
        int rowH = Ui.dp(c, 42);
        int padH = Ui.dp(c, 12);

        // 1. Build Header Cells
        for (int i = 0; i < cols.length; i++) {
            final String colName = cols[i];
            TextView tv = new TextView(c);
            tv.setText(colName);
            tv.setTextSize(13.5f);
            tv.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
            tv.setTextColor(Ui.TEXT);
            tv.setSingleLine(true);
            tv.setEllipsize(TextUtils.TruncateAt.END);
            tv.setGravity((numeric[i] ? Gravity.END : Gravity.START) | Gravity.CENTER_VERTICAL);
            tv.setPadding(padH, 0, padH, 0);
            tv.setClickable(true);
            Ui.hand(tv);
            tv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Dialogs.copy(c, "column", colName);
                    if (host != null) host.toast("已复制列名: " + colName);
                }
            });
            headerRow.addView(tv, new LinearLayout.LayoutParams(widths[i], hdrH));

            if (i < cols.length - 1) {
                View vDiv = new View(c);
                vDiv.setBackgroundColor(Ui.STROKE);
                headerRow.addView(vDiv, new LinearLayout.LayoutParams(divW, -1));
            }
        }

        // 2. Build Data Rows (up to MAX_RENDER_ROWS)
        int limit = Math.min(data.length, MAX_RENDER_ROWS);
        for (int rIdx = 0; rIdx < limit; rIdx++) {
            final int rowIndex = rIdx;
            final String[] rowData = data[rIdx];

            LinearLayout rowLayout = new LinearLayout(c);
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            rowLayout.setBackgroundColor(rIdx % 2 == 1 ? 0xFFFAF9F5 : 0xFFFFFFFF);

            for (int cIdx = 0; cIdx < cols.length; cIdx++) {
                final int colIndex = cIdx;
                final String val = cIdx < rowData.length ? rowData[cIdx] : "";
                final String colName = cols[cIdx];
                boolean isNull = "NULL".equals(val);

                TextView cell = new TextView(c);
                cell.setText(val);
                cell.setTextSize(14f);
                cell.setTypeface(Typeface.create(Typeface.MONOSPACE, isNull ? Typeface.ITALIC : Typeface.NORMAL));
                cell.setTextColor(isNull ? Ui.MUTED : (numeric[cIdx] ? 0xFF9E5A1B : Ui.TEXT));
                cell.setSingleLine(true);
                cell.setEllipsize(TextUtils.TruncateAt.END);
                cell.setGravity((numeric[cIdx] ? Gravity.END : Gravity.START) | Gravity.CENTER_VERTICAL);
                cell.setPadding(padH, 0, padH, 0);
                cell.setClickable(true);
                cell.setLongClickable(true);
                cell.setBackground(new RippleDrawable(
                        ColorStateList.valueOf(0x22D97757), null, Ui.rect(0xFFFFFFFF, 0, 0, 0)));
                Ui.hand(cell);

                cell.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Dialogs.copy(c, colName, val);
                        if (host != null) {
                            String preview = val.length() > 36 ? val.substring(0, 36) + "…" : val;
                            host.toast("已复制 [" + colName + "]: " + preview);
                        }
                    }
                });

                cell.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                        Dialogs.copy(c, colName, val);
                        if (host != null) {
                            String preview = val.length() > 36 ? val.substring(0, 36) + "…" : val;
                            host.toast("已复制 [" + colName + "]: " + preview);
                        }
                        Dialogs.rowDetail(c, cols, rowData, rowIndex, host);
                        return true;
                    }
                });

                rowLayout.addView(cell, new LinearLayout.LayoutParams(widths[cIdx], rowH));

                if (cIdx < cols.length - 1) {
                    View vDiv = new View(c);
                    vDiv.setBackgroundColor(0xFFEDEAE2);
                    rowLayout.addView(vDiv, new LinearLayout.LayoutParams(divW, -1));
                }
            }

            bodyCol.addView(rowLayout, new LinearLayout.LayoutParams(-2, rowH));

            View hDiv = new View(c);
            hDiv.setBackgroundColor(0xFFEDEAE2);
            bodyCol.addView(hDiv, new LinearLayout.LayoutParams(-1, divW));
        }

        if (data.length > MAX_RENDER_ROWS) {
            TextView more = Ui.label(c, "已显示前 " + MAX_RENDER_ROWS + " 行（共 " + data.length + " 行），可点击「复制」导出完整 TSV",
                    12.5f, Ui.TEXT2, false);
            more.setPadding(padH, Ui.dp(c, 10), padH, Ui.dp(c, 10));
            bodyCol.addView(more, new LinearLayout.LayoutParams(-1, -2));
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        if (w != ow && cols.length > 0 && current != null) {
            computeWidths();
            post(new Runnable() {
                @Override
                public void run() {
                    rebuildTableViews();
                }
            });
        }
    }

    public boolean hasData() {
        return current != null && current.isResultSet && !current.rows.isEmpty();
    }

    public QueryResult getCurrentResult() {
        return current;
    }

    public String toTsv() {
        if (!hasData()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cols.length; i++) {
            if (i > 0) sb.append('\t');
            sb.append(cols[i]);
        }
        sb.append('\n');
        for (String[] row : data) {
            for (int i = 0; i < row.length; i++) {
                if (i > 0) sb.append('\t');
                sb.append(row[i]);
            }
            sb.append('\n');
        }
        return sb.toString();
    }
}
