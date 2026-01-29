package com.afonso.fiveminutediary.data;

import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Simplified text formatting serializer
 * Supports: bold, italic, underline, text color, highlight
 */
public class TextFormattingSerializer {

    private static final String TYPE_BOLD = "b";
    private static final String TYPE_ITALIC = "i";
    private static final String TYPE_UNDERLINE = "u";
    private static final String TYPE_COLOR = "c";
    private static final String TYPE_HIGHLIGHT = "h";

    /**
     * Serialize SpannableString to JSON string
     */
    public static String serializeFormatting(CharSequence text) {
        if (!(text instanceof Spannable) || text.length() == 0) {
            return null;
        }

        try {
            Spannable spannable = (Spannable) text;
            JSONArray spans = new JSONArray();

            // Bold & Italic
            StyleSpan[] styleSpans = spannable.getSpans(0, text.length(), StyleSpan.class);
            for (StyleSpan span : styleSpans) {
                int start = spannable.getSpanStart(span);
                int end = spannable.getSpanEnd(span);

                if (start >= 0 && end <= text.length() && start < end) {
                    JSONObject obj = new JSONObject();
                    obj.put("s", start);
                    obj.put("e", end);

                    if (span.getStyle() == Typeface.BOLD) {
                        obj.put("t", TYPE_BOLD);
                    } else if (span.getStyle() == Typeface.ITALIC) {
                        obj.put("t", TYPE_ITALIC);
                    }

                    spans.put(obj);
                }
            }

            // Underline
            UnderlineSpan[] underlineSpans = spannable.getSpans(0, text.length(), UnderlineSpan.class);
            for (UnderlineSpan span : underlineSpans) {
                int start = spannable.getSpanStart(span);
                int end = spannable.getSpanEnd(span);

                if (start >= 0 && end <= text.length() && start < end) {
                    JSONObject obj = new JSONObject();
                    obj.put("s", start);
                    obj.put("e", end);
                    obj.put("t", TYPE_UNDERLINE);
                    spans.put(obj);
                }
            }

            // Text color
            ForegroundColorSpan[] fgSpans = spannable.getSpans(0, text.length(), ForegroundColorSpan.class);
            for (ForegroundColorSpan span : fgSpans) {
                int start = spannable.getSpanStart(span);
                int end = spannable.getSpanEnd(span);

                if (start >= 0 && end <= text.length() && start < end) {
                    JSONObject obj = new JSONObject();
                    obj.put("s", start);
                    obj.put("e", end);
                    obj.put("t", TYPE_COLOR);
                    obj.put("v", span.getForegroundColor());
                    spans.put(obj);
                }
            }

            // Highlight
            BackgroundColorSpan[] bgSpans = spannable.getSpans(0, text.length(), BackgroundColorSpan.class);
            for (BackgroundColorSpan span : bgSpans) {
                int start = spannable.getSpanStart(span);
                int end = spannable.getSpanEnd(span);

                if (start >= 0 && end <= text.length() && start < end) {
                    JSONObject obj = new JSONObject();
                    obj.put("s", start);
                    obj.put("e", end);
                    obj.put("t", TYPE_HIGHLIGHT);
                    obj.put("v", span.getBackgroundColor());
                    spans.put(obj);
                }
            }

            return spans.length() > 0 ? spans.toString() : null;

        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Deserialize JSON to SpannableString
     */
    public static SpannableString deserializeFormatting(String text, String formattingJson) {
        SpannableString spannable = new SpannableString(text);

        if (formattingJson == null || formattingJson.isEmpty() || text.isEmpty()) {
            return spannable;
        }

        try {
            JSONArray spans = new JSONArray(formattingJson);

            for (int i = 0; i < spans.length(); i++) {
                JSONObject obj = spans.getJSONObject(i);

                int start = obj.getInt("s");
                int end = obj.getInt("e");
                String type = obj.getString("t");

                // Validate bounds
                if (start < 0 || end > text.length() || start >= end) {
                    continue;
                }

                switch (type) {
                    case TYPE_BOLD:
                        spannable.setSpan(
                                new StyleSpan(Typeface.BOLD),
                                start, end,
                                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        );
                        break;

                    case TYPE_ITALIC:
                        spannable.setSpan(
                                new StyleSpan(Typeface.ITALIC),
                                start, end,
                                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        );
                        break;

                    case TYPE_UNDERLINE:
                        spannable.setSpan(
                                new UnderlineSpan(),
                                start, end,
                                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        );
                        break;

                    case TYPE_COLOR:
                        if (obj.has("v")) {
                            spannable.setSpan(
                                    new ForegroundColorSpan(obj.getInt("v")),
                                    start, end,
                                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                            );
                        }
                        break;

                    case TYPE_HIGHLIGHT:
                        if (obj.has("v")) {
                            spannable.setSpan(
                                    new BackgroundColorSpan(obj.getInt("v")),
                                    start, end,
                                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                            );
                        }
                        break;
                }
            }

        } catch (JSONException e) {
            e.printStackTrace();
        }

        return spannable;
    }

    /**
     * Check if text has any formatting
     */
    public static boolean hasFormatting(CharSequence text) {
        if (!(text instanceof Spannable)) {
            return false;
        }

        Spannable spannable = (Spannable) text;

        return spannable.getSpans(0, text.length(), StyleSpan.class).length > 0 ||
                spannable.getSpans(0, text.length(), UnderlineSpan.class).length > 0 ||
                spannable.getSpans(0, text.length(), ForegroundColorSpan.class).length > 0 ||
                spannable.getSpans(0, text.length(), BackgroundColorSpan.class).length > 0;
    }
}