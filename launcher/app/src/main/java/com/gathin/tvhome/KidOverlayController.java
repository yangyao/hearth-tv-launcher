package com.gathin.tvhome;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * The layer a child cannot bypass: the quiz at the end of each watched session, the daily-cap lock,
 * and the parent gate behind both. Shown as a TYPE_APPLICATION_OVERLAY window when the permission is
 * granted (one adb command, shown in KidModeSettingsActivity), so it covers a playing video app and
 * survives Back and Home; without it the same cards render inside ParentGateActivity on Home.
 * Back, Menu and Settings do nothing here; number keys on the remote type into the answer.
 */
public final class KidOverlayController {

    public static final String QUIZ = "quiz", LOCK = "lock", GATE = "gate", OPTIONS = "options", PASSED = "passed";

    /** What the cards call back into; the overlay and the in-Home activity wire it to KidSession. */
    public interface Host {
        /** The child answered the whole group. */
        void onQuizPassed();
        /** Parent chose "unlock for today". */
        void onUnlockToday();
        /** Parent chose "turn kids mode off". */
        void onCloseMode();
        /** Back on the gate: the overlay ignores it, the in-Home activity finishes. */
        void onGateAbandon();
    }

    private static View current;
    private static String currentMode;

    private KidOverlayController() {}

    public static boolean canOverlay(Context c) { return Settings.canDrawOverlays(c); }

    public static boolean showing() { return current != null; }

    /** What is on the layer right now (QUIZ, LOCK, GATE or OPTIONS), or null. */
    public static String currentMode() { return currentMode; }

    /** Shows {@code mode} over everything; false when the overlay permission is missing. */
    public static boolean show(Context c, String mode, Host host) {
        Context app = c.getApplicationContext();
        if (!canOverlay(app)) return false;
        WindowManager wm = (WindowManager) app.getSystemService(Context.WINDOW_SERVICE);
        hide(wm);
        Shield shield = new Shield(app, host);
        shield.swap(mode);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_DIM_BEHIND,
                android.graphics.PixelFormat.TRANSLUCENT);
        lp.dimAmount = 0.75f;
        try {
            wm.addView(shield, lp);
        } catch (Exception e) {
            return false;
        }
        current = shield;
        currentMode = mode;
        shield.requestFocus();
        return true;
    }

    public static void dismiss() {
        View v = current;
        current = null;
        currentMode = null;
        if (v == null) return;
        try {
            ((WindowManager) v.getContext().getSystemService(Context.WINDOW_SERVICE)).removeView(v);
        } catch (Exception ignored) {
        }
    }

    private static void hide(WindowManager wm) {
        View v = current;
        current = null;
        currentMode = null;
        if (v != null) {
            try {
                wm.removeView(v);
            } catch (Exception ignored) {
            }
        }
    }

    /** The in-Home shield (ParentGateActivity) hosts the same cards; setContentView on the result. */
    public static Shield buildShield(Context c, Host host) {
        return new Shield(c, host);
    }

    /**
     * The card surface itself. Keys: Back is swallowed (the gate hands it to the host), Menu and
     * Settings die here, number keys type into the answer, the rest reaches the focused keypad key.
     */
    public static final class Shield extends FrameLayout implements KidSession.GateUi {
        private final Host host;
        private final Handler handler = new Handler(Looper.getMainLooper());
        private String mode;
        private List<KidQuiz.Question> questions;
        private int qIndex;
        private final StringBuilder input = new StringBuilder();
        private KidGateServer gateServer;   // up only while a GATE card shows and the toggle allows
        private boolean passedAll;
        private TextView answerLine, messageLine;
        private ImageView qrView;
        private Runnable onConfirm;

        Shield(Context c, Host host) {
            super(c);
            this.host = host;
            setBackgroundColor(0xE6101418);
            setFocusable(true);
            setFocusableInTouchMode(true);
        }

        /** Loads the state a mode needs and renders it. Also the way the card changes mode. */
        public void swap(String newMode) {
            mode = newMode;
            KidModeSettings cfg = KidModeSettings.load(getContext());
            if (QUIZ.equals(mode) && questions == null)
                questions = KidQuiz.generate(new Random(), cfg.grade, cfg.questions);
            if (GATE.equals(mode)) {
                KidSession session = KidSession.get(getContext());
                gateServer = cfg.qrAnswer ? session.ensureGateServer() : null;
                session.setGateUi(this);   // the phone's answers and choice re-render / close this card
            }
            if (OPTIONS.equals(mode)) KidSession.get(getContext()).clearGate();
            input.setLength(0);
            passedAll = false;
            render();
        }

        private void render() {
            removeAllViews();
            answerLine = null;
            messageLine = null;
            qrView = null;
            LinearLayout panel = new LinearLayout(getContext());
            panel.setOrientation(LinearLayout.VERTICAL);
            panel.setGravity(Gravity.CENTER_HORIZONTAL);
            panel.setPadding(dp(36), dp(28), dp(36), dp(28));
            panel.setBackground(rounded(0xFF1A242C, dp(18)));
            FrameLayout wrapper = new FrameLayout(getContext());
            wrapper.addView(panel, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
            // The gate card with its keypad is taller than some TV panels: a plainly centered layout
            // would clip the bottom keypad row off the screen. A fill-viewport ScrollView centers
            // whatever fits and scrolls the rest, and D-pad focus moves keep every key reachable.
            ScrollView scroll = new ScrollView(getContext());
            scroll.setFillViewport(true);
            scroll.setVerticalScrollBarEnabled(false);
            scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
            scroll.addView(wrapper, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            addView(scroll, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            if (OPTIONS.equals(mode)) optionsContent(panel);
            else if (LOCK.equals(mode)) lockContent(panel);
            else if (GATE.equals(mode)) gateContent(panel);
            else if (PASSED.equals(mode)) passedContent(panel);
            else if (passedAll) doneContent(panel);
            else quizContent(panel);
            handler.removeCallbacks(gateTick);
            if (GATE.equals(mode)) handler.postDelayed(gateTick, 500);   // cooldown line + QR validity
        }

        // ---- the four cards ----------------------------------------------------------------------

        private void quizContent(LinearLayout panel) {
            KidQuiz.Question q = questions.get(qIndex);
            panel.addView(text(getContext(), "休息时间", 26, 0xFFE4E8EB, dp(4)));
            panel.addView(text(getContext(), "第 " + (qIndex + 1) + " / " + questions.size() + " 题 · 答对就能继续看", 15, 0xFF8D9BA7, dp(2)));
            TextView question = text(getContext(), q.text + " = ?", 40, 0xFFFFFFFF, dp(16));
            panel.addView(question);
            answerLine = text(getContext(), "＝ " + input, 30, 0xFF9AD6A8, dp(6));
            panel.addView(answerLine);
            messageLine = text(getContext(), "", 15, 0xFF8D9BA7, dp(2));
            panel.addView(messageLine);
            panel.addView(keypad(() -> {
                KidQuiz.Question current = questions.get(qIndex);
                if (input.length() == 0) return;
                if (Integer.parseInt(input.toString()) == current.answer) {
                    qIndex++;
                    if (qIndex >= questions.size()) {
                        passedAll = true;
                        render();
                        handler.postDelayed(() -> host.onQuizPassed(), 1800);
                    } else {
                        render();   // next question, same group
                    }
                } else {
                    input.setLength(0);   // same question again: a checkpoint, not a punishment
                    answerLine.setText("＝ ");
                    messageLine.setText("不对，再想一想");
                    messageLine.setTextColor(0xFFE57373);
                }
            }));
            parentEntry(panel);
        }

        private void doneContent(LinearLayout panel) {
            panel.addView(text(getContext(), "全部答对！", 30, 0xFF9AD6A8, dp(6)));
            panel.addView(text(getContext(), "继续观看", 17, 0xFF8D9BA7, dp(4)));
        }

        private void lockContent(LinearLayout panel) {
            KidModeSettings cfg = KidModeSettings.load(getContext());
            panel.addView(text(getContext(), "今天的时间用完了", 26, 0xFFE4E8EB, dp(4)));
            panel.addView(text(getContext(), "每天可用 " + cfg.dailyCapMinutes + " 分钟，现在该休息了。", 17, 0xFF8D9BA7, dp(2)));
            panel.addView(text(getContext(), "想继续看或关掉儿童模式，请家长解锁。", 15, 0xFF8D9BA7, dp(2)));
            panel.addView(bigButton(getContext(), "家长解锁", () -> swap(GATE), dp(18)));
        }

        private void gateContent(LinearLayout panel) {
            KidSession session = KidSession.get(getContext());
            panel.addView(text(getContext(), "家长验证", 26, 0xFFE4E8EB, dp(4)));

            boolean qr = gateServer != null && gateServer.running() && gateServer.url() != null;
            if (qr) {
                // QR-only card: the question lives on the phone page, nothing to read or type here.
                panel.addView(text(getContext(), "请家长用手机扫码作答", 16, 0xFF8D9BA7, dp(10)));
                qrView = new ImageView(getContext());
                qrView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                qrView.setPadding(dp(8), dp(8), dp(8), dp(8));
                qrView.setBackground(rounded(0xFFFFFFFF, dp(10)));
                rebuildQr(dp(154));
                panel.addView(qrView, new LinearLayout.LayoutParams(dp(170), dp(170)));
                messageLine = text(getContext(), "", 14, 0xFF53616B, dp(12));
                panel.addView(messageLine);
                return;
            }

            // No QR (toggle off, or the TV has no LAN): the question with the remote keypad.
            panel.addView(text(getContext(), "请家长计算，孩子不用试啦", 15, 0xFF8D9BA7, dp(2)));
            LinearLayout left = new LinearLayout(getContext());
            left.setOrientation(LinearLayout.VERTICAL);
            left.setGravity(Gravity.CENTER_HORIZONTAL);
            KidQuiz.Question q = session.gateQuestion();
            left.addView(text(getContext(), q.text + " = ?", 38, 0xFFFFFFFF, dp(16)));
            answerLine = text(getContext(), "＝ " + input, 30, 0xFF9AD6A8, dp(6));
            left.addView(answerLine);
            messageLine = text(getContext(), "", 15, 0xFF8D9BA7, dp(2));
            left.addView(messageLine);
            left.addView(keypad(this::submitGate));
            panel.addView(left);
        }

        private void optionsContent(LinearLayout panel) {
            panel.addView(text(getContext(), "验证通过", 26, 0xFF9AD6A8, dp(4)));
            panel.addView(text(getContext(), "接下来想怎么做？", 17, 0xFF8D9BA7, dp(2)));
            panel.addView(bigButton(getContext(), "今日解锁（家长陪同）", () -> host.onUnlockToday(), dp(18)));
            panel.addView(bigButton(getContext(), "关闭儿童模式", () -> host.onCloseMode(), dp(18)));
        }

        /**
         * The QR path's after-pass card: passive on purpose — the choice happens on the phone where
         * the QR was answered, and this card just follows (it goes away when the choice lands).
         */
        private void passedContent(LinearLayout panel) {
            panel.addView(text(getContext(), "验证通过", 30, 0xFF9AD6A8, dp(6)));
            panel.addView(text(getContext(), "请在手机上选择下一步", 17, 0xFF8D9BA7, dp(10)));
            panel.addView(text(getContext(), "电视会跟着手机的操作自动变化", 13, 0xFF53616B, dp(4)));
        }

        /** The quiz card's quiet way in for a parent; the dropdown's close entry lands on GATE directly. */
        private void parentEntry(LinearLayout panel) {
            TextView entry = text(getContext(), "家长入口", 13, 0xFF53616B, dp(0));
            entry.setPadding(0, dp(14), 0, 0);
            entry.setFocusable(true);
            entry.setClickable(true);
            entry.setBackground(rounded(0x00000000, dp(10)));
            entry.setOnClickListener(v -> swap(GATE));
            panel.addView(entry);
        }

        /** The TV keypad's confirm: the session checks it, the listener re-renders with the result. */
        private void submitGate() {
            if (input.length() == 0) return;
            KidSession.get(getContext()).submitGateAnswer(input.toString());
        }

        /** A gate answer landed (TV keypad or phone): choose where the choice happens. */
        @Override public void onGateAnswered(KidSession.GateResult result) {
            if (result == KidSession.GateResult.OK) {
                // QR mode: the phone chose to answer, so the phone also chooses what's next; the
                // keypad path has no phone, so the choice buttons stay here on the TV.
                boolean qr = gateServer != null && gateServer.running();
                swap(qr ? PASSED : OPTIONS);
                return;
            }
            if (result == KidSession.GateResult.WRONG) {
                input.setLength(0);
                render();
            }
            // COOLDOWN: the tick below is already counting the wait down.
        }

        /** The choice was made on the phone: the in-Home activity closes, the overlay host ignores it. */
        @Override public void onGateClosed() { host.onGateAbandon(); }

        /** The gate's resident ½ s tick: cooldown countdown on the keypad card, QR validity on the QR one. */
        private final Runnable gateTick = new Runnable() {
            @Override public void run() {
                if (!GATE.equals(mode)) return;
                long cd = KidSession.get(getContext()).gateCooldownUntil() - System.currentTimeMillis();
                if (messageLine != null) {
                    if (cd > 0) {
                        long left = (cd + 999) / 1000;
                        messageLine.setText(left >= 60
                                ? String.format(Locale.US, "答案不正确；请等 %d 分 %02d 秒后再试", left / 60, left % 60)
                                : String.format(Locale.US, "答案不正确；请等 %d 秒后再试", left));
                        messageLine.setTextColor(0xFFE57373);
                    } else if (gateServer != null && gateServer.running()) {
                        long left = gateServer.tokenExpiresIn();
                        messageLine.setText(left > 0
                                ? String.format(Locale.US, "二维码 %d:%02d 内有效，过期自动刷新", left / 60000, left / 1000 % 60)
                                : "正在刷新二维码…");
                        messageLine.setTextColor(0xFF53616B);
                    } else if (messageLine.length() > 0) {
                        messageLine.setText("");
                    }
                }
                if (gateServer != null && gateServer.running() && gateServer.tokenExpiresIn() <= 0) {
                    gateServer.rotateToken();
                    rebuildQr(dp(154));
                }
                handler.postDelayed(this, 500);
            }
        };

        private void rebuildQr(int aboutPx) {
            if (gateServer == null || qrView == null) return;
            String url = gateServer.url();
            if (url == null) return;
            qrView.setImageBitmap(KidGateServer.qrBitmap(url, aboutPx));
        }

        // ---- keys ---------------------------------------------------------------------------------

        @Override
        public boolean dispatchKeyEvent(KeyEvent event) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                switch (event.getKeyCode()) {
                    case KeyEvent.KEYCODE_BACK:
                        // Gate and passed: the parent may change their mind (the quiz/lock returns);
                        // quiz and lock: nothing gets past the layer.
                        if (GATE.equals(mode) || PASSED.equals(mode)) host.onGateAbandon();
                        return true;
                    case KeyEvent.KEYCODE_MENU:
                    case KeyEvent.KEYCODE_SETTINGS:
                    case KeyEvent.KEYCODE_ALL_APPS:
                    case KeyEvent.KEYCODE_APP_SWITCH:
                        return true;
                    default:
                        int digit = digitOf(event.getKeyCode());
                        if (digit >= 0 && input.length() < 6) {
                            input.append(digit);
                            if (answerLine != null) answerLine.setText("＝ " + input);
                            return true;
                        }
                }
            }
            return super.dispatchKeyEvent(event);
        }

        private static int digitOf(int keyCode) {
            if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) return keyCode - KeyEvent.KEYCODE_0;
            if (keyCode >= KeyEvent.KEYCODE_NUMPAD_0 && keyCode <= KeyEvent.KEYCODE_NUMPAD_9)
                return keyCode - KeyEvent.KEYCODE_NUMPAD_0;
            return -1;
        }

        @Override
        protected void onDetachedFromWindow() {
            handler.removeCallbacksAndMessages(null);
            KidSession session = KidSession.get(getContext());
            session.setGateUi(null);
            session.stopGateServer();   // the LAN port lives exactly as long as the card does
            super.onDetachedFromWindow();
        }

        // ---- small widgets -------------------------------------------------------------------------

        /** The remote keypad: three columns, del / 0 / confirm on the last row. */
        private View keypad(Runnable confirm) {
            LinearLayout grid = new LinearLayout(getContext());
            grid.setOrientation(LinearLayout.VERTICAL);
            grid.setPadding(0, dp(14), 0, 0);
            String[][] rows = {{"7", "8", "9"}, {"4", "5", "6"}, {"1", "2", "3"}, {"删除", "0", "确定"}};
            for (final String[] row : rows) {
                LinearLayout line = new LinearLayout(getContext());
                line.setOrientation(LinearLayout.HORIZONTAL);
                for (final String key : row) {
                    boolean confirmKey = "确定".equals(key), deleteKey = "删除".equals(key);
                    TextView b = button(getContext(), key, () -> {
                        if (confirmKey) onConfirm.run();
                        else if (deleteKey) {
                            if (input.length() > 0) input.setLength(input.length() - 1);
                            if (answerLine != null) answerLine.setText("＝ " + input);
                        } else if (input.length() < 6) {
                            input.append(key);
                            if (answerLine != null) answerLine.setText("＝ " + input);
                        }
                    });
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(88), dp(56));
                    lp.setMargins(dp(6), dp(6), dp(6), dp(6));
                    line.addView(b, lp);
                }
                grid.addView(line);
            }
            onConfirm = confirm;
            return grid;
        }

        private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    }

    // ---- widget helpers, shared so the cards all look the same -----------------------------------

    private static TextView text(Context c, String s, int sp, int color, int vPad) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(sp);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, vPad, 0, vPad);
        return t;
    }

    /** A focusable pill in the NasPhotoSettings style: filled when focused, outlined when not. */
    private static TextView button(Context c, String label, Runnable action) {
        int dp = Math.round(c.getResources().getDisplayMetrics().density);
        TextView t = new TextView(c);
        t.setText(label);
        t.setTextColor(0xFFE4E8EB);
        t.setTextSize(19);
        t.setGravity(Gravity.CENTER);
        t.setFocusable(true);
        t.setClickable(true);
        t.setBackground(shape(false, dp));
        t.setOnFocusChangeListener((v, focus) -> t.setBackground(shape(focus, dp)));
        t.setOnClickListener(v -> action.run());
        return t;
    }

    private static GradientDrawable shape(boolean focus, int dp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(focus ? 0xFF33444F : 0xFF202A31);
        d.setCornerRadius(dp * 10);
        d.setStroke(focus ? dp * 2 : dp, focus ? 0xFFFFFFFF : 0xFF53616B);
        return d;
    }

    private static TextView bigButton(Context c, String label, Runnable action, int vPad) {
        TextView t = button(c, label, action);
        // Fixed height, no vertical padding: the 18dp paddings inside the 56dp height left a 20dp
        // content box for a ~23dp line of text, clipping the label's bottom. Gravity centers instead.
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Math.round(280 * c.getResources().getDisplayMetrics().density), Math.round(60 * c.getResources().getDisplayMetrics().density));
        lp.topMargin = vPad;
        t.setLayoutParams(lp);
        return t;
    }

    private static GradientDrawable rounded(int color, int radiusPx) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radiusPx);
        return d;
    }
}
