package com.mysqllab;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.text.InputType;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * In-window overlay modal system & full-screen data viewer.
 * Renders directly inside MainActivity's root FrameLayout instead of android.app.Dialog,
 * completely eliminating OEM WindowManager dialog-squishing bugs and guaranteeing
 * 100% edge-to-edge full-screen table magnification.
 */
public final class Dialogs {
    private Dialogs() {}

    public interface Pick<T> {
        void picked(T value);
    }

    public interface Handle {
        void dismiss();
    }

    private static FrameLayout hostRoot;
    private static final List<View> activeOverlays = new ArrayList<>();

    public static void attachRoot(FrameLayout root) {
        hostRoot = root;
        activeOverlays.clear();
    }

    public static boolean dismissTop() {
        if (!activeOverlays.isEmpty()) {
            View top = activeOverlays.get(activeOverlays.size() - 1);
            dismissOverlay(top);
            return true;
        }
        return false;
    }

    private static void dismissOverlay(final View scrim) {
        if (scrim == null) return;
        activeOverlays.remove(scrim);
        InputMethodManager imm = (InputMethodManager) scrim.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(scrim.getWindowToken(), 0);
        }
        scrim.animate().alpha(0f).setDuration(140).withEndAction(new Runnable() {
            @Override
            public void run() {
                if (scrim.getParent() instanceof ViewGroup) {
                    ((ViewGroup) scrim.getParent()).removeView(scrim);
                }
            }
        }).start();
    }

    static class MaxScroll extends ScrollView {
        private final int max;

        MaxScroll(Context c, int max) {
            super(c);
            this.max = max;
        }

        @Override
        protected void onMeasure(int w, int h) {
            super.onMeasure(w, MeasureSpec.makeMeasureSpec(max, MeasureSpec.AT_MOST));
        }
    }

    public static void copy(Context c, String label, String text) {
        ClipboardManager cm = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText(label, text));
    }

    public static int getDialogWidth(Context ctx) {
        DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        int screenW = dm.widthPixels;
        if (hostRoot != null && hostRoot.getWidth() > 0) {
            screenW = hostRoot.getWidth();
        }
        return Math.min(screenW - Ui.dp(ctx, 32), Ui.dp(ctx, 420));
    }

    public static Handle show(Context ctx, final View content) {
        return showInternal(ctx, content, false);
    }

    private static Handle showInternal(Context ctx, final View content, boolean fullscreen) {
        if (hostRoot == null) {
            return new Handle() {
                @Override
                public void dismiss() {
                }
            };
        }

        final FrameLayout scrim = new FrameLayout(ctx);
        scrim.setBackgroundColor(fullscreen ? Ui.BG : 0x66141413);
        scrim.setClickable(true);
        scrim.setFocusable(true);
        scrim.setFocusableInTouchMode(true);

        final Handle handle = new Handle() {
            @Override
            public void dismiss() {
                dismissOverlay(scrim);
            }
        };

        if (!fullscreen) {
            scrim.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    handle.dismiss();
                }
            });
            content.setClickable(true);
            int width = getDialogWidth(ctx);
            FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
                    width, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
            int margin = Ui.dp(ctx, 16);
            clp.setMargins(margin, margin, margin, margin);
            scrim.addView(content, clp);
        } else {
            content.setClickable(true);
            scrim.addView(content, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }

        scrim.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent ev) {
                if ((keyCode == KeyEvent.KEYCODE_ESCAPE || keyCode == KeyEvent.KEYCODE_BACK)
                        && ev.getAction() == KeyEvent.ACTION_UP) {
                    handle.dismiss();
                    return true;
                }
                return false;
            }
        });

        hostRoot.addView(scrim, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        activeOverlays.add(scrim);

        scrim.setAlpha(0f);
        scrim.animate().alpha(1f).setDuration(160).start();
        if (!fullscreen) {
            content.setScaleX(0.95f);
            content.setScaleY(0.95f);
            content.animate().scaleX(1f).scaleY(1f).setDuration(180)
                    .setInterpolator(new DecelerateInterpolator(1.6f)).start();
        }
        return handle;
    }

    private static LinearLayout panel(Context c) {
        LinearLayout p = new LinearLayout(c);
        p.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(c, 20);
        p.setPadding(pad, pad, pad, Ui.dp(c, 18));
        p.setBackground(Ui.rect(Ui.CARD, Ui.dpf(c, 16), Ui.STROKE, Math.max(1, Ui.dp(c, 1))));
        Ui.clipRound(p, 16);
        return p;
    }

    private static LinearLayout titleRow(final Context c, final Handle[] ref, int icon, int iconColor, String title) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        IconView iv = new IconView(c, icon, iconColor);
        row.addView(iv, Ui.lp(Ui.dp(c, 20), Ui.dp(c, 20)));

        TextView t = Ui.label(c, title, 16f, Ui.TEXT, true);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tlp.leftMargin = Ui.dp(c, 10);
        row.addView(t, tlp);

        FancyButton close = new FancyButton(c, "", IconView.CLOSE, FancyButton.GHOST);
        close.setCompact(true);
        close.setTooltip("关闭 (Esc)");
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (ref[0] != null) ref[0].dismiss();
            }
        });
        row.addView(close, Ui.lp(Ui.dp(c, 34), Ui.dp(c, 34)));
        return row;
    }

    private static LinearLayout buttonRow(Context c) {
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        r.setPadding(0, Ui.dp(c, 18), 0, 0);
        return r;
    }

    public static void confirm(Context c, String title, String message, String okLabel, boolean danger,
                               final Runnable onOk) {
        final Handle[] ref = new Handle[1];
        LinearLayout p = panel(c);
        p.addView(titleRow(c, ref, danger ? IconView.ALERT : IconView.INFO, danger ? Ui.RED : Ui.ACCENT, title));

        TextView m = Ui.label(c, message, 14f, Ui.TEXT2, false);
        m.setLineSpacing(Ui.dpf(c, 4), 1f);
        p.addView(m, Ui.lpm(-1, -2, 0, Ui.dp(c, 12), 0, 0));

        LinearLayout row = buttonRow(c);
        FancyButton cancel = new FancyButton(c, "取消", -1, FancyButton.SECONDARY);
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (ref[0] != null) ref[0].dismiss();
            }
        });
        FancyButton ok = new FancyButton(c, okLabel, -1, danger ? FancyButton.DANGER : FancyButton.PRIMARY);
        ok.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (ref[0] != null) ref[0].dismiss();
                if (onOk != null) onOk.run();
            }
        });
        row.addView(cancel);
        row.addView(ok, Ui.lpm(-2, -2, Ui.dp(c, 10), 0, 0, 0));
        p.addView(row);

        ref[0] = show(c, p);
    }

    public static void prompt(final Context c, String title, String hint, String okLabel, final Pick<String> cb) {
        final Handle[] ref = new Handle[1];
        LinearLayout p = panel(c);
        p.addView(titleRow(c, ref, IconView.PLUS, Ui.ACCENT, title));

        final EditText et = new EditText(c);
        et.setHint(hint);
        et.setHintTextColor(Ui.MUTED);
        et.setTextColor(Ui.TEXT);
        et.setTextSize(14.5f);
        et.setTypeface(Typeface.MONOSPACE);
        et.setSingleLine(true);
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        et.setImeOptions(EditorInfo.IME_ACTION_DONE);
        et.setBackground(Ui.rect(Ui.BG2, Ui.dpf(c, 10), Ui.STROKE, Math.max(1, Ui.dp(c, 1))));
        et.setPadding(Ui.dp(c, 12), Ui.dp(c, 10), Ui.dp(c, 12), Ui.dp(c, 10));
        p.addView(et, Ui.lpm(-1, -2, 0, Ui.dp(c, 14), 0, 0));

        LinearLayout row = buttonRow(c);
        FancyButton cancel = new FancyButton(c, "取消", -1, FancyButton.SECONDARY);
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (ref[0] != null) ref[0].dismiss();
            }
        });

        final Runnable submit = new Runnable() {
            @Override
            public void run() {
                String v = et.getText().toString().trim();
                if (v.length() == 0) {
                    Ui.shake(et);
                    return;
                }
                if (ref[0] != null) ref[0].dismiss();
                if (cb != null) cb.picked(v);
            }
        };

        FancyButton ok = new FancyButton(c, okLabel, -1, FancyButton.PRIMARY);
        ok.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                submit.run();
            }
        });
        et.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                submit.run();
                return true;
            }
        });

        row.addView(cancel);
        row.addView(ok, Ui.lpm(-2, -2, Ui.dp(c, 10), 0, 0, 0));
        p.addView(row);

        ref[0] = show(c, p);
        et.requestFocus();
    }

    /**
     * Dedicated server & startup parameters configuration panel:
     * Configure host, port, username, password, and custom mysqld CLI startup arguments.
     */
    public static void serverConfig(final Context c, final Runnable onSaved) {
        final Handle[] ref = new Handle[1];
        LinearLayout p = panel(c);
        p.addView(titleRow(c, ref, IconView.SETTINGS, Ui.ACCENT, "服务器与启动配置"));

        TextView sub = Ui.label(c, "可配置服务运行地址、端口、用户密码及 mysqld 启动参数", 12.5f, Ui.TEXT2, false);
        p.addView(sub, Ui.lpm(-1, -2, 0, Ui.dp(c, 4), 0, Ui.dp(c, 10)));

        DisplayMetrics dm = c.getResources().getDisplayMetrics();
        MaxScroll sv = new MaxScroll(c, (int) (dm.heightPixels * 0.62f));
        LinearLayout form = new LinearLayout(c);
        form.setOrientation(LinearLayout.VERTICAL);
        sv.addView(form, new ViewGroup.LayoutParams(-1, -2));

        final ServerConfig cfg = ServerConfig.get(c);

        // Host & Port Row
        LinearLayout netRow = new LinearLayout(c);
        netRow.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout hostCol = new LinearLayout(c);
        hostCol.setOrientation(LinearLayout.VERTICAL);
        hostCol.addView(Ui.label(c, "服务器地址 (Host)", 12f, Ui.TEXT, true));
        final EditText etHost = new EditText(c);
        etHost.setText(cfg.getHost());
        etHost.setHint("127.0.0.1 或 0.0.0.0");
        etHost.setHintTextColor(Ui.MUTED);
        etHost.setTextColor(Ui.TEXT);
        etHost.setTextSize(13.5f);
        etHost.setTypeface(Typeface.MONOSPACE);
        etHost.setSingleLine(true);
        etHost.setBackground(Ui.rect(Ui.BG2, Ui.dpf(c, 8), Ui.STROKE, 1));
        etHost.setPadding(Ui.dp(c, 10), Ui.dp(c, 8), Ui.dp(c, 10), Ui.dp(c, 8));
        hostCol.addView(etHost, Ui.lpm(-1, -2, 0, Ui.dp(c, 4), 0, 0));
        netRow.addView(hostCol, new LinearLayout.LayoutParams(0, -2, 1.4f));

        LinearLayout portCol = new LinearLayout(c);
        portCol.setOrientation(LinearLayout.VERTICAL);
        portCol.addView(Ui.label(c, "端口 (Port)", 12f, Ui.TEXT, true));
        final EditText etPort = new EditText(c);
        etPort.setText(String.valueOf(cfg.getPort()));
        etPort.setHint("3306");
        etPort.setHintTextColor(Ui.MUTED);
        etPort.setTextColor(Ui.TEXT);
        etPort.setTextSize(13.5f);
        etPort.setTypeface(Typeface.MONOSPACE);
        etPort.setSingleLine(true);
        etPort.setInputType(InputType.TYPE_CLASS_NUMBER);
        etPort.setBackground(Ui.rect(Ui.BG2, Ui.dpf(c, 8), Ui.STROKE, 1));
        etPort.setPadding(Ui.dp(c, 10), Ui.dp(c, 8), Ui.dp(c, 10), Ui.dp(c, 8));
        portCol.addView(etPort, Ui.lpm(-1, -2, 0, Ui.dp(c, 4), 0, 0));
        LinearLayout.LayoutParams portLp = new LinearLayout.LayoutParams(0, -2, 1f);
        portLp.leftMargin = Ui.dp(c, 8);
        netRow.addView(portCol, portLp);

        form.addView(netRow, Ui.lpm(-1, -2, 0, 0, 0, Ui.dp(c, 10)));

        // User & Password Row
        LinearLayout authRow = new LinearLayout(c);
        authRow.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout userCol = new LinearLayout(c);
        userCol.setOrientation(LinearLayout.VERTICAL);
        userCol.addView(Ui.label(c, "用户名", 12f, Ui.TEXT, true));
        final EditText etUser = new EditText(c);
        etUser.setText(cfg.getUser());
        etUser.setHint("root");
        etUser.setHintTextColor(Ui.MUTED);
        etUser.setTextColor(Ui.TEXT);
        etUser.setTextSize(13.5f);
        etUser.setTypeface(Typeface.MONOSPACE);
        etUser.setSingleLine(true);
        etUser.setBackground(Ui.rect(Ui.BG2, Ui.dpf(c, 8), Ui.STROKE, 1));
        etUser.setPadding(Ui.dp(c, 10), Ui.dp(c, 8), Ui.dp(c, 10), Ui.dp(c, 8));
        userCol.addView(etUser, Ui.lpm(-1, -2, 0, Ui.dp(c, 4), 0, 0));
        authRow.addView(userCol, new LinearLayout.LayoutParams(0, -2, 1f));

        LinearLayout passCol = new LinearLayout(c);
        passCol.setOrientation(LinearLayout.VERTICAL);
        passCol.addView(Ui.label(c, "密码", 12f, Ui.TEXT, true));
        final EditText etPass = new EditText(c);
        etPass.setText(cfg.getPassword());
        etPass.setHint("留空为无密码");
        etPass.setHintTextColor(Ui.MUTED);
        etPass.setTextColor(Ui.TEXT);
        etPass.setTextSize(13.5f);
        etPass.setTypeface(Typeface.MONOSPACE);
        etPass.setSingleLine(true);
        etPass.setBackground(Ui.rect(Ui.BG2, Ui.dpf(c, 8), Ui.STROKE, 1));
        etPass.setPadding(Ui.dp(c, 10), Ui.dp(c, 8), Ui.dp(c, 10), Ui.dp(c, 8));
        passCol.addView(etPass, Ui.lpm(-1, -2, 0, Ui.dp(c, 4), 0, 0));
        LinearLayout.LayoutParams passLp = new LinearLayout.LayoutParams(0, -2, 1.2f);
        passLp.leftMargin = Ui.dp(c, 8);
        authRow.addView(passCol, passLp);

        form.addView(authRow, Ui.lpm(-1, -2, 0, 0, 0, Ui.dp(c, 12)));

        // Extra Startup Arguments Section
        form.addView(Ui.label(c, "mysqld 手动启动参数 (CLI Arguments)", 12f, Ui.TEXT, true));
        TextView argTip = Ui.label(c, "传递给 mysqld 进程的自定义启动参数，以空格或换行分隔：", 11.5f, Ui.TEXT2, false);
        form.addView(argTip, Ui.lpm(-1, -2, 0, Ui.dp(c, 2), 0, Ui.dp(c, 4)));

        final EditText etArgs = new EditText(c);
        etArgs.setText(cfg.getExtraArgs());
        etArgs.setHint("--innodb_buffer_pool_size=64M\n--max_connections=50\n--sql_mode=STRICT_TRANS_TABLES");
        etArgs.setHintTextColor(Ui.MUTED);
        etArgs.setTextColor(Ui.TEXT);
        etArgs.setTextSize(13f);
        etArgs.setTypeface(Typeface.MONOSPACE);
        etArgs.setMinLines(3);
        etArgs.setMaxLines(7);
        etArgs.setGravity(Gravity.TOP | Gravity.START);
        etArgs.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        etArgs.setBackground(Ui.rect(Ui.BG2, Ui.dpf(c, 8), Ui.STROKE, 1));
        etArgs.setPadding(Ui.dp(c, 10), Ui.dp(c, 8), Ui.dp(c, 10), Ui.dp(c, 8));
        form.addView(etArgs, Ui.lpm(-1, -2, 0, 0, 0, Ui.dp(c, 6)));

        // Quick Preset Chips
        HorizontalScrollView chipScroll = new HorizontalScrollView(c);
        chipScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = new LinearLayout(c);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chipScroll.addView(chips, new ViewGroup.LayoutParams(-2, -2));

        String[][] presets = {
                {"+ 缓冲池 64M", "--innodb_buffer_pool_size=64M"},
                {"+ 缓冲池 128M", "--innodb_buffer_pool_size=128M"},
                {"+ 最大连接 50", "--max_connections=50"},
                {"+ 开启通用日志", "--general_log=1"},
                {"+ 严格模式", "--sql_mode=STRICT_TRANS_TABLES"},
        };
        for (final String[] pr : presets) {
            FancyButton chip = new FancyButton(c, pr[0], -1, FancyButton.SECONDARY);
            chip.setCompact(true);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    String cur = etArgs.getText().toString();
                    if (!cur.contains(pr[1])) {
                        if (cur.length() > 0 && !cur.endsWith(" ") && !cur.endsWith("\n")) {
                            cur += "\n";
                        }
                        cur += pr[1];
                        etArgs.setText(cur);
                        etArgs.setSelection(cur.length());
                    }
                }
            });
            chips.addView(chip, Ui.lpm(-2, Ui.dp(c, 28), 0, 0, Ui.dp(c, 6), 0));
        }
        form.addView(chipScroll, Ui.lpm(-1, -2, 0, 0, 0, Ui.dp(c, 6)));

        p.addView(sv, Ui.lpm(-1, -2, 0, 0, 0, 0));

        // Bottom Actions
        LinearLayout row = buttonRow(c);
        FancyButton resetBtn = new FancyButton(c, "重置默认", -1, FancyButton.GHOST);
        resetBtn.setCompact(true);
        resetBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                etHost.setText(ServerConfig.DEFAULT_HOST);
                etPort.setText(String.valueOf(ServerConfig.DEFAULT_PORT));
                etUser.setText(ServerConfig.DEFAULT_USER);
                etPass.setText(ServerConfig.DEFAULT_PASSWORD);
                etArgs.setText(ServerConfig.DEFAULT_EXTRA_ARGS);
            }
        });

        FancyButton cancel = new FancyButton(c, "取消", -1, FancyButton.SECONDARY);
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (ref[0] != null) ref[0].dismiss();
            }
        });

        FancyButton save = new FancyButton(c, "保存配置", -1, FancyButton.PRIMARY);
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String h = etHost.getText().toString().trim();
                if (h.isEmpty()) h = ServerConfig.DEFAULT_HOST;

                int pt;
                try {
                    pt = Integer.parseInt(etPort.getText().toString().trim());
                    if (pt <= 0 || pt > 65535) throw new NumberFormatException();
                } catch (Exception e) {
                    Ui.shake(etPort);
                    return;
                }

                String u = etUser.getText().toString().trim();
                if (u.isEmpty()) u = ServerConfig.DEFAULT_USER;

                String pwd = etPass.getText().toString();
                String extra = etArgs.getText().toString().trim();

                cfg.setHost(h);
                cfg.setPort(pt);
                cfg.setUser(u);
                cfg.setPassword(pwd);
                cfg.setExtraArgs(extra);
                cfg.save(c);

                if (ref[0] != null) ref[0].dismiss();
                if (onSaved != null) onSaved.run();
            }
        });

        row.addView(resetBtn);
        View spacer = new View(c);
        row.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
        row.addView(cancel);
        row.addView(save, Ui.lpm(-2, -2, Ui.dp(c, 8), 0, 0, 0));
        p.addView(row);

        ref[0] = show(c, p);
    }

    /** Key/value view of one result row with per-field copy and whole-row copy. */
    public static void rowDetail(final Context c, String[] cols, final String[] values, int rowNumber,
                                 final ResultGrid.Host host) {
        final Handle[] ref = new Handle[1];
        LinearLayout p = panel(c);
        p.addView(titleRow(c, ref, IconView.TABLE, Ui.ACCENT, "第 " + (rowNumber + 1) + " 行数据详情"));

        DisplayMetrics dm = c.getResources().getDisplayMetrics();
        MaxScroll sv = new MaxScroll(c, (int) (dm.heightPixels * 0.58f));
        LinearLayout list = new LinearLayout(c);
        list.setOrientation(LinearLayout.VERTICAL);
        sv.addView(list, new ViewGroup.LayoutParams(-1, -2));

        for (int i = 0; i < cols.length && i < values.length; i++) {
            final String colName = cols[i];
            final String val = values[i];
            LinearLayout item = new LinearLayout(c);
            item.setOrientation(LinearLayout.HORIZONTAL);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding(Ui.dp(c, 12), Ui.dp(c, 9), Ui.dp(c, 8), Ui.dp(c, 9));
            item.setBackground(Ui.rect(i % 2 == 0 ? Ui.BG : Ui.CARD, Ui.dpf(c, 8), Ui.STROKE, 1));

            LinearLayout col = new LinearLayout(c);
            col.setOrientation(LinearLayout.VERTICAL);
            TextView k = Ui.label(c, colName, 12f, Ui.ACCENT, true);
            k.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
            TextView v = Ui.label(c, val, 13.5f, "NULL".equals(val) ? Ui.MUTED : Ui.TEXT, false);
            v.setTypeface(Typeface.MONOSPACE);
            v.setTextIsSelectable(true);
            col.addView(k);
            col.addView(v, Ui.lpm(-1, -2, 0, Ui.dp(c, 3), 0, 0));

            item.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
            FancyButton cp = new FancyButton(c, "复制", IconView.COPY, FancyButton.SECONDARY);
            cp.setCompact(true);
            cp.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    copy(c, colName, val);
                    if (host != null) host.toast("已复制 [" + colName + "]: " + val);
                }
            });
            item.addView(cp, Ui.lpm(-2, Ui.dp(c, 32), Ui.dp(c, 8), 0, 0, 0));
            list.addView(item, Ui.lpm(-1, -2, 0, 0, 0, Ui.dp(c, 6)));
        }
        p.addView(sv, Ui.lpm(-1, -2, 0, Ui.dp(c, 12), 0, 0));

        LinearLayout row = buttonRow(c);
        FancyButton copyRowBtn = new FancyButton(c, "复制整行 (TSV)", IconView.COPY, FancyButton.PRIMARY);
        copyRowBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < values.length; i++) {
                    if (i > 0) sb.append('\t');
                    sb.append(values[i]);
                }
                copy(c, "row", sb.toString());
                if (host != null) host.toast("已复制整行数据");
                if (ref[0] != null) ref[0].dismiss();
            }
        });
        row.addView(copyRowBtn);
        p.addView(row);

        ref[0] = show(c, p);
    }

    /** Simple list of actions with icons. */
    public static void actions(Context c, String title, String sub, final String[] labels, final int[] icons,
                               final Pick<Integer> cb) {
        final Handle[] ref = new Handle[1];
        LinearLayout p = panel(c);
        p.addView(titleRow(c, ref, IconView.TABLE, Ui.ACCENT, title));

        if (sub != null) {
            TextView s = Ui.label(c, sub, 12.5f, Ui.TEXT2, false);
            p.addView(s, Ui.lpm(-1, -2, Ui.dp(c, 30), Ui.dp(c, 2), 0, Ui.dp(c, 8)));
        }

        DisplayMetrics dm = c.getResources().getDisplayMetrics();
        MaxScroll sv = new MaxScroll(c, (int) (dm.heightPixels * 0.56f));
        LinearLayout list = new LinearLayout(c);
        list.setOrientation(LinearLayout.VERTICAL);
        sv.addView(list, new ViewGroup.LayoutParams(-1, -2));

        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            FancyButton b = new FancyButton(c, labels[i], icons[i], FancyButton.SECONDARY);
            b.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (ref[0] != null) ref[0].dismiss();
                    if (cb != null) cb.picked(idx);
                }
            });
            list.addView(b, Ui.lpm(-1, -2, 0, Ui.dp(c, 6), 0, 0));
        }

        p.addView(sv, Ui.lpm(-1, -2, 0, Ui.dp(c, 6), 0, 0));
        ref[0] = show(c, p);
    }

    public static void history(final Context c, final List<String> items, final Pick<String> cb,
                               final Runnable onClear) {
        final Handle[] ref = new Handle[1];
        LinearLayout p = panel(c);
        p.addView(titleRow(c, ref, IconView.HISTORY, Ui.ACCENT, "SQL 执行历史"));

        DisplayMetrics dm = c.getResources().getDisplayMetrics();
        if (items.isEmpty()) {
            TextView e = Ui.label(c, "还没有执行记录", 14, Ui.TEXT2, false);
            e.setGravity(Gravity.CENTER);
            p.addView(e, Ui.lpm(-1, -2, 0, Ui.dp(c, 30), 0, Ui.dp(c, 30)));
        } else {
            MaxScroll sv = new MaxScroll(c, (int) (dm.heightPixels * 0.56f));
            LinearLayout list = new LinearLayout(c);
            list.setOrientation(LinearLayout.VERTICAL);
            sv.addView(list, new ViewGroup.LayoutParams(-1, -2));

            for (int i = items.size() - 1; i >= 0; i--) {
                final String sql = items.get(i);
                TextView t = Ui.label(c, sql, 13f, Ui.TEXT, false);
                t.setTypeface(Typeface.MONOSPACE);
                t.setMaxLines(4);
                t.setEllipsize(android.text.TextUtils.TruncateAt.END);
                t.setPadding(Ui.dp(c, 12), Ui.dp(c, 10), Ui.dp(c, 12), Ui.dp(c, 10));
                t.setClickable(true);
                t.setFocusable(true);
                float r = Ui.dpf(c, 10);
                t.setBackground(Ui.rect(Ui.BG, r, Ui.STROKE, 1));
                Ui.hand(t);
                t.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (ref[0] != null) ref[0].dismiss();
                        if (cb != null) cb.picked(sql);
                    }
                });
                list.addView(t, Ui.lpm(-1, -2, 0, 0, 0, Ui.dp(c, 6)));
            }
            p.addView(sv, Ui.lpm(-1, -2, 0, Ui.dp(c, 10), 0, 0));

            LinearLayout row = buttonRow(c);
            FancyButton clear = new FancyButton(c, "清空历史", IconView.TRASH, FancyButton.DANGER);
            clear.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (ref[0] != null) ref[0].dismiss();
                    if (onClear != null) onClear.run();
                }
            });
            row.addView(clear);
            p.addView(row);
        }

        ref[0] = show(c, p);
    }

    /**
     * True 100% full-screen in-window overlay viewer for large SQL tables.
     * Guaranteed to span the entire Activity window edge-to-edge on all devices.
     */
    public static void fullscreenGrid(final Context c, final QueryResult r, final ResultGrid.Host host) {
        if (r == null || !r.isResultSet || r.rows.isEmpty()) {
            if (host != null) host.toast("当前没有可放大的结果集");
            return;
        }
        final Handle[] ref = new Handle[1];

        LinearLayout panel = new LinearLayout(c);
        panel.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(c, 14);
        panel.setPadding(pad, Ui.dp(c, 14), pad, pad);
        panel.setBackgroundColor(Ui.BG);

        // Top bar
        LinearLayout hdr = new LinearLayout(c);
        hdr.setOrientation(LinearLayout.HORIZONTAL);
        hdr.setGravity(Gravity.CENTER_VERTICAL);
        hdr.addView(new IconView(c, IconView.TABLE, Ui.ACCENT), Ui.lp(Ui.dp(c, 22), Ui.dp(c, 22)));

        LinearLayout titleCol = new LinearLayout(c);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        TextView title = Ui.label(c, "全屏表格视图 · 共 " + r.rows.size() + " 行", 16f, Ui.TEXT, true);
        TextView tip = Ui.label(c, "点击任意单元格立即复制 · 长按单元格查看整行详情 · 支持上下左右自由滑动", 11.5f, Ui.TEXT2, false);
        titleCol.addView(title);
        titleCol.addView(tip, Ui.lpm(-2, -2, 0, Ui.dp(c, 2), 0, 0));

        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, -2, 1f);
        tlp.leftMargin = Ui.dp(c, 10);
        hdr.addView(titleCol, tlp);

        final ResultGrid fullGrid = new ResultGrid(c, host);

        FancyButton copyTsv = new FancyButton(c, "复制全表", IconView.COPY, FancyButton.SECONDARY);
        copyTsv.setCompact(true);
        copyTsv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Dialogs.copy(c, "result", fullGrid.toTsv());
                if (host != null) host.toast("已复制全表数据 (TSV)");
            }
        });
        hdr.addView(copyTsv, Ui.lpm(-2, Ui.dp(c, 36), 0, 0, Ui.dp(c, 8), 0));

        FancyButton close = new FancyButton(c, "退出全屏", IconView.CLOSE, FancyButton.PRIMARY);
        close.setCompact(true);
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (ref[0] != null) ref[0].dismiss();
            }
        });
        hdr.addView(close, Ui.lpm(-2, Ui.dp(c, 36), 0, 0, 0, 0));
        panel.addView(hdr, Ui.lpm(-1, -2, 0, 0, 0, Ui.dp(c, 10)));

        panel.addView(fullGrid, new LinearLayout.LayoutParams(-1, 0, 1f));
        fullGrid.showResult(r);

        ref[0] = showInternal(c, panel, true);
    }
}
