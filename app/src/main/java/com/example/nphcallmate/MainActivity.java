package com.example.nphcallmate;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import org.json.JSONObject;

public class MainActivity extends Activity {
    static final int REQ_RESTORE = 42;
    static final int GREEN = 0xFF0B5D3B;
    static final int BG = 0xFFEEF3F0;
    static final int LINE = 0xFFD5DFDA;
    static final int INK = 0xFF1E2B25;
    static final int FIELD = 0xFFF8FAF9;
    static final String APP = "NPH Aro Call Roster";
    static final String FOOTER = RosterEngine.FOOTER;
    static final String[] RANKS = {"Senior Staff 1", "Senior Staff 2", "Senior Staff 3",
            "Junior Staff 1", "Junior Staff 2", "Junior Staff 3", "Intern"};
    static final int[] RANK_VALUE = {6, 5, 4, 3, 2, 1, 0};
    static final String[] TYPES = {"Leave", "Day Off", "Excuse"};

    SharedPreferences sp;
    int selYear, selMonth;
    int seed = 1;
    List<String[]> staffEntries = new ArrayList<>();   // {name, rank index}
    List<String[]> excEntries = new ArrayList<>();     // {name, type, start, end}
    long durStart = -1, durEnd = -1;

    LinearLayout rosterView, historyView, helpView, histList, staffListBox, excListBox;
    Button tabRoster, tabHistory, tabHelp, monthBtn, durBtn, viewStaffBtn, viewExcBtn;
    CheckBox[] cbs = new CheckBox[3];
    EditText staffNameEt, restEt, defLeaveEt;
    AutoCompleteTextView excNameEt;
    Spinner rankSp, typeSp;
    ArrayAdapter<String> nameAdapter;
    TextView out, histOut;

    RosterEngine.Roster cur;
    String status = "";
    String lastPath = null;

    // ================================================================ UI
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences("nph_call_mate", MODE_PRIVATE);

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(30), dp(12), dp(18));
        sv.addView(root);

        TextView title = new TextView(this);
        title.setText(APP);
        title.setTextSize(25);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(GREEN);
        root.addView(title);

        TextView by = new TextView(this);
        by.setText("By Kelechi");
        by.setTextSize(10);
        by.setTextColor(0x80000000);
        by.setPadding(0, 0, 0, dp(8));
        root.addView(by);

        tabRoster = solid("Roster");
        tabHistory = outline("History");
        tabHelp = outline("Help");
        root.addView(row(tabRoster, tabHistory, tabHelp));

        rosterView = vbox(root);
        historyView = vbox(root);
        helpView = vbox(root);
        buildRosterTab();
        buildHistoryTab();
        buildHelpTab();

        TextView foot = new TextView(this);
        foot.setText(FOOTER);
        foot.setTextSize(11);
        foot.setTextColor(0xFF6B7A72);
        foot.setGravity(Gravity.CENTER);
        foot.setPadding(0, dp(18), 0, dp(6));
        root.addView(foot);

        tabRoster.setOnClickListener(v -> showTab(0));
        tabHistory.setOnClickListener(v -> showTab(1));
        tabHelp.setOnClickListener(v -> showTab(2));

        setContentView(sv);
        loadSettings();
        loadEntries();
        refreshStaffList();
        refreshExcList();
        refreshNames();
        showTab(0);
    }

    void buildRosterTab() {
        // ---- month
        LinearLayout c1 = card(rosterView, "Month");
        monthBtn = outline("");
        monthBtn.setLayoutParams(fullParams());
        monthBtn.setOnClickListener(v -> pickMonth());
        c1.addView(monthBtn);

        // ---- columns
        LinearLayout c2 = card(rosterView, "Call columns needed this month");
        String[] cbText = {"1st on Call (Interns)", "2nd on Call (Junior staff)", "3rd on Call (Senior staff)"};
        for (int i = 0; i < 3; i++) {
            cbs[i] = new CheckBox(this);
            cbs[i].setText(cbText[i]);
            cbs[i].setTextColor(INK);
            cbs[i].setChecked(true);
            c2.addView(cbs[i]);
        }

        // ---- staff entry
        LinearLayout c3 = card(rosterView, "Staff Entry");
        c3.addView(capRow(new String[]{"Staff name", "Rank", ""}, new float[]{1.3f, 1.2f, 0.7f}));
        staffNameEt = new EditText(this);
        styleInput(staffNameEt, "Name");
        staffNameEt.setLayoutParams(wp(1.3f));
        rankSp = spinner(RANKS);
        rankSp.setLayoutParams(wp(1.2f));
        Button addStaff = solid("Add");
        addStaff.setLayoutParams(wp(0.7f));
        addStaff.setOnClickListener(v -> addStaff());
        c3.addView(row(staffNameEt, rankSp, addStaff));
        viewStaffBtn = outline("View Staff Entry");
        viewStaffBtn.setLayoutParams(fullParams());
        c3.addView(viewStaffBtn);
        staffListBox = vbox(c3);
        staffListBox.setVisibility(View.GONE);
        viewStaffBtn.setOnClickListener(v -> toggle(staffListBox));

        // ---- exception entry
        LinearLayout c4 = card(rosterView, "Exception Entry");
        c4.addView(capRow(new String[]{"Staff name", "Exception Type", "Duration", ""},
                new float[]{1.2f, 1.0f, 1.2f, 0.7f}));
        excNameEt = new AutoCompleteTextView(this);
        styleInput(excNameEt, "Name");
        excNameEt.setThreshold(1);
        excNameEt.setLayoutParams(wp(1.2f));
        nameAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_dropdown_item_1line, new ArrayList<String>());
        excNameEt.setAdapter(nameAdapter);
        typeSp = spinner(TYPES);
        typeSp.setLayoutParams(wp(1.0f));
        durBtn = outline("Duration");
        durBtn.setTextSize(10);
        durBtn.setLayoutParams(wp(1.2f));
        durBtn.setOnClickListener(v -> pickDuration());
        Button addExc = solid("Add");
        addExc.setLayoutParams(wp(0.7f));
        addExc.setOnClickListener(v -> addException());
        c4.addView(row(excNameEt, typeSp, durBtn, addExc));
        viewExcBtn = outline("View Exception Entry");
        viewExcBtn.setLayoutParams(fullParams());
        c4.addView(viewExcBtn);
        excListBox = vbox(c4);
        excListBox.setVisibility(View.GONE);
        viewExcBtn.setOnClickListener(v -> toggle(excListBox));

        // ---- rules
        LinearLayout c5 = card(rosterView, "Rules");
        c5.addView(cap("Off-duty days after each call (same for everyone)"));
        restEt = new EditText(this);
        styleInput(restEt, "1");
        restEt.setInputType(InputType.TYPE_CLASS_NUMBER);
        c5.addView(restEt);
        c5.addView(cap("Default leave length (working days)"));
        defLeaveEt = new EditText(this);
        styleInput(defLeaveEt, "15");
        defLeaveEt.setInputType(InputType.TYPE_CLASS_NUMBER);
        c5.addView(defLeaveEt);

        // ---- actions
        LinearLayout c6 = card(rosterView, "Create and save");
        Button gen = solid("Generate");
        Button shuffle = outline("Shuffle");
        gen.setOnClickListener(v -> generate());
        shuffle.setOnClickListener(v -> { seed++; generate(); });
        c6.addView(row(gen, shuffle));
        c6.addView(exportRow());
        Button backup = outline("Backup");
        Button restore = outline("Restore");
        backup.setOnClickListener(v -> doBackup());
        restore.setOnClickListener(v -> doRestore());
        c6.addView(row(backup, restore));

        // ---- roster output
        LinearLayout c7 = card(rosterView, "Call Duty Roster");
        out = mono();
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.addView(out);
        c7.addView(hs);
    }

    void buildHistoryTab() {
        LinearLayout c1 = card(historyView, "History (approved rosters only)");
        TextView t = new TextView(this);
        t.setText("A roster is added here when you tap Save PDF, Save Image or Share. "
                + "Only the latest save for each month is kept, and new months are worked out from it.");
        t.setTextSize(12);
        t.setTextColor(INK);
        c1.addView(t);
        histList = vbox(c1);
        LinearLayout c2 = card(historyView, "Selected roster");
        c2.addView(exportRow());
        histOut = mono();
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.addView(histOut);
        c2.addView(hs);
    }

    void buildHelpTab() {
        LinearLayout c = card(helpView, "Help");
        TextView t = new TextView(this);
        t.setText(HelpText.HELP);
        t.setTextSize(13);
        t.setTextColor(INK);
        t.setLineSpacing(0f, 1.15f);
        t.setTextIsSelectable(true);
        c.addView(t);
    }

    LinearLayout exportRow() {
        Button pdf = solid("Save PDF");
        Button img = solid("Save Image");
        Button share = outline("Share");
        pdf.setOnClickListener(v -> savePdf());
        img.setOnClickListener(v -> saveImage(false));
        share.setOnClickListener(v -> saveImage(true));
        return row(pdf, img, share);
    }

    void showTab(int t) {
        rosterView.setVisibility(t == 0 ? View.VISIBLE : View.GONE);
        historyView.setVisibility(t == 1 ? View.VISIBLE : View.GONE);
        helpView.setVisibility(t == 2 ? View.VISIBLE : View.GONE);
        styleTab(tabRoster, t == 0);
        styleTab(tabHistory, t == 1);
        styleTab(tabHelp, t == 2);
        if (t == 1) refreshHistory();
    }

    void styleTab(Button b, boolean active) {
        b.setBackground(shape(active ? GREEN : Color.WHITE, GREEN, 10));
        b.setTextColor(active ? Color.WHITE : GREEN);
    }

    void toggle(LinearLayout box) {
        box.setVisibility(box.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
    }

    // ------------------------------------------------------- UI helpers
    int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    GradientDrawable shape(int fill, int stroke, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radiusDp));
        if (stroke != 0) g.setStroke(dp(1), stroke);
        return g;
    }

    LinearLayout.LayoutParams fullParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(2), dp(4), dp(2), dp(2));
        return lp;
    }

    LinearLayout.LayoutParams wp(float weight) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight);
        lp.setMargins(dp(2), dp(2), dp(2), dp(2));
        return lp;
    }

    LinearLayout vbox(LinearLayout parent) {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        parent.addView(v);
        return v;
    }

    LinearLayout card(LinearLayout parent, String heading) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(shape(Color.WHITE, LINE, 12));
        c.setPadding(dp(12), dp(10), dp(12), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(8), 0, 0);
        c.setLayoutParams(lp);
        TextView h = new TextView(this);
        h.setText(heading.toUpperCase(Locale.ROOT));
        h.setTextSize(12);
        h.setTypeface(Typeface.DEFAULT_BOLD);
        h.setTextColor(GREEN);
        c.addView(h);
        View line = new View(this);
        line.setBackgroundColor(LINE);
        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        ll.setMargins(0, dp(6), 0, dp(8));
        line.setLayoutParams(ll);
        c.addView(line);
        parent.addView(c);
        return c;
    }

    TextView cap(String t) {
        TextView tv = new TextView(this);
        tv.setText(t);
        tv.setTextSize(11);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setTextColor(0xFF4A5A52);
        tv.setPadding(dp(2), dp(8), 0, dp(3));
        return tv;
    }

    LinearLayout capRow(String[] names, float[] weights) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < names.length; i++) {
            TextView tv = new TextView(this);
            tv.setText(names[i]);
            tv.setTextSize(10);
            tv.setTypeface(Typeface.DEFAULT_BOLD);
            tv.setTextColor(0xFF4A5A52);
            tv.setLayoutParams(wp(weights[i]));
            r.addView(tv);
        }
        return r;
    }

    void styleInput(EditText et, String hint) {
        et.setHint(hint);
        et.setTextSize(13);
        et.setTextColor(INK);
        et.setHintTextColor(0xFF9AA8A0);
        et.setSingleLine(true);
        et.setBackground(shape(FIELD, LINE, 8));
        et.setPadding(dp(8), dp(9), dp(8), dp(9));
    }

    TextView mono() {
        TextView t = new TextView(this);
        t.setTypeface(Typeface.MONOSPACE);
        t.setTextSize(12);
        t.setTextColor(INK);
        t.setTextIsSelectable(true);
        return t;
    }

    Button baseBtn(String t) {
        Button b = new Button(this);
        b.setText(t);
        b.setAllCaps(false);
        b.setTextSize(13);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(dp(6), dp(9), dp(6), dp(9));
        b.setLayoutParams(wp(1f));
        return b;
    }

    Button solid(String t) {
        Button b = baseBtn(t);
        b.setBackground(shape(GREEN, 0, 10));
        b.setTextColor(Color.WHITE);
        return b;
    }

    Button outline(String t) {
        Button b = baseBtn(t);
        b.setBackground(shape(Color.WHITE, GREEN, 10));
        b.setTextColor(GREEN);
        return b;
    }

    LinearLayout row(View... vs) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        for (View x : vs) r.addView(x);
        return r;
    }

    Spinner spinner(String[] items) {
        Spinner s = new Spinner(this);
        ArrayAdapter<String> ad = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, items) {
            @Override
            public View getView(int pos, View cv, ViewGroup parent) {
                View v = super.getView(pos, cv, parent);
                ((TextView) v).setTextSize(12);
                return v;
            }
        };
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        s.setAdapter(ad);
        s.setBackground(shape(FIELD, LINE, 8));
        s.setPadding(dp(4), dp(7), dp(4), dp(7));
        return s;
    }

    void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }

    String cleanName(String s) {
        return s.replaceAll("[|,\\r\\n]", " ").replaceAll("\\s+", " ").trim();
    }

    int readInt(EditText et, int def, int min) {
        try {
            int v = Integer.parseInt(et.getText().toString().trim());
            return Math.max(v, min);
        } catch (Exception e) {
            return def;
        }
    }

    // ------------------------------------------------- settings / entries
    void loadSettings() {
        Calendar now = Calendar.getInstance();
        selYear = now.get(Calendar.YEAR);
        selMonth = now.get(Calendar.MONTH) + 1;
        String m = sp.getString("month", "");
        try {
            String[] t = m.split("-");
            int y = Integer.parseInt(t[0]);
            int mo = Integer.parseInt(t[1]);
            if (mo >= 1 && mo <= 12) {
                selYear = y;
                selMonth = mo;
            }
        } catch (Exception e) {
            // keep the current month
        }
        restEt.setText(sp.getString("rest", "1"));
        defLeaveEt.setText(sp.getString("defleave", "15"));
        for (int i = 0; i < 3; i++) cbs[i].setChecked(sp.getBoolean("act" + i, true));
        seed = sp.getInt("seed", 1);
        updateMonthText();
    }

    void saveSettings() {
        SharedPreferences.Editor ed = sp.edit();
        ed.putString("month", String.format(Locale.US, "%04d-%02d", selYear, selMonth));
        ed.putString("rest", restEt.getText().toString());
        ed.putString("defleave", defLeaveEt.getText().toString());
        for (int i = 0; i < 3; i++) ed.putBoolean("act" + i, cbs[i].isChecked());
        ed.putInt("seed", seed);
        ed.apply();
    }

    void loadEntries() {
        staffEntries.clear();
        excEntries.clear();
        for (String line : sp.getString("staff_entries", "").split("\n")) {
            String[] f = line.split("\\|", -1);
            if (f.length >= 2 && !f[0].isEmpty()) staffEntries.add(new String[]{f[0], f[1]});
        }
        for (String line : sp.getString("exc_entries", "").split("\n")) {
            String[] f = line.split("\\|", -1);
            if (f.length >= 4 && !f[0].isEmpty()) excEntries.add(new String[]{f[0], f[1], f[2], f[3]});
        }
    }

    void saveEntries() {
        StringBuilder a = new StringBuilder();
        for (String[] e : staffEntries) a.append(e[0]).append("|").append(e[1]).append("\n");
        StringBuilder b = new StringBuilder();
        for (String[] e : excEntries) {
            b.append(e[0]).append("|").append(e[1]).append("|").append(e[2]).append("|").append(e[3]).append("\n");
        }
        sp.edit().putString("staff_entries", a.toString()).putString("exc_entries", b.toString()).apply();
    }

    void updateMonthText() {
        monthBtn.setText(RosterEngine.monthName(selMonth) + " " + selYear + "     (tap to change)");
    }

    void pickMonth() {
        DatePickerDialog dlg = new DatePickerDialog(this, (view, yy, mm, dd) -> {
            selYear = yy;
            selMonth = mm + 1;
            updateMonthText();
            saveSettings();
        }, selYear, selMonth - 1, 1);
        dlg.setTitle("Pick any date in the month");
        dlg.show();
    }

    // -------------------------------------------------------- staff entry
    int findStaff(String name) {
        for (int i = 0; i < staffEntries.size(); i++) {
            if (staffEntries.get(i)[0].equalsIgnoreCase(name)) return i;
        }
        return -1;
    }

    void addStaff() {
        String name = cleanName(staffNameEt.getText().toString());
        if (name.isEmpty()) {
            toast("Type the person's name first.");
            return;
        }
        if (findStaff(name) >= 0) {
            toast(name + " is already in Staff Entry.");
            return;
        }
        staffEntries.add(new String[]{name, String.valueOf(rankSp.getSelectedItemPosition())});
        saveEntries();
        staffNameEt.setText("");
        rankSp.setSelection(0);
        refreshStaffList();
        refreshNames();
    }

    void refreshNames() {
        nameAdapter.clear();
        for (String[] e : staffEntries) nameAdapter.add(e[0]);
    }

    void refreshStaffList() {
        staffListBox.removeAllViews();
        viewStaffBtn.setText("View Staff Entry (" + staffEntries.size() + ")");
        if (staffEntries.isEmpty()) {
            staffListBox.addView(cap("No staff added yet."));
            return;
        }
        for (int i = 0; i < staffEntries.size(); i++) {
            final String[] e = staffEntries.get(i);
            int idx = 0;
            try {
                idx = Integer.parseInt(e[1]);
            } catch (Exception ex) {
                idx = 0;
            }
            TextView t = new TextView(this);
            t.setText(e[0] + "  -  " + RANKS[Math.max(0, Math.min(idx, RANKS.length - 1))]);
            t.setTextSize(13);
            t.setTextColor(INK);
            t.setLayoutParams(wp(1f));
            Button rm = outline("Remove");
            rm.setTextSize(11);
            rm.setLayoutParams(wp(0.4f));
            rm.setOnClickListener(v -> confirmRemove(e[0], () -> {
                staffEntries.remove(e);
                saveEntries();
                refreshStaffList();
                refreshNames();
            }));
            staffListBox.addView(row(t, rm));
        }
    }

    void confirmRemove(String what, Runnable action) {
        new AlertDialog.Builder(this)
                .setTitle("Remove")
                .setMessage("Remove " + what + "?")
                .setPositiveButton("Remove", (di, which) -> action.run())
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ---------------------------------------------------- exception entry
    void updateDurText() {
        if (durStart < 0) {
            durBtn.setText("Duration");
        } else if (durEnd < 0) {
            durBtn.setText(RosterEngine.iso(durStart) + "\n(default length)");
        } else {
            durBtn.setText(RosterEngine.iso(durStart) + "\nto " + RosterEngine.iso(durEnd));
        }
    }

    void pickDuration() {
        long base = durStart >= 0 ? durStart : RosterEngine.dayNum(selYear, selMonth, 1);
        int[] a = RosterEngine.ymd(base);
        DatePickerDialog dlg = new DatePickerDialog(this, (view, yy, mm, dd) -> {
            durStart = RosterEngine.dayNum(yy, mm + 1, dd);
            durEnd = -1;
            updateDurText();
            askEnd();
        }, a[0], a[1] - 1, a[2]);
        dlg.setTitle("Start date");
        dlg.show();
    }

    void askEnd() {
        int defLeave = readInt(defLeaveEt, 15, 1);
        new AlertDialog.Builder(this)
                .setTitle("End date")
                .setMessage("Pick an end date, or keep the default length (Leave: " + defLeave
                        + " working days. Day Off or Excuse: 1 day).")
                .setPositiveButton("Pick end date", (di, which) -> pickEnd())
                .setNegativeButton("Use default", null)
                .show();
    }

    void pickEnd() {
        int[] a = RosterEngine.ymd(durStart);
        DatePickerDialog dlg = new DatePickerDialog(this, (view, yy, mm, dd) -> {
            long end = RosterEngine.dayNum(yy, mm + 1, dd);
            if (end < durStart) {
                toast("The end date is before the start date. Default length kept.");
                durEnd = -1;
            } else {
                durEnd = end;
            }
            updateDurText();
        }, a[0], a[1] - 1, a[2]);
        dlg.setTitle("End date");
        dlg.show();
    }

    void addException() {
        String typed = cleanName(excNameEt.getText().toString());
        if (typed.isEmpty()) {
            toast("Type the person's name first.");
            return;
        }
        int idx = findStaff(typed);
        if (idx < 0) {
            toast(typed + " is not in Staff Entry yet. Add the person there first.");
            return;
        }
        String name = staffEntries.get(idx)[0];
        String type = TYPES[typeSp.getSelectedItemPosition()];
        int defLeave = readInt(defLeaveEt, 15, 1);
        long s = durStart >= 0 ? durStart : RosterEngine.dayNum(selYear, selMonth, 1);
        long end;
        if (durEnd >= 0) end = durEnd;
        else if (type.equals("Leave")) end = RosterEngine.endFromWorkdays(s, defLeave);
        else end = s;
        excEntries.add(new String[]{name, type, RosterEngine.iso(s), RosterEngine.iso(end)});
        saveEntries();
        excNameEt.setText("");
        typeSp.setSelection(0);
        durStart = -1;
        durEnd = -1;
        updateDurText();
        refreshExcList();
        toast(type + " added: " + name + ", " + RosterEngine.iso(s) + " to " + RosterEngine.iso(end));
    }

    void refreshExcList() {
        excListBox.removeAllViews();
        viewExcBtn.setText("View Exception Entry (" + excEntries.size() + ")");
        if (excEntries.isEmpty()) {
            excListBox.addView(cap("No exceptions added yet."));
            return;
        }
        for (int i = 0; i < excEntries.size(); i++) {
            final String[] e = excEntries.get(i);
            TextView t = new TextView(this);
            t.setText(e[0] + "\n" + e[1] + ": " + e[2] + " to " + e[3]);
            t.setTextSize(13);
            t.setTextColor(INK);
            t.setLayoutParams(wp(1f));
            Button rm = outline("Remove");
            rm.setTextSize(11);
            rm.setLayoutParams(wp(0.4f));
            rm.setOnClickListener(v -> confirmRemove(e[1] + " for " + e[0], () -> {
                excEntries.remove(e);
                saveEntries();
                refreshExcList();
            }));
            excListBox.addView(row(t, rm));
        }
    }

    // ----------------------------------------------------------- history
    String hkey(int y, int m) {
        return String.format(Locale.US, "h_%04d-%02d", y, m);
    }

    TreeSet<String> historyKeys() {
        TreeSet<String> keys = new TreeSet<>();
        for (String k : sp.getAll().keySet()) {
            if (k.startsWith("h_")) keys.add(k);
        }
        return keys;
    }

    void refreshHistory() {
        histList.removeAllViews();
        TreeSet<String> keys = historyKeys();
        if (keys.isEmpty()) {
            histList.addView(cap("Nothing approved yet."));
            return;
        }
        for (String k : keys.descendingSet()) {
            final String key = k;
            try {
                RosterEngine.Roster r = RosterEngine.parse(sp.getString(key, ""));
                Button bt = outline(RosterEngine.monthName(r.month) + " " + r.year + "   (saved " + r.stamp + ")");
                bt.setLayoutParams(fullParams());
                bt.setOnClickListener(v -> openHistory(key));
                histList.addView(bt);
            } catch (Exception e) {
                // skip unreadable entry
            }
        }
    }

    void openHistory(String key) {
        try {
            cur = RosterEngine.parse(sp.getString(key, ""));
            status = "Status: APPROVED (in History, saved " + cur.stamp + ")";
            showCurrent();
        } catch (Exception e) {
            toast("Could not open that roster.");
        }
    }

    void showCurrent() {
        if (cur == null) return;
        String t = RosterEngine.render(cur, status);
        out.setText(t);
        histOut.setText(t);
    }

    void approve() {
        cur.stamp = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date());
        sp.edit().putString(hkey(cur.year, cur.month), RosterEngine.serialize(cur)).apply();
        status = "Status: APPROVED (saved to History " + cur.stamp + ")";
        showCurrent();
        refreshHistory();
    }

    // -------------------------------------------------------- generation
    void generate() {
        try {
            if (staffEntries.isEmpty()) {
                out.setText("Add people in Staff Entry first.");
                return;
            }
            int rest = readInt(restEt, 1, 0);
            boolean[] act = {cbs[0].isChecked(), cbs[1].isChecked(), cbs[2].isChecked()};
            saveSettings();

            StringBuilder internsT = new StringBuilder();
            StringBuilder staffT = new StringBuilder();
            for (String[] e : staffEntries) {
                int idx = Integer.parseInt(e[1]);
                if (idx >= RANKS.length - 1) {
                    internsT.append(e[0]).append("\n");
                } else {
                    staffT.append(e[0]).append(", ").append(RANK_VALUE[idx]).append("\n");
                }
            }

            List<RosterEngine.Lv> entered = new ArrayList<>();
            for (String[] e : excEntries) {
                try {
                    entered.add(new RosterEngine.Lv(e[0], RosterEngine.parseDate(e[2]),
                            RosterEngine.parseDate(e[3]), e[1]));
                } catch (Exception ex) {
                    // skip a damaged entry
                }
            }

            int py = selMonth == 1 ? selYear - 1 : selYear;
            int pm = selMonth == 1 ? 12 : selMonth - 1;
            RosterEngine.Roster prev = null;
            String ps = sp.getString(hkey(py, pm), null);
            if (ps != null) prev = RosterEngine.parse(ps);

            List<RosterEngine.Lv> older = new ArrayList<>();
            String thisKey = hkey(selYear, selMonth);
            for (String k : historyKeys()) {
                if (k.compareTo(thisKey) < 0) {
                    try {
                        older.addAll(RosterEngine.parse(sp.getString(k, "")).leaves);
                    } catch (Exception ex) {
                        // ignore unreadable entry
                    }
                }
            }

            cur = RosterEngine.generate(selYear, selMonth, act, internsT.toString(), staffT.toString(),
                    entered, rest, prev, older, seed);
            status = "Status: DRAFT. It goes to History when you tap Save PDF, Save Image or Share.";
            showCurrent();
        } catch (IllegalArgumentException e) {
            out.setText(e.getMessage() == null ? "Please check your entries." : e.getMessage());
        } catch (Exception e) {
            out.setText("Please check your entries and settings.");
        }
    }

    // ------------------------------------------------- PDF / image export
    String fit(String s, Paint p, float w) {
        if (p.measureText(s) <= w) return s;
        int n = p.breakText(s, true, w - p.measureText("..."), null);
        return s.substring(0, Math.max(0, n)) + "...";
    }

    void drawRoster(Canvas c) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint fill = new Paint();
        fill.setStyle(Paint.Style.FILL);
        Paint line = new Paint();
        line.setStyle(Paint.Style.STROKE);
        line.setColor(0xFFBBBBBB);
        line.setStrokeWidth(0.6f);

        c.drawColor(Color.WHITE);
        float left = 30, top = 92, tw = 535, rowH = 20, headH = 22, dateW = 62;

        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setTextSize(20);
        p.setColor(GREEN);
        c.drawText(APP, left, 40, p);
        p.setTypeface(Typeface.DEFAULT);
        p.setTextSize(8);
        p.setColor(0x80000000);
        c.drawText("By Kelechi", left, 52, p);
        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setTextSize(13);
        p.setColor(Color.BLACK);
        c.drawText("Call Duty Roster - " + RosterEngine.monthName(cur.month) + " " + cur.year, left, 76, p);

        int n = 0;
        int[] cols = new int[3];
        for (int i = 0; i < 3; i++) if (cur.act[i]) cols[n++] = i;
        float colW = (tw - dateW) / Math.max(1, n);

        fill.setColor(GREEN);
        c.drawRect(left, top, left + tw, top + headH, fill);
        p.setTextSize(10);
        p.setColor(Color.WHITE);
        c.drawText("Date", left + 5, top + 15, p);
        for (int k = 0; k < n; k++) {
            c.drawText(RosterEngine.TITLES[cols[k]], left + dateW + k * colW + 5, top + 15, p);
        }

        p.setTypeface(Typeface.DEFAULT);
        p.setColor(Color.BLACK);
        for (int d = 1; d <= cur.days; d++) {
            float y = top + headH + (d - 1) * rowH;
            String dw = RosterEngine.dow(cur, d);
            if (dw.equals("Sat") || dw.equals("Sun")) {
                fill.setColor(0xFFF0F0F0);
                c.drawRect(left, y, left + tw, y + rowH, fill);
            }
            c.drawRect(left, y, left + dateW, y + rowH, line);
            c.drawText(d + " " + dw, left + 5, y + 14, p);
            for (int k = 0; k < n; k++) {
                float x = left + dateW + k * colW;
                c.drawRect(x, y, x + colW, y + rowH, line);
                c.drawText(fit(cur.rows[d][cols[k]], p, colW - 8), x + 5, y + 14, p);
            }
        }

        p.setTextSize(9);
        p.setColor(0xFF666666);
        p.setTextAlign(Paint.Align.CENTER);
        c.drawText(FOOTER, 595 / 2f, 810, p);
    }

    String fileBase() {
        return String.format(Locale.US, "NPH_Aro_Call_Roster_%04d-%02d", cur.year, cur.month);
    }

    Uri saveMedia(String name, String mime, byte[] data, boolean image) {
        lastPath = null;
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                ContentResolver res = getContentResolver();
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                cv.put(MediaStore.MediaColumns.MIME_TYPE, mime);
                cv.put(MediaStore.MediaColumns.RELATIVE_PATH,
                        image ? Environment.DIRECTORY_PICTURES + "/NPHAroCallRoster" : Environment.DIRECTORY_DOWNLOADS);
                Uri base = image ? MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                                 : MediaStore.Downloads.EXTERNAL_CONTENT_URI;
                Uri u = res.insert(base, cv);
                if (u == null) return null;
                OutputStream os = res.openOutputStream(u);
                os.write(data);
                os.close();
                return u;
            } else {
                File dir = getExternalFilesDir(null);
                if (dir == null) dir = getFilesDir();
                File f = new File(dir, name);
                FileOutputStream fo = new FileOutputStream(f);
                fo.write(data);
                fo.close();
                lastPath = f.getAbsolutePath();
                return null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    boolean reportSaved(Uri u, String where) {
        if (u != null) {
            toast("Saved in " + where + " and added to History");
            return true;
        }
        if (lastPath != null) {
            toast("Saved: " + lastPath + " (added to History)");
            return true;
        }
        toast("Could not save the file.");
        return false;
    }

    void savePdf() {
        if (cur == null) {
            toast("Generate a roster or open one from History first.");
            return;
        }
        try {
            PdfDocument doc = new PdfDocument();
            PdfDocument.PageInfo pi = new PdfDocument.PageInfo.Builder(595, 842, 1).create();
            PdfDocument.Page pg = doc.startPage(pi);
            drawRoster(pg.getCanvas());
            doc.finishPage(pg);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            doc.writeTo(bos);
            doc.close();
            Uri u = saveMedia(fileBase() + ".pdf", "application/pdf", bos.toByteArray(), false);
            if (reportSaved(u, "Downloads")) approve();
        } catch (Exception e) {
            toast("Could not create the PDF.");
        }
    }

    void saveImage(boolean share) {
        if (cur == null) {
            toast("Generate a roster or open one from History first.");
            return;
        }
        try {
            Bitmap bm = Bitmap.createBitmap(1190, 1684, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(bm);
            cv.scale(2f, 2f);
            drawRoster(cv);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            bm.compress(Bitmap.CompressFormat.PNG, 100, bos);
            Uri u = saveMedia(fileBase() + ".png", "image/png", bos.toByteArray(), true);
            boolean ok = reportSaved(u, "Pictures/NPHAroCallRoster");
            if (ok) approve();
            if (share && u != null) {
                Intent s = new Intent(Intent.ACTION_SEND);
                s.setType("image/png");
                s.putExtra(Intent.EXTRA_STREAM, u);
                s.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(s, "Share roster"));
            }
        } catch (Exception e) {
            toast("Could not create the image.");
        }
    }

    // ------------------------------------------------- backup / restore
    void doBackup() {
        try {
            saveSettings();
            JSONObject o = new JSONObject();
            Map<String, ?> all = sp.getAll();
            for (Map.Entry<String, ?> e : all.entrySet()) o.put(e.getKey(), e.getValue());
            String stamp = new SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(new Date());
            Uri u = saveMedia("NPH_Aro_Call_Roster_backup_" + stamp + ".json", "application/json",
                    o.toString(2).getBytes("UTF-8"), false);
            if (u != null) toast("Backup saved in Downloads");
            else if (lastPath != null) toast("Backup saved: " + lastPath);
            else toast("Backup failed.");
        } catch (Exception e) {
            toast("Backup failed.");
        }
    }

    void doRestore() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        startActivityForResult(i, REQ_RESTORE);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_RESTORE || res != RESULT_OK || data == null || data.getData() == null) return;
        try {
            InputStream is = getContentResolver().openInputStream(data.getData());
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
            is.close();
            JSONObject o = new JSONObject(bos.toString("UTF-8"));
            SharedPreferences.Editor ed = sp.edit();
            Iterator<String> it = o.keys();
            int count = 0;
            while (it.hasNext()) {
                String k = it.next();
                Object v = o.get(k);
                if (v instanceof String) ed.putString(k, (String) v);
                else if (v instanceof Integer) ed.putInt(k, (Integer) v);
                else if (v instanceof Boolean) ed.putBoolean(k, (Boolean) v);
                else continue;
                count++;
            }
            if (count == 0) throw new Exception("empty");
            ed.apply();
            loadSettings();
            loadEntries();
            refreshStaffList();
            refreshExcList();
            refreshNames();
            refreshHistory();
            out.setText("Restore complete. Your staff, exceptions, settings and History are back.");
        } catch (Exception e) {
            toast("That is not a valid backup file.");
        }
    }
}
