package com.gathin.tvhome;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Random;

/**
 * Kids mode config: grade, session length, questions per checkpoint, the daily cap and the parent
 * gate's difficulty (independent of the grade). Reachable from the dropdown only while the mode is
 * off; every change applies at once. Also shows the one adb command that grants the overlay
 * permission, without which the quiz can only appear back on Home.
 */
public class KidModeSettingsActivity extends Activity {

    private boolean kidsOn;
    private KidModeSettings cfg;

    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));   // locale
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        rebuild();
    }

    private void rebuild() {
        kidsOn = KidModeSettings.load(this).enabled;
        cfg = KidModeSettings.load(this);
        SharedPreferences p = KidModeSettings.prefs(this);
        getWindow().getDecorView().setSystemUiVisibility(5894);
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(50), dp(25), dp(50), dp(30));
        root.setBackgroundColor(0xff101418);
        scroll.addView(root);
        setContentView(scroll);

        add(root, text("儿童模式", 27), 50);
        add(root, text("看满单次时长弹出题目，答对继续；每天累计到上限就锁定，只有家长能解锁。开启后，桌面只保留儿童模式入口。", 16), 60);

        long watched = p.getInt("watched_seconds", 0) / 60;
        String cap = cfg.dailyCapMinutes == 0 ? "不限" : cfg.dailyCapMinutes + " 分钟";
        add(root, text("今日已看 " + watched + " 分钟 · 每日上限 " + cap + " · 难度档 " + cfg.gateDifficulty, 15), 40);

        Switch on = new Switch(this);
        on.setText("启用儿童模式");
        on.setTextSize(22);
        on.setTextColor(Color.WHITE);
        on.setChecked(kidsOn);
        on.setEnabled(!kidsOn);
        if (kidsOn) {
            on.setText("儿童模式开启中");
            add(root, on, 56);
            add(root, text("关闭需要家长验证：回到桌面，点齿轮下拉菜单里的「儿童模式」。", 15), 40);
            add(root, button("前往家长验证", () -> {
                startActivity(new Intent(this, ParentGateActivity.class).putExtra(ParentGateActivity.EXTRA_MODE, ParentGateActivity.MODE_GATE));
                finish();
            }), 52);
        } else {
            on.setOnCheckedChangeListener((v, checked) -> {
                KidModeSettings.setEnabled(this, checked);
                Toast.makeText(this, checked ? "儿童模式已开启" : "已关闭，计时停止", Toast.LENGTH_SHORT).show();
                rebuild();
            });
            add(root, on, 56);
        }

        add(root, text("孩子年级（决定答题难度，随成长调整）", 16), 40);
        add(root, optionsRow(new String[]{"1", "2", "3", "4", "5", "6"}, cfg.grade - 1, i -> save(null, i + 1, null, null, null, null)), 56);

        add(root, text("单次时长（看满多久弹出题目）", 16), 40);
        add(root, optionsRow(new String[]{"5", "10", "15", "20", "30"}, indexOfMinutes(cfg.sessionMinutes), i -> save(null, null, minuteChoice(i), null, null, null)), 56);

        add(root, text("每次题数", 16), 40);
        add(root, optionsRow(new String[]{"1", "2", "3", "4", "5"}, cfg.questions - 1, i -> save(null, null, null, i + 1, null, null)), 56);

        add(root, text("每日上限（到点锁定，0 为不限）", 16), 40);
        add(root, optionsRow(new String[]{"30", "60", "90", "120", "不限"}, indexOfCap(cfg.dailyCapMinutes), i -> save(null, null, null, null, capChoice(i), null)), 56);

        add(root, text("家长门禁难度（关闭儿童模式 / 解锁用，独立于年级）", 16), 40);
        add(root, optionsRow(new String[]{"简单", "较难", "很难"}, cfg.gateDifficulty - 1, i -> save(null, null, null, null, null, i + 1)), 56);
        add(root, text("示例：" + KidQuiz.gate(new Random(), cfg.gateDifficulty).text + " = ?", 15), 40);

        add(root, text("悬浮窗授权（答题盖在视频上需要它）", 16), 40);
        if (KidOverlayController.canOverlay(this)) {
            add(root, text("已授权：到点直接弹出答题。", 15), 36);
        } else {
            add(root, text("未授权：到点只能在回到桌面时补弹。在电脑上执行一次：", 15), 40);
            add(root, text("adb shell appops set " + getPackageName() + " SYSTEM_ALERT_WINDOW allow", 14), 44);
        }
    }

    private void save(Boolean enabled, Integer grade, Integer session, Integer questions, Integer cap, Integer difficulty) {
        KidModeSettings.save(this,
                enabled != null ? enabled : kidsOn,
                grade != null ? grade : cfg.grade,
                session != null ? session : cfg.sessionMinutes,
                questions != null ? questions : cfg.questions,
                cap != null ? cap : cfg.dailyCapMinutes,
                difficulty != null ? difficulty : cfg.gateDifficulty);
        rebuild();
    }

    private int indexOfMinutes(int v) {
        int[] choices = {5, 10, 15, 20, 30};
        for (int i = 0; i < choices.length; i++) if (choices[i] == v) return i;
        return 1;
    }

    private int minuteChoice(int i) { return new int[]{5, 10, 15, 20, 30}[Math.max(0, Math.min(4, i))]; }

    private int indexOfCap(int v) {
        int[] choices = {30, 60, 90, 120, 0};
        for (int i = 0; i < choices.length; i++) if (choices[i] == v) return i;
        return 1;
    }

    private int capChoice(int i) { return new int[]{30, 60, 90, 120, 0}[Math.max(0, Math.min(4, i))]; }

    /** One selectable row of pill buttons; the selected one keeps the focused look. */
    private LinearLayout optionsRow(String[] labels, int selected, java.util.function.IntConsumer pick) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            TextView b = button(labels[i], () -> pick.accept(index));
            b.setBackground(KidModeSettingsActivity.this.shape(i == selected));
            row.addView(b, rowLp());
        }
        return row;
    }

    private LinearLayout.LayoutParams rowLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(84), dp(48));
        lp.setMargins(dp(5), 0, dp(5), 0);
        return lp;
    }

    // ---- the NasPhotoSettingsActivity widget style ---------------------------------------------------

    private TextView text(String s, int size) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(0xffE4E8EB);
        t.setTextSize(size);
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }

    private TextView button(String label, Runnable action) {
        TextView t = text(label, 19);
        t.setFocusable(true);
        t.setClickable(true);
        t.setGravity(Gravity.CENTER);
        t.setBackground(shape(false));
        t.setOnFocusChangeListener((v, focus) -> t.setBackground(shape(focus)));
        t.setOnClickListener(v -> action.run());
        return t;
    }

    private GradientDrawable shape(boolean focus) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(focus ? 0xff33444F : 0xff202A31);
        d.setCornerRadius(dp(10));
        d.setStroke(dp(focus ? 2 : 1), focus ? Color.WHITE : 0xff53616B);
        return d;
    }

    private void add(LinearLayout root, View v, int h) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(h));
        p.bottomMargin = dp(9);
        root.addView(v, p);
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
