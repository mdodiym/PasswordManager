package com.dodiy.passwordmanager;

import android.graphics.Path;

/** Parses the absolute M/L/C/Z path data produced by tools/convert_icons.py (24x24 box). */
final class PathData {
    private PathData() {}

    static Path parse(String d) {
        Path p = new Path();
        float[] v = new float[6];
        int i = 0, n = d.length();
        while (i < n) {
            char cmd = d.charAt(i++);
            int need = cmd == 'C' ? 6 : (cmd == 'M' || cmd == 'L') ? 2 : 0;
            for (int k = 0; k < need; k++) {
                while (i < n && d.charAt(i) == ' ') i++;
                int s = i;
                while (i < n) {
                    char ch = d.charAt(i);
                    if ((ch >= '0' && ch <= '9') || ch == '.' || (ch == '-' && i == s)) i++; else break;
                }
                v[k] = Float.parseFloat(d.substring(s, i));
            }
            switch (cmd) {
                case 'M': p.moveTo(v[0], v[1]); break;
                case 'L': p.lineTo(v[0], v[1]); break;
                case 'C': p.cubicTo(v[0], v[1], v[2], v[3], v[4], v[5]); break;
                case 'Z': p.close(); break;
                default: break;
            }
        }
        return p;
    }
}
