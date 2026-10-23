package com.boxzero.app.ui;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.boxzero.app.R;

/** Shared frosted bottom navigation bar. Include view_bottom_nav in a layout, then call setup(). */
public class BottomNav {

    public static final int TAB_HOME = 0;
    public static final int TAB_FAV = 1;
    public static final int TAB_HISTORY = 2;
    public static final int TAB_LIVE = 3;
    public static final int TAB_MINE = 4;

    public static void setup(Activity activity, int selected) {
        View nav = activity.findViewById(R.id.bottomNav);
        if (nav == null) return;
        int[] tabs = {R.id.tabHome, R.id.tabFav, R.id.tabHistory, R.id.tabLive, R.id.tabMine};
        int[] icons = {R.id.iconHome, R.id.iconFav, R.id.iconHistory, R.id.iconLive, R.id.iconMine};
        int[] texts = {R.id.textHome, R.id.textFav, R.id.textHistory, R.id.textLive, R.id.textMine};
        for (int i = 0; i < tabs.length; i++) {
            View tab = nav.findViewById(tabs[i]);
            ImageView icon = nav.findViewById(icons[i]);
            TextView text = nav.findViewById(texts[i]);
            boolean sel = (i == selected);
            int color = sel ? Color.parseColor("#2E7CE6") : Color.parseColor("#8A8A8A");
            icon.setColorFilter(color);
            text.setTextColor(color);
            final int idx = i;
            tab.setOnClickListener(v -> go(activity, idx, selected));
        }
    }

    private static void go(Activity activity, int idx, int current) {
        if (idx == current) return;
        Intent it;
        switch (idx) {
            case TAB_FAV:
                it = new Intent(activity, RecordsActivity.class);
                it.putExtra(RecordsActivity.EXTRA_MODE, RecordsActivity.MODE_FAV);
                break;
            case TAB_HISTORY:
                it = new Intent(activity, RecordsActivity.class);
                it.putExtra(RecordsActivity.EXTRA_MODE, RecordsActivity.MODE_HISTORY);
                break;
            case TAB_LIVE:
                it = new Intent(activity, LiveActivity.class);
                break;
            case TAB_MINE:
                it = new Intent(activity, SettingsActivity.class);
                break;
            case TAB_HOME:
            default:
                it = new Intent(activity, MainActivity.class);
                break;
        }
        it.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        activity.startActivity(it);
        activity.overridePendingTransition(0, 0);
    }
}
