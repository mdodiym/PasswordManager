package com.dodiy.passwordmanager;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;

/** Round logo tile: brand colour background with the logo in white or black, whichever reads better. */
final class LogoDrawable extends Drawable {
    private final Provider provider;
    private final String fallback;
    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint img = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

    private static final int[] PALETTE = {
            0xFF3949AB, 0xFF00897B, 0xFF6D4C41, 0xFF8E24AA, 0xFFD81B60, 0xFF5E35B1,
            0xFF1E88E5, 0xFF43A047, 0xFFF4511E, 0xFF546E7A, 0xFFC0CA33, 0xFF00ACC1
    };

    /** @param fallbackTitle used for the letter icon when there is no provider */
    LogoDrawable(Provider p, String fallbackTitle) {
        if (p != null && p.kind == Provider.IMAGE) Providers.loadBitmap(p);
        provider = p;
        fallback = fallbackTitle == null ? "" : fallbackTitle.trim();
        int color;
        if (p == null) color = PALETTE[(fallback.toLowerCase().hashCode() & 0x7fffffff) % PALETTE.length];
        else if (p.kind == Provider.MICROSOFT || p.kind == Provider.IMAGE) color = 0xFFFFFFFF;
        else color = p.color;
        bg.setColor(color);
        fg.setColor(onColor(color));
        fg.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        fg.setTextAlign(Paint.Align.CENTER);
        ring.setStyle(Paint.Style.STROKE);
        ring.setColor(0x33808080);
    }

    /** White on most brand colours (as brands show their own logos); black only on light ones like yellow. */
    static int onColor(int color) {
        double l = 0.2126 * lin(Color.red(color)) + 0.7152 * lin(Color.green(color)) + 0.0722 * lin(Color.blue(color));
        double withWhite = 1.05 / (l + 0.05);   // WCAG contrast ratio of white text on this colour
        return withWhite >= 2.2 ? Color.WHITE : 0xFF111111;
    }

    private static double lin(int c) {
        double s = c / 255.0;
        return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
    }

    @Override public void draw(Canvas canvas) {
        Rect b = getBounds();
        float size = Math.min(b.width(), b.height());
        float cx = b.exactCenterX(), cy = b.exactCenterY(), r = size / 2f;
        canvas.drawCircle(cx, cy, r, bg);
        ring.setStrokeWidth(Math.max(1f, size / 48f));
        canvas.drawCircle(cx, cy, r - ring.getStrokeWidth() / 2f, ring);

        if (provider != null && provider.kind == Provider.GLYPH) {
            float s = size * 0.56f / 24f;
            canvas.save();
            canvas.translate(cx - 12 * s, cy - 12 * s);
            canvas.scale(s, s);
            canvas.drawPath(provider.path(), fg);
            canvas.restore();
        } else if (provider != null && provider.kind == Provider.IMAGE && provider.bitmap != null) {
            android.graphics.Bitmap bm = provider.bitmap;
            android.graphics.BitmapShader sh = new android.graphics.BitmapShader(bm, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP);
            android.graphics.Matrix m = new android.graphics.Matrix();
            float sc = size / Math.max(bm.getWidth(), bm.getHeight());
            m.setScale(sc, sc);
            m.postTranslate(cx - bm.getWidth() * sc / 2f, cy - bm.getHeight() * sc / 2f);
            sh.setLocalMatrix(m);
            img.setShader(sh);
            canvas.drawCircle(cx, cy, r, img);
            canvas.drawCircle(cx, cy, r - ring.getStrokeWidth() / 2f, ring);
        } else if (provider != null && provider.kind == Provider.MICROSOFT) {
            float q = size * 0.24f, gap = size * 0.025f;
            int[] cols = {0xFFF25022, 0xFF7FBA00, 0xFF00A4EF, 0xFFFFB900};
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            for (int i = 0; i < 4; i++) {
                float x = cx + (i % 2 == 0 ? -q - gap : gap), y = cy + (i < 2 ? -q - gap : gap);
                p.setColor(cols[i]);
                canvas.drawRect(new RectF(x, y, x + q, y + q), p);
            }
        } else {
            String t = provider != null ? provider.data : initials(fallback);
            float ts = t.length() <= 1 ? 0.5f : t.length() == 2 ? 0.40f : 0.30f;
            fg.setTextSize(size * ts);
            Paint.FontMetrics fm = fg.getFontMetrics();
            canvas.drawText(t, cx, cy - (fm.ascent + fm.descent) / 2f, fg);
        }
    }

    static String initials(String title) {
        String t = title == null ? "" : title.trim();
        if (t.isEmpty()) return "?";
        int cp = t.codePointAt(0);
        return new String(Character.toChars(Character.toUpperCase(cp)));
    }

    @Override public void setAlpha(int alpha) { bg.setAlpha(alpha); fg.setAlpha(alpha); invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter cf) { bg.setColorFilter(cf); fg.setColorFilter(cf); invalidateSelf(); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
