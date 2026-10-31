package com.mysqllab;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Claude-style warm minimalist main shell with adaptive landscape rail / portrait bottom navigation,
 * in-window full-screen & modal overlay support, physical keyboard shortcuts & mouse support.
 * Author: tzdwindows 7
 */
public class MainActivity extends Activity implements AppHost, StateCallback {

    private static final String[] NAV_LABELS = {"服务器", "工作台", "数据浏览", "关于"};
    private static final int[] NAV_ICONS = {
            IconView.SERVER, IconView.TERMINAL, IconView.DATABASE, IconView.INFO
    };

    private MysqlServerManager serverManager;
    private final SqlSession sqlSession = new SqlSession();
    private ServerState currentState = ServerState.STOPPED;

    private FrameLayout rootFrame;
    private LinearLayout middleRow;
    private FrameLayout pageContainer;
    private NavBar railNav;
    private NavBar bottomNav;

    private View statusDot;
    private TextView statusLabel;
    private LinearLayout statusChip;
    private TextView toastBanner;

    private ServerPage serverPage;
    private WorkbenchPage workbenchPage;
    private ExplorerPage explorerPage;
    private AboutPage aboutPage;
    private Page[] pages;

    private int activePage = 0;
    private boolean isWide = false;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable hideToastRunnable = new Runnable() {
        @Override
        public void run() {
            if (toastBanner != null) {
                toastBanner.animate().alpha(0f).translationY(-Ui.dpf(MainActivity.this, 14))
                        .setDuration(180).withEndAction(new Runnable() {
                            @Override
                            public void run() {
                                toastBanner.setVisibility(View.GONE);
                            }
                        }).start();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupWindowStyle();
        ServerConfig.get(this);

        serverManager = new MysqlServerManager(this);

        rootFrame = new FrameLayout(this);
        rootFrame.setBackgroundColor(Ui.BG);
        Dialogs.attachRoot(rootFrame);

        // 1. Clean Warm Paper Backdrop
        AuroraView aurora = new AuroraView(this);
        rootFrame.addView(aurora, new FrameLayout.LayoutParams(-1, -1));

        // 2. Main Foreground Vertical Shell
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        rootFrame.addView(shell, new FrameLayout.LayoutParams(-1, -1));

        // ---- Top Header Bar
        LinearLayout header = buildHeaderBar();
        shell.addView(header, new LinearLayout.LayoutParams(-1, -2));

        // Subtle 1dp header divider line
        View hdrDiv = new View(this);
        hdrDiv.setBackgroundColor(Ui.STROKE);
        shell.addView(hdrDiv, new LinearLayout.LayoutParams(-1, Math.max(1, Ui.dp(this, 1))));

        // ---- Middle Row (Rail Nav in landscape + Page Container)
        middleRow = new LinearLayout(this);
        middleRow.setOrientation(LinearLayout.HORIZONTAL);
        shell.addView(middleRow, new LinearLayout.LayoutParams(-1, 0, 1f));

        railNav = new NavBar(this, true, NAV_LABELS, NAV_ICONS);
        int rPad = Ui.dp(this, 6);
        railNav.setPadding(rPad, Ui.dp(this, 8), rPad, Ui.dp(this, 8));
        railNav.setBackground(Ui.rect(Ui.CARD, Ui.dpf(this, 14), Ui.STROKE, Math.max(1, Ui.dp(this, 1))));
        railNav.setListener(new NavBar.Listener() {
            @Override
            public void onSelect(int index) {
                showPage(index);
            }
        });
        LinearLayout.LayoutParams railLp = new LinearLayout.LayoutParams(-2, -1);
        railLp.setMargins(Ui.dp(this, 10), Ui.dp(this, 8), 0, Ui.dp(this, 10));
        middleRow.addView(railNav, railLp);

        pageContainer = new FrameLayout(this);
        int cPad = Ui.dp(this, 6);
        pageContainer.setPadding(cPad, 0, cPad, Ui.dp(this, 4));
        middleRow.addView(pageContainer, new LinearLayout.LayoutParams(0, -1, 1f));

        // ---- Bottom Navigation Bar (Portrait)
        bottomNav = new NavBar(this, false, NAV_LABELS, NAV_ICONS);
        int bPad = Ui.dp(this, 4);
        bottomNav.setPadding(bPad, bPad, bPad, bPad);
        bottomNav.setBackground(Ui.rect(Ui.CARD, Ui.dpf(this, 14), Ui.STROKE, Math.max(1, Ui.dp(this, 1))));
        bottomNav.setListener(new NavBar.Listener() {
            @Override
            public void onSelect(int index) {
                showPage(index);
            }
        });
        LinearLayout.LayoutParams botLp = new LinearLayout.LayoutParams(-1, -2);
        botLp.setMargins(Ui.dp(this, 10), 0, Ui.dp(this, 10), Ui.dp(this, 8));
        shell.addView(bottomNav, botLp);

        // ---- Pages
        serverPage = new ServerPage(this, this);
        workbenchPage = new WorkbenchPage(this, this);
        explorerPage = new ExplorerPage(this, this);
        aboutPage = new AboutPage(this, this);
        pages = new Page[]{serverPage, workbenchPage, explorerPage, aboutPage};

        for (int i = 0; i < pages.length; i++) {
            pages[i].setVisibility(i == 0 ? View.VISIBLE : View.GONE);
            pageContainer.addView(pages[i], new FrameLayout.LayoutParams(-1, -1));
        }

        // ---- Minimalist Toast Banner (Dark charcoal pill with warm ivory text, always on top)
        toastBanner = Ui.label(this, "", 13f, 0xFFFAF9F5, true);
        toastBanner.setPadding(Ui.dp(this, 16), Ui.dp(this, 10), Ui.dp(this, 16), Ui.dp(this, 10));
        toastBanner.setBackground(Ui.rect(0xF2141413, Ui.dpf(this, 999), Ui.ACCENT, Math.max(1, Ui.dp(this, 1))));
        toastBanner.setVisibility(View.GONE);
        if (Build.VERSION.SDK_INT >= 21) {
            toastBanner.setElevation(Ui.dpf(this, 16));
        }
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        tlp.topMargin = Ui.dp(this, 58);
        rootFrame.addView(toastBanner, tlp);

        setContentView(rootFrame);

        applyResponsiveMode(getResources().getConfiguration());
        requestNotificationPermissionIfNeeded();
        serverManager.setCallback(this);
        onStateChanged(serverManager.getCurrentState(), "Ready");
        pages[0].onShown();
    }

    @Override
    public void onBackPressed() {
        if (Dialogs.dismissTop()) {
            return;
        }
        super.onBackPressed();
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 101 && currentState == ServerState.RUNNING) {
            MysqlForegroundService.start(this);
        }
    }

    private void setupWindowStyle() {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        Window w = getWindow();
        if (w != null) {
            if (Build.VERSION.SDK_INT >= 21) {
                w.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
                w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
                w.setStatusBarColor(Ui.BG);
                w.setNavigationBarColor(Ui.BG);
            }
            if (Build.VERSION.SDK_INT >= 23) {
                int flags = w.getDecorView().getSystemUiVisibility();
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                if (Build.VERSION.SDK_INT >= 26) {
                    flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                }
                w.getDecorView().setSystemUiVisibility(flags);
            }
            if (Build.VERSION.SDK_INT >= 28) {
                WindowManager.LayoutParams lp = w.getAttributes();
                lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
                w.setAttributes(lp);
            }
        }
    }

    private LinearLayout buildHeaderBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        int hPad = Ui.dp(this, 12);
        int vPad = Ui.dp(this, 8);
        bar.setPadding(hPad, vPad, hPad, vPad);
        bar.setBackgroundColor(Ui.BG);

        // Minimalist Terracotta App Emblem
        LinearLayout logo = new LinearLayout(this);
        logo.setGravity(Gravity.CENTER);
        logo.setBackground(Ui.rect(0x1FD97757, Ui.dpf(this, 10), 0x4DD97757, Math.max(1, Ui.dp(this, 1))));
        logo.addView(new IconView(this, IconView.DATABASE, Ui.ACCENT), Ui.lp(Ui.dp(this, 18), Ui.dp(this, 18)));
        bar.addView(logo, Ui.lp(Ui.dp(this, 34), Ui.dp(this, 34)));

        // Title + Author subtitle
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView tMain = Ui.label(this, "MySQL 8.0", 15.5f, Ui.TEXT, true);
        TextView badge = Ui.label(this, "Lab", 10f, Ui.ACCENT, true);
        badge.setPadding(Ui.dp(this, 5), Ui.dp(this, 1), Ui.dp(this, 5), Ui.dp(this, 1));
        badge.setBackground(Ui.rect(0x1FD97757, Ui.dpf(this, 5), 0x4DD97757, 1));
        titleRow.addView(tMain);
        titleRow.addView(badge, Ui.lpm(-2, -2, Ui.dp(this, 6), 0, 0, 0));
        titles.addView(titleRow);

        TextView tSub = Ui.label(this, "作者: " + AboutPage.AUTHOR, 11f, Ui.TEXT2, false);
        tSub.setSingleLine(true);
        tSub.setEllipsize(TextUtils.TruncateAt.END);
        titles.addView(tSub, Ui.lpm(-2, -2, 0, Ui.dp(this, 1), 0, 0));

        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, -2, 1f);
        tlp.leftMargin = Ui.dp(this, 9);
        bar.addView(titles, tlp);

        // Compact Live Server Status Pill
        statusChip = new LinearLayout(this);
        statusChip.setOrientation(LinearLayout.HORIZONTAL);
        statusChip.setGravity(Gravity.CENTER_VERTICAL);
        statusChip.setPadding(Ui.dp(this, 10), Ui.dp(this, 5), Ui.dp(this, 10), Ui.dp(this, 5));
        statusChip.setBackground(Ui.rect(Ui.CARD, Ui.dpf(this, 999), Ui.STROKE, Math.max(1, Ui.dp(this, 1))));
        statusChip.setClickable(true);
        statusChip.setFocusable(true);
        Ui.hand(statusChip);
        statusChip.setTooltipText("服务器运行状态 · 点击切换到服务器控制台");
        statusChip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showPage(0);
            }
        });

        statusDot = new View(this);
        statusDot.setBackground(Ui.oval(Ui.MUTED));
        statusChip.addView(statusDot, Ui.lp(Ui.dp(this, 7), Ui.dp(this, 7)));

        statusLabel = Ui.label(this, "未启动", 11.5f, Ui.TEXT, true);
        statusLabel.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        statusChip.addView(statusLabel, Ui.lpm(-2, -2, Ui.dp(this, 5), 0, 0, 0));
        bar.addView(statusChip, Ui.lpm(-2, -2, Ui.dp(this, 4), 0, Ui.dp(this, 6), 0));

        // Quick Author / About Button
        FancyButton aboutBtn = new FancyButton(this, "关于", IconView.INFO, FancyButton.GHOST);
        aboutBtn.setCompact(true);
        aboutBtn.setTooltip("关于本软件 · 作者：" + AboutPage.AUTHOR);
        aboutBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showPage(3);
            }
        });
        bar.addView(aboutBtn, Ui.lp(-2, Ui.dp(this, 32)));

        return bar;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        applyResponsiveMode(newConfig);
    }

    private void applyResponsiveMode(Configuration cfg) {
        boolean wide = cfg.orientation == Configuration.ORIENTATION_LANDSCAPE || cfg.screenWidthDp >= 620;
        isWide = wide;
        railNav.setVisibility(wide ? View.VISIBLE : View.GONE);
        bottomNav.setVisibility(wide ? View.GONE : View.VISIBLE);
        railNav.select(activePage, false);
        bottomNav.select(activePage, false);
        if (pages != null) {
            for (Page p : pages) {
                p.setWide(wide);
            }
        }
    }

    // ---- AppHost Implementation

    @Override
    public Context context() {
        return this;
    }

    @Override
    public SqlSession session() {
        return sqlSession;
    }

    @Override
    public MysqlServerManager manager() {
        return serverManager;
    }

    @Override
    public ServerState serverState() {
        return currentState;
    }

    @Override
    public void showPage(int index) {
        if (index < 0 || index >= pages.length) return;
        railNav.select(index, true);
        bottomNav.select(index, true);
        if (index == activePage) {
            pages[index].onShown();
            return;
        }
        final int prev = activePage;
        final int dir = index > prev ? 1 : -1;
        activePage = index;

        final Page outPage = pages[prev];
        final Page inPage = pages[index];

        outPage.animate().cancel();
        inPage.animate().cancel();

        float shift = Ui.dpf(this, 18) * dir;
        inPage.setVisibility(View.VISIBLE);
        inPage.setAlpha(0f);
        inPage.setTranslationX(isWide ? 0f : shift);
        inPage.setTranslationY(isWide ? shift * 0.5f : 0f);
        inPage.animate()
                .alpha(1f)
                .translationX(0f)
                .translationY(0f)
                .setDuration(200)
                .setInterpolator(new DecelerateInterpolator(1.6f))
                .start();

        outPage.animate()
                .alpha(0f)
                .translationX(isWide ? 0f : -shift * 0.4f)
                .translationY(isWide ? -shift * 0.3f : 0f)
                .setDuration(150)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        if (activePage != prev) {
                            outPage.setVisibility(View.GONE);
                            outPage.setTranslationX(0f);
                            outPage.setTranslationY(0f);
                        }
                    }
                }).start();

        inPage.onShown();
    }

    @Override
    public void runInWorkbench(String sql, boolean execute) {
        showPage(1);
        workbenchPage.runSql(sql, execute);
    }

    @Override
    public void startServer() {
        serverManager.startServer();
    }

    @Override
    public void stopServer() {
        sqlSession.reset();
        serverManager.stopServer();
    }

    @Override
    public void reinitServer() {
        sqlSession.reset();
        serverManager.reinitializeDatabase();
    }

    @Override
    public void toast(final String msg) {
        if (msg == null || msg.isEmpty()) return;
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                mainHandler.removeCallbacks(hideToastRunnable);
                toastBanner.bringToFront();
                toastBanner.setText(msg);
                toastBanner.setVisibility(View.VISIBLE);
                toastBanner.setAlpha(0f);
                toastBanner.setTranslationY(-Ui.dpf(MainActivity.this, 12));
                toastBanner.animate().alpha(1f).translationY(0f)
                        .setDuration(180)
                        .setInterpolator(new DecelerateInterpolator())
                        .start();
                mainHandler.postDelayed(hideToastRunnable, 2400);
            }
        });
    }

    // ---- StateCallback Implementation

    @Override
    public void onStateChanged(ServerState state, String message) {
        currentState = state;
        if (state == ServerState.RUNNING) {
            MysqlForegroundService.start(this);
        } else if (state == ServerState.STOPPED || state == ServerState.ERROR) {
            MysqlForegroundService.stop(this);
        }
        int dotColor;
        String label;
        if (state == ServerState.RUNNING) {
            dotColor = Ui.GREEN;
            label = String.valueOf(ServerConfig.get(this).getPort());
        } else if (state == ServerState.STARTING) {
            dotColor = Ui.ACCENT;
            label = "启动中";
        } else if (state == ServerState.INITIALIZING) {
            dotColor = Ui.BLUE;
            label = "初始化";
        } else if (state == ServerState.ERROR) {
            dotColor = Ui.RED;
            label = "异常";
        } else {
            dotColor = Ui.MUTED;
            label = "未启动";
        }
        statusDot.setBackground(Ui.oval(dotColor));
        statusLabel.setText(label);
        statusChip.setBackground(Ui.rect(Ui.CARD, Ui.dpf(this, 999),
                state == ServerState.RUNNING ? Ui.alpha(Ui.GREEN, 110) : Ui.STROKE, Math.max(1, Ui.dp(this, 1))));

        if (pages != null) {
            for (Page p : pages) {
                p.onServerState(state, message);
            }
        }
    }

    @Override
    public void onLog(String line) {
        if (serverPage != null) {
            serverPage.onLog(line);
        }
    }

    // ---- Physical Keyboard Shortcuts Support

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int code = event.getKeyCode();
            if (code == KeyEvent.KEYCODE_ESCAPE) {
                if (Dialogs.dismissTop()) return true;
            }
            boolean ctrl = event.isCtrlPressed() || event.isMetaPressed();

            // F5 or Ctrl+Enter -> Run SQL in Workbench
            if (code == KeyEvent.KEYCODE_F5
                    || (ctrl && (code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER))) {
                if (activePage != 1) showPage(1);
                workbenchPage.run();
                return true;
            }
            if (ctrl) {
                if (code == KeyEvent.KEYCODE_1) {
                    showPage(0);
                    return true;
                }
                if (code == KeyEvent.KEYCODE_2) {
                    showPage(1);
                    return true;
                }
                if (code == KeyEvent.KEYCODE_3) {
                    showPage(2);
                    return true;
                }
                if (code == KeyEvent.KEYCODE_4) {
                    showPage(3);
                    return true;
                }
                if (code == KeyEvent.KEYCODE_L && activePage == 1) {
                    workbenchPage.clearAll();
                    return true;
                }
                if (code == KeyEvent.KEYCODE_H && activePage == 1) {
                    workbenchPage.showHistory();
                    return true;
                }
                if (code == KeyEvent.KEYCODE_DPAD_UP && activePage == 1) {
                    workbenchPage.historyNav(-1);
                    return true;
                }
                if (code == KeyEvent.KEYCODE_DPAD_DOWN && activePage == 1) {
                    workbenchPage.historyNav(1);
                    return true;
                }
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (pages != null) {
            for (Page p : pages) {
                p.onPauseSave();
            }
        }
    }
}
