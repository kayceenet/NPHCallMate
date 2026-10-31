package com.example.nphcallmate;

import java.text.DateFormatSymbols;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeSet;

/** Pure Java roster logic (no Android classes). */
public class RosterEngine {
    public static final String[] TITLES = {"1st on Call", "2nd on Call", "3rd on Call"};
    public static final String[] DOW = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
    public static final String FOOTER = "Created by Kelechi N.W. 08164389966";
    static final double PAIR_TOL = 1.0;
    static final int SENIOR_MIN = 4;
    /** Built-in rule by weekday of the call (0=Sun..6=Sat): Friday call = off next Wednesday, Saturday call = off next Tuesday. */
    static final int[] POST_CALL_OFF = {-1, -1, -1, -1, -1, 3, 2};

    public static class Lv {
        public String name;
        public long s, e;
        public String type;

        public Lv(String name, long s, long e, String type) {
            this.name = name;
            this.s = s;
            this.e = e;
            this.type = type;
        }

        public String id() {
            return name.toLowerCase(Locale.ROOT) + "|" + s + "|" + e;
        }
    }

    public static class Roster {
        public int year, month, days;
        public boolean[] act = new boolean[3];
        public String[][] rows;
        public List<Lv> leaves = new ArrayList<>();
        public String stamp = "";
        public String notes = "";
    }

    static class P {
        String name;
        int rank;
        int total;
        int last = -99;
        boolean block;
        List<Lv> lv = new ArrayList<>();
        Set<Long> off = new HashSet<>();
        Map<P, Integer> pairs = new HashMap<>();
    }

    // ------------------------------------------------------------ dates
    static TimeZone utc() {
        return TimeZone.getTimeZone("UTC");
    }

    public static long dayNum(int y, int m, int d) {
        Calendar c = new GregorianCalendar(utc());
        c.clear();
        c.set(y, m - 1, d);
        return c.getTimeInMillis() / 86400000L;
    }

    public static int[] ymd(long dn) {
        Calendar c = new GregorianCalendar(utc());
        c.setTimeInMillis(dn * 86400000L);
        return new int[]{c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH)};
    }

    public static String iso(long dn) {
        int[] a = ymd(dn);
        return String.format(Locale.US, "%04d-%02d-%02d", a[0], a[1], a[2]);
    }

    /** 0 = Sunday ... 6 = Saturday */
    public static int wd(long dn) {
        return (int) (((dn + 4) % 7 + 7) % 7);
    }

    public static int dim(int y, int m) {
        return new GregorianCalendar(y, m - 1, 1).getActualMaximum(Calendar.DAY_OF_MONTH);
    }

    public static String monthName(int m) {
        return new DateFormatSymbols(Locale.ENGLISH).getMonths()[m - 1];
    }

    public static long parseDate(String s) {
        String[] t = s.trim().split("[-/.]");
        if (t.length != 3) throw new IllegalArgumentException("date");
        int a = Integer.parseInt(t[0].trim());
        int b = Integer.parseInt(t[1].trim());
        int c = Integer.parseInt(t[2].trim());
        int y, m, d;
        if (t[0].trim().length() == 4) {
            y = a; m = b; d = c;
        } else {
            d = a; m = b; y = c;
        }
        if (y < 100) y += 2000;
        if (m < 1 || m > 12 || d < 1 || d > 31) throw new IllegalArgumentException("date");
        return dayNum(y, m, d);
    }

    /** Last day of a leave that covers n working days (Mon-Fri), counting the start date. */
    public static long endFromWorkdays(long start, int n) {
        if (n < 1) n = 1;
        long dn = start;
        int count = 0;
        while (true) {
            int w = wd(dn);
            if (w >= 1 && w <= 5) {
                count++;
                if (count >= n) return dn;
            }
            dn++;
        }
    }

    // ---------------------------------------------------------- parsing
    static String clean(String s) {
        return s.replaceAll("[|\\r\\n]", " ").trim();
    }

    static List<P> parsePeople(String text, boolean withRank) {
        List<P> list = new ArrayList<>();
        String regex = withRank ? "\\n" : "[\\n,]";
        for (String raw : text.split(regex)) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            P p = new P();
            p.name = clean(line);
            if (withRank) {
                int i = line.lastIndexOf(',');
                if (i > 0) {
                    try {
                        p.rank = Integer.parseInt(line.substring(i + 1).trim());
                        p.name = clean(line.substring(0, i));
                    } catch (NumberFormatException e) {
                        // no seniority number: whole line is the name
                    }
                }
            }
            if (!p.name.isEmpty()) list.add(p);
        }
        return list;
    }

    // ------------------------------------------------------- generation
    static boolean less(double[] a, double[] b) {
        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) return a[i] < b[i];
        }
        return false;
    }

    static boolean onLeave(P p, long dn) {
        for (Lv l : p.lv) if (dn >= l.s && dn <= l.e) return true;
        return false;
    }

    static void addOff(P p, long dn) {
        int w = wd(dn);
        int o = POST_CALL_OFF[w];
        if (o < 0) return;
        int k = (o - w + 7) % 7;
        if (k == 0) k = 7;
        p.off.add(dn + k);
    }

    static void mark(P p, int d, long dn, Set<P> used) {
        p.total++;
        p.last = d;
        used.add(p);
        addOff(p, dn);
    }

    static void bump(P a, P b) {
        Integer x = a.pairs.get(b);
        a.pairs.put(b, x == null ? 1 : x + 1);
        Integer y = b.pairs.get(a);
        b.pairs.put(a, y == null ? 1 : y + 1);
    }

    static double idealGap(List<P> pool, long[] dn, int days, int slotsPerDay) {
        if (pool.isEmpty() || slotsPerDay == 0) return 1;
        long sum = 0;
        for (P p : pool) for (int d = 1; d <= days; d++) if (!onLeave(p, dn[d])) sum++;
        return Math.max(1.0, (double) sum / (days * slotsPerDay));
    }

    /** Chooses the person most due for a call. po[0] reports which relaxation pass was needed. */
    static P pick(List<P> pool, Set<P> used, int d, long dn, double gap, int rest, double tol,
                  P partner, int exRank, Random rnd, int[] po) {
        int minGap = Math.max(rest + 1, (int) Math.ceil(gap * 0.5));
        for (int pass = 0; pass < 4; pass++) {
            List<P> el = new ArrayList<>();
            for (P p : pool) {
                if (used.contains(p) || onLeave(p, dn)) continue;
                if (exRank >= 0 && p.rank == exRank) continue;
                boolean restViol = (d - p.last) <= rest || p.off.contains(dn);
                boolean early = (d - p.last) < minGap;
                boolean blocked = p.block && d <= 7;
                if (pass == 0 && (restViol || early || blocked)) continue;
                if (pass == 1 && (restViol || early)) continue;
                if (pass == 2 && restViol) continue;
                el.add(p);
            }
            if (el.isEmpty()) continue;
            double bd = Double.MAX_VALUE;
            for (P p : el) bd = Math.min(bd, p.last + gap);
            P best = null;
            double[] bk = null;
            for (P p : el) {
                double due = p.last + gap;
                if (due > bd + tol) continue;
                int pc = 0;
                if (partner != null) {
                    Integer v = partner.pairs.get(p);
                    pc = v == null ? 0 : v;
                }
                double[] k = {pc, due, p.total, rnd.nextDouble()};
                if (bk == null || less(k, bk)) {
                    best = p;
                    bk = k;
                }
            }
            po[0] = pass;
            return best;
        }
        return null;
    }

    static void addNotes(P p, int pass, int d, long dn, int rest, Set<String> blockNotes, Set<String> restNotes) {
        if (pass >= 1 && p.block && d <= 7) blockNotes.add(p.name);
        if (pass == 3 && ((d - p.last) <= rest || p.off.contains(dn))) restNotes.add(p.name);
    }

    public static Roster generate(int year, int month, boolean[] act, String internsText, String staffText,
                                  List<Lv> entered, int rest, Roster prev, List<Lv> olderLeaves, int seed) {
        if (month < 1 || month > 12) throw new IllegalArgumentException("Month must be between 01 and 12.");
        if (!act[0] && !act[1] && !act[2]) throw new IllegalArgumentException("Tick at least one call column.");
        if (rest < 0) rest = 0;

        List<Lv> leaves = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        if (entered != null) for (Lv l : entered) if (seen.add(l.id())) leaves.add(l);
        if (olderLeaves != null) for (Lv l : olderLeaves) if (seen.add(l.id())) leaves.add(l);

        List<P> interns = parsePeople(internsText, false);
        List<P> staff = parsePeople(staffText, true);
        List<P> all = new ArrayList<>(interns);
        all.addAll(staff);
        for (P p : all) for (Lv l : leaves) if (l.name.equalsIgnoreCase(p.name)) p.lv.add(l);

        int days = dim(year, month);
        long[] dn = new long[days + 2];
        for (int d = 1; d <= days; d++) dn[d] = dayNum(year, month, d);

        StringBuilder notes = new StringBuilder();
        int py = month == 1 ? year - 1 : year;
        int pm = month == 1 ? 12 : month - 1;
        if (prev != null && prev.year == py && prev.month == pm) {
            Map<String, P> byKey = new HashMap<>();
            for (P p : all) byKey.put(p.name.toLowerCase(Locale.ROOT), p);
            for (int d = 1; d <= prev.days; d++) {
                for (int c = 0; c < 3; c++) {
                    String nm = prev.rows[d][c];
                    if (nm.isEmpty() || nm.equals("UNCOVERED")) continue;
                    P p = byKey.get(nm.toLowerCase(Locale.ROOT));
                    if (p == null) continue;
                    p.last = d - prev.days;
                    if (d > prev.days - 7) p.block = true;
                    addOff(p, dayNum(py, pm, d));
                }
            }
        } else {
            notes.append("No approved roster for ").append(monthName(pm)).append(" ").append(py)
                 .append(" in History, so nothing was carried over.\n");
        }

        int staffSlots = (act[1] && act[2]) ? 2 : ((act[1] || act[2]) ? 1 : 0);
        double gapI = idealGap(interns, dn, days, act[0] ? 1 : 0);
        double gapS = idealGap(staff, dn, days, staffSlots);

        Random rnd = new Random(seed);
        Set<String> blockNotes = new TreeSet<>();
        Set<String> restNotes = new TreeSet<>();
        String[][] rows = new String[days + 1][3];
        for (int d = 0; d <= days; d++) Arrays.fill(rows[d], "");
        int uncovered = 0;
        int soloNights = 0;

        for (int d = 1; d <= days; d++) {
            Set<P> used = new HashSet<>();
            if (act[0]) {
                int[] po = new int[1];
                P p = pick(interns, used, d, dn[d], gapI, rest, 0, null, -1, rnd, po);
                if (p == null) {
                    rows[d][0] = "UNCOVERED";
                    uncovered++;
                } else {
                    rows[d][0] = p.name;
                    addNotes(p, po[0], d, dn[d], rest, blockNotes, restNotes);
                    mark(p, d, dn[d], used);
                }
            }
            if (act[1] && act[2]) {
                Set<P> skip = new HashSet<>(used);
                P a = null, b = null, solo = null;
                int[] poA = new int[1], poB = new int[1], poSolo = new int[1];
                while (true) {
                    int[] pc = new int[1];
                    P cand = pick(staff, skip, d, dn[d], gapS, rest, 0, null, -1, rnd, pc);
                    if (cand == null) break;
                    if (solo == null) {
                        solo = cand;
                        poSolo[0] = pc[0];
                    }
                    Set<P> withCand = new HashSet<>(used);
                    withCand.add(cand);
                    int[] pp = new int[1];
                    P partner = pick(staff, withCand, d, dn[d], gapS, rest, PAIR_TOL, cand, cand.rank, rnd, pp);
                    if (partner != null) {
                        a = cand;
                        b = partner;
                        poA[0] = pc[0];
                        poB[0] = pp[0];
                        break;
                    }
                    skip.add(cand);
                }
                if (a != null && b != null) {
                    addNotes(a, poA[0], d, dn[d], rest, blockNotes, restNotes);
                    addNotes(b, poB[0], d, dn[d], rest, blockNotes, restNotes);
                    mark(a, d, dn[d], used);
                    mark(b, d, dn[d], used);
                    bump(a, b);
                    P senior = b.rank > a.rank ? b : a;
                    P junior = senior == a ? b : a;
                    rows[d][1] = junior.name;
                    rows[d][2] = senior.name;
                } else if (solo != null) {
                    addNotes(solo, poSolo[0], d, dn[d], rest, blockNotes, restNotes);
                    mark(solo, d, dn[d], used);
                    uncovered++;
                    soloNights++;
                    if (solo.rank >= SENIOR_MIN) {
                        rows[d][2] = solo.name;
                        rows[d][1] = "UNCOVERED";
                    } else {
                        rows[d][1] = solo.name;
                        rows[d][2] = "UNCOVERED";
                    }
                } else {
                    uncovered += 2;
                    rows[d][1] = "UNCOVERED";
                    rows[d][2] = "UNCOVERED";
                }
            } else if (act[1] || act[2]) {
                int col = act[1] ? 1 : 2;
                int[] po = new int[1];
                P p = pick(staff, used, d, dn[d], gapS, rest, 0, null, -1, rnd, po);
                if (p == null) {
                    rows[d][col] = "UNCOVERED";
                    uncovered++;
                } else {
                    rows[d][col] = p.name;
                    addNotes(p, po[0], d, dn[d], rest, blockNotes, restNotes);
                    mark(p, d, dn[d], used);
                }
            }
        }

        if (!blockNotes.isEmpty()) {
            notes.append("Note: too few people to fully skip last month's final week. Used in week 1: ")
                 .append(join(blockNotes)).append("\n");
        }
        if (!restNotes.isEmpty()) {
            notes.append("Note: off-duty rule could not be fully kept for: ").append(join(restNotes))
                 .append(" (too few people available).\n");
        }
        if (soloNights > 0) {
            notes.append("Note: on ").append(soloNights)
                 .append(" night(s) only one staff could be placed. Two staff of the same rank cannot share a night, ")
                 .append("so add people of other ranks or check leave dates.\n");
        }
        if (uncovered > 0) {
            notes.append("Note: ").append(uncovered)
                 .append(" slot(s) are UNCOVERED. Check exceptions or add people.\n");
        }

        Roster r = new Roster();
        r.year = year;
        r.month = month;
        r.days = days;
        r.act = act.clone();
        r.rows = rows;
        r.notes = notes.toString();
        for (Lv l : leaves) if (l.e >= dn[1]) r.leaves.add(l);
        return r;
    }

    static String join(Set<String> s) {
        StringBuilder sb = new StringBuilder();
        for (String x : s) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(x);
        }
        return sb.toString();
    }

    // ---------------------------------------------------------- storage
    public static String serialize(Roster r) {
        StringBuilder sb = new StringBuilder();
        sb.append("H|").append(r.year).append("|").append(r.month).append("|")
          .append(r.act[0] ? 1 : 0).append("|").append(r.act[1] ? 1 : 0).append("|")
          .append(r.act[2] ? 1 : 0).append("|").append(clean(r.stamp));
        for (Lv l : r.leaves) {
            sb.append("\nL|").append(clean(l.name)).append("|").append(iso(l.s)).append("|")
              .append(iso(l.e)).append("|").append(clean(l.type == null ? "Leave" : l.type));
        }
        for (int d = 1; d <= r.days; d++) {
            sb.append("\nD|").append(d).append("|").append(r.rows[d][0]).append("|")
              .append(r.rows[d][1]).append("|").append(r.rows[d][2]);
        }
        return sb.toString();
    }

    public static Roster parse(String s) {
        Roster r = new Roster();
        List<String[]> drows = new ArrayList<>();
        int maxDay = 0;
        for (String line : s.split("\n")) {
            String[] f = line.split("\\|", -1);
            if (f[0].equals("H") && f.length >= 6) {
                r.year = Integer.parseInt(f[1]);
                r.month = Integer.parseInt(f[2]);
                for (int i = 0; i < 3; i++) r.act[i] = f[3 + i].equals("1");
                r.stamp = f.length > 6 ? f[6] : "";
            } else if (f[0].equals("L") && f.length >= 4) {
                r.leaves.add(new Lv(f[1], parseDate(f[2]), parseDate(f[3]), f.length > 4 ? f[4] : "Leave"));
            } else if (f[0].equals("D") && f.length >= 2) {
                drows.add(f);
                maxDay = Math.max(maxDay, Integer.parseInt(f[1]));
            }
        }
        r.days = maxDay;
        r.rows = new String[maxDay + 1][3];
        for (int d = 0; d <= maxDay; d++) Arrays.fill(r.rows[d], "");
        for (String[] f : drows) {
            int d = Integer.parseInt(f[1]);
            for (int c = 0; c < 3; c++) r.rows[d][c] = f.length > c + 2 ? f[c + 2] : "";
        }
        return r;
    }

    // ---------------------------------------------------------- display
    public static String dow(Roster r, int d) {
        return DOW[wd(dayNum(r.year, r.month, d))];
    }

    public static String render(Roster r, String status) {
        int w = 12;
        for (int d = 1; d <= r.days; d++)
            for (int c = 0; c < 3; c++)
                if (r.act[c]) w = Math.max(w, r.rows[d][c].length());
        w += 2;
        String fmt = "%-" + w + "s";
        StringBuilder sb = new StringBuilder();
        sb.append("CALL DUTY ROSTER - ").append(monthName(r.month)).append(" ").append(r.year).append("\n\n");
        sb.append(String.format(Locale.US, "%-8s", "Date"));
        for (int c = 0; c < 3; c++) if (r.act[c]) sb.append(String.format(Locale.US, fmt, TITLES[c]));
        sb.append("\n");
        Map<String, List<Integer>> duty = new LinkedHashMap<>();
        for (int d = 1; d <= r.days; d++) {
            sb.append(String.format(Locale.US, "%-8s", d + " " + dow(r, d)));
            for (int c = 0; c < 3; c++) {
                if (!r.act[c]) continue;
                String nm = r.rows[d][c];
                sb.append(String.format(Locale.US, fmt, nm));
                if (!nm.isEmpty() && !nm.equals("UNCOVERED")) {
                    List<Integer> l = duty.get(nm);
                    if (l == null) {
                        l = new ArrayList<>();
                        duty.put(nm, l);
                    }
                    l.add(d);
                }
            }
            sb.append("\n");
        }
        sb.append("\nTotals (duties, days between duties)\n");
        for (Map.Entry<String, List<Integer>> e : duty.entrySet()) {
            List<Integer> l = e.getValue();
            sb.append(String.format(Locale.US, "%-" + w + "s%d", e.getKey(), l.size()));
            if (l.size() >= 2) {
                int mn = 999, mx = 0;
                for (int i = 1; i < l.size(); i++) {
                    int g = l.get(i) - l.get(i - 1);
                    mn = Math.min(mn, g);
                    mx = Math.max(mx, g);
                }
                sb.append("   gaps ").append(mn).append("-").append(mx);
            }
            sb.append("\n");
        }
        long first = dayNum(r.year, r.month, 1);
        long lastDay = dayNum(r.year, r.month, r.days);
        StringBuilder lv = new StringBuilder();
        for (Lv l : r.leaves) {
            if (l.e >= first && l.s <= lastDay) {
                lv.append(String.format(Locale.US, "%-" + w + "s%s: %s to %s\n", l.name,
                        l.type == null ? "Leave" : l.type, iso(l.s), iso(l.e)));
            }
        }
        if (lv.length() > 0) sb.append("\nLeave / Day Off / Excuse\n").append(lv);
        if (!r.notes.isEmpty()) sb.append("\n").append(r.notes);
        if (status != null && !status.isEmpty()) sb.append("\n").append(status).append("\n");
        sb.append("\n").append(FOOTER).append("\n");
        return sb.toString();
    }
}
