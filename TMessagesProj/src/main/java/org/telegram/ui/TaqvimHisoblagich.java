package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.graphics.ColorUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;

/** Simple calculator with digit grouping. The result can be written to the debts list. */
public class TaqvimHisoblagich extends FrameLayout {

    private static final int MAX_DIGITS = 15;
    private static final int MAX_HISTORY = 30;
    private static final long MAX_AMOUNT = 999999999999L;
    private static final int MAX_DEBTS = 300;

    private final BaseFragment fragment;
    private final int textColor;
    private final int accent;
    private final int cardColor;

    private TextView histView;
    private TextView exprView;
    private TextView resView;
    private TextView unitView;

    // state
    private final ArrayList<String> tokens = new ArrayList<>(); // numbers and + - * /
    private String cur = "0";   // number being typed, "" while waiting for the next number
    private boolean fresh;      // the last thing done was "="
    private boolean error;
    private String lastHist = "";
    private final ArrayList<String[]> history = new ArrayList<>(); // {expression, result}

    public TaqvimHisoblagich(BaseFragment fragment, Context context, int textColor, int accent, int cardColor) {
        super(context);
        this.fragment = fragment;
        this.textColor = textColor;
        this.accent = accent;
        this.cardColor = cardColor;

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(16), dp(8), dp(16), dp(DialogsActivity.MAIN_TABS_HEIGHT_WITH_MARGINS + 16));
        addView(column, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        column.addView(buildDisplay(context), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(150)));
        LinearLayout.LayoutParams chipsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        chipsLp.topMargin = dp(10);
        chipsLp.bottomMargin = dp(10);
        column.addView(buildChips(context), chipsLp);
        column.addView(buildKeys(context), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        update();
    }

    private static String t(String uz, String ru, String en) {
        switch (LocaleController.getInstance().getCurrentLocale().getLanguage()) {
            case "uz": return uz;
            case "ru": return ru;
            default:   return en;
        }
    }

    /* ---------------- views ---------------- */

    private View buildDisplay(Context context) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.BOTTOM | Gravity.END);
        box.setPadding(dp(20), dp(12), dp(20), dp(14));
        box.setBackground(round(cardColor, 26));

        histView = new TextView(context);
        histView.setTextSize(13);
        histView.setSingleLine(true);
        histView.setGravity(Gravity.END);
        histView.setTextColor(ColorUtils.setAlphaComponent(textColor, 120));
        box.addView(histView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        exprView = new TextView(context);
        exprView.setTextSize(20);
        exprView.setSingleLine(true);
        exprView.setEllipsize(android.text.TextUtils.TruncateAt.START);
        exprView.setGravity(Gravity.END);
        exprView.setTextColor(ColorUtils.setAlphaComponent(textColor, 150));
        LinearLayout.LayoutParams exprLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        exprLp.topMargin = dp(4);
        box.addView(exprView, exprLp);

        LinearLayout resRow = new LinearLayout(context);
        resRow.setOrientation(LinearLayout.HORIZONTAL);
        resRow.setGravity(Gravity.BOTTOM | Gravity.END);
        resView = new TextView(context);
        resView.setTextSize(46);
        resView.setSingleLine(true);
        resView.setGravity(Gravity.END);
        resView.setTypeface(AndroidUtilities.bold());
        resView.setTextColor(textColor);
        resRow.addView(resView, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        unitView = new TextView(context);
        unitView.setText(t("so'm", "сум", "UZS"));
        unitView.setTextSize(14);
        unitView.setTextColor(ColorUtils.setAlphaComponent(textColor, 120));
        unitView.setPadding(dp(6), 0, 0, dp(8));
        resRow.addView(unitView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        box.addView(resRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return box;
    }

    private View buildChips(Context context) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(chip(context, t("Nusxa", "Копировать", "Copy"), false, v -> copyResult()));
        row.addView(chip(context, t("Tarix", "История", "History"), false, v -> showHistory()));
        row.addView(chip(context, t("+ Qarzga yozish", "+ В долги", "+ Add to debts"), true, v -> addToDebts("")));
        return row;
    }

    private View chip(Context context, String text, boolean hot, View.OnClickListener listener) {
        TextView tv = new TextView(context);
        tv.setText(text);
        tv.setTextSize(13);
        tv.setSingleLine(true);
        tv.setTypeface(AndroidUtilities.bold());
        tv.setPadding(dp(14), dp(9), dp(14), dp(9));
        tv.setTextColor(hot ? 0xFFFFFFFF : accent);
        tv.setBackground(round(hot ? accent : cardColor, 16));
        tv.setOnClickListener(listener);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(8);
        tv.setLayoutParams(lp);
        return tv;
    }

    private View buildKeys(Context context) {
        final String[][] rows = {
                {"C", "⌫", "%", "/"},
                {"7", "8", "9", "*"},
                {"4", "5", "6", "-"},
                {"1", "2", "3", "+"},
                {"±", "0", ",", "="}
        };
        LinearLayout grid = new LinearLayout(context);
        grid.setOrientation(LinearLayout.VERTICAL);
        for (int r = 0; r < rows.length; r++) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int k = 0; k < 4; k++) {
                final String key = rows[r][k];
                row.addView(keyView(context, key), keyLp(k));
            }
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
            if (r > 0) {
                rowLp.topMargin = dp(8);
            }
            grid.addView(row, rowLp);
        }
        return grid;
    }

    private LinearLayout.LayoutParams keyLp(int column) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        if (column > 0) {
            lp.leftMargin = dp(8);
        }
        return lp;
    }

    private View keyView(Context context, final String key) {
        final boolean isOp = key.equals("/") || key.equals("*") || key.equals("-") || key.equals("+");
        final boolean isFn = key.equals("C") || key.equals("⌫") || key.equals("%");
        final boolean isEq = key.equals("=");
        TextView tv = new TextView(context);
        String label = key;
        if (key.equals("/")) label = "÷";
        if (key.equals("*")) label = "×";
        if (key.equals("-")) label = "−";
        tv.setText(label);
        tv.setGravity(Gravity.CENTER);
        tv.setTextSize(isOp || isEq ? 28 : 24);
        tv.setTextColor(isEq ? 0xFFFFFFFF : (isOp || isFn ? accent : textColor));
        int bg;
        if (isEq) {
            bg = accent;
        } else if (isOp) {
            bg = ColorUtils.blendARGB(cardColor, accent, 0.22f);
        } else if (isFn) {
            bg = ColorUtils.blendARGB(cardColor, accent, 0.12f);
        } else {
            bg = cardColor;
        }
        tv.setBackground(round(bg, 22));
        tv.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                v.animate().scaleX(0.94f).scaleY(0.94f).setDuration(70).start();
            } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                v.animate().scaleX(1f).scaleY(1f).setDuration(110).start();
            }
            return false;
        });
        tv.setOnClickListener(v -> press(key));
        return tv;
    }

    private static GradientDrawable round(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    /* ---------------- keys ---------------- */

    private void clearAll() {
        tokens.clear();
        cur = "0";
        fresh = false;
        error = false;
        lastHist = "";
    }

    private static int digitCount(String s) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            if (Character.isDigit(s.charAt(i))) {
                n++;
            }
        }
        return n;
    }

    private void press(String key) {
        switch (key) {
            case "C":
                clearAll();
                break;
            case "⌫":
                backspace();
                break;
            case "%":
                percent();
                break;
            case "±":
                if (!error && !cur.isEmpty() && !cur.equals("0")) {
                    cur = cur.startsWith("-") ? cur.substring(1) : "-" + cur;
                }
                break;
            case "=":
                equalsKey();
                break;
            case "+":
            case "-":
            case "*":
            case "/":
                operator(key);
                break;
            case ",":
                if (error) {
                    clearAll();
                }
                if (fresh) {
                    tokens.clear();
                    cur = "0";
                    fresh = false;
                }
                if (cur.isEmpty()) {
                    cur = "0";
                }
                if (!cur.contains(".")) {
                    cur += ".";
                }
                break;
            default:
                digit(key);
                break;
        }
        update();
    }

    private void digit(String d) {
        if (error) {
            clearAll();
        }
        if (fresh) {
            tokens.clear();
            cur = "0";
            fresh = false;
        }
        if (cur.isEmpty() || cur.equals("0")) {
            cur = d;
        } else if (cur.equals("-0")) {
            cur = "-" + d;
        } else if (digitCount(cur) < MAX_DIGITS) {
            cur += d;
        }
    }

    private void operator(String op) {
        if (error) {
            return;
        }
        fresh = false;
        if (cur.isEmpty()) {
            if (!tokens.isEmpty()) {
                tokens.set(tokens.size() - 1, op);
            }
        } else {
            if (cur.endsWith(".")) {
                cur = cur.substring(0, cur.length() - 1);
            }
            tokens.add(cur);
            tokens.add(op);
            cur = "";
        }
    }

    private void backspace() {
        if (error) {
            clearAll();
            return;
        }
        fresh = false;
        if (!cur.isEmpty()) {
            cur = cur.substring(0, cur.length() - 1);
            if (cur.isEmpty() || cur.equals("-")) {
                cur = tokens.isEmpty() ? "0" : "";
            }
        } else if (tokens.size() >= 2) {
            tokens.remove(tokens.size() - 1);          // the operator
            cur = tokens.remove(tokens.size() - 1);    // its left number becomes editable again
        }
    }

    private void percent() {
        if (error || cur.isEmpty()) {
            return;
        }
        try {
            BigDecimal v = new BigDecimal(cur);
            final String last = tokens.size() >= 2 ? tokens.get(tokens.size() - 1) : "";
            if (last.equals("+") || last.equals("-")) {
                // 200 + 10%  ->  10% of 200
                ArrayList<String> base = new ArrayList<>(tokens.subList(0, tokens.size() - 1));
                BigDecimal b = eval(base);
                v = b.multiply(v).divide(BigDecimal.valueOf(100), 12, RoundingMode.HALF_UP);
            } else {
                v = v.divide(BigDecimal.valueOf(100), 12, RoundingMode.HALF_UP);
            }
            cur = plain(v);
            fresh = false;
        } catch (Exception e) {
            setError();
        }
    }

    private void equalsKey() {
        if (error || tokens.isEmpty()) {
            return;
        }
        ArrayList<String> all = new ArrayList<>(tokens);
        if (cur.isEmpty()) {
            all.remove(all.size() - 1);
        } else {
            all.add(cur);
        }
        if (all.size() < 3) {
            if (all.size() == 1) {
                tokens.clear();
                cur = all.get(0);
            }
            return;
        }
        final String exprText = buildExpr(all);
        try {
            String result = plain(eval(all));
            history.add(0, new String[]{exprText, result});
            while (history.size() > MAX_HISTORY) {
                history.remove(history.size() - 1);
            }
            lastHist = exprText + " =";
            tokens.clear();
            cur = result;
            fresh = true;
        } catch (Exception e) {
            setError();
        }
    }

    private void setError() {
        tokens.clear();
        cur = "0";
        error = true;
        fresh = false;
    }

    /* ---------------- maths ---------------- */

    private static String plain(BigDecimal r) {
        if (r.scale() > 9) {
            r = r.setScale(9, RoundingMode.HALF_UP);
        }
        if (r.signum() == 0) {
            return "0";
        }
        r = r.stripTrailingZeros();
        if (r.scale() < 0) {
            r = r.setScale(0);
        }
        return r.toPlainString();
    }

    private static BigDecimal eval(ArrayList<String> input) {
        ArrayList<String> t = new ArrayList<>(input);
        // multiplication and division first
        for (int i = 1; i < t.size(); ) {
            String op = t.get(i);
            if (op.equals("*") || op.equals("/")) {
                BigDecimal a = new BigDecimal(t.get(i - 1));
                BigDecimal b = new BigDecimal(t.get(i + 1));
                BigDecimal r;
                if (op.equals("*")) {
                    r = a.multiply(b);
                } else {
                    if (b.signum() == 0) {
                        throw new ArithmeticException("div0");
                    }
                    r = a.divide(b, 12, RoundingMode.HALF_UP);
                }
                t.set(i - 1, r.toPlainString());
                t.remove(i);
                t.remove(i);
            } else {
                i += 2;
            }
        }
        BigDecimal result = new BigDecimal(t.get(0));
        for (int i = 1; i + 1 < t.size(); i += 2) {
            BigDecimal b = new BigDecimal(t.get(i + 1));
            result = t.get(i).equals("+") ? result.add(b) : result.subtract(b);
        }
        if (result.abs().compareTo(new BigDecimal("1000000000000000")) > 0) {
            throw new ArithmeticException("big");
        }
        return result;
    }

    /* ---------------- text ---------------- */

    /** "1250000.5" -> "1 250 000,5" */
    private static String fmt(String plain) {
        if (plain == null || plain.isEmpty()) {
            return "";
        }
        boolean negative = plain.startsWith("-");
        String s = negative ? plain.substring(1) : plain;
        String intPart = s;
        String frac = null;
        int dot = s.indexOf('.');
        if (dot >= 0) {
            intPart = s.substring(0, dot);
            frac = s.substring(dot + 1);
        }
        StringBuilder sb = new StringBuilder();
        int len = intPart.length();
        for (int i = 0; i < len; i++) {
            if (i > 0 && (len - i) % 3 == 0) {
                sb.append(' ');
            }
            sb.append(intPart.charAt(i));
        }
        if (frac != null) {
            sb.append(',').append(frac);
        }
        return (negative ? "−" : "") + sb;
    }

    private static String opSymbol(String op) {
        switch (op) {
            case "*": return "×";
            case "/": return "÷";
            case "-": return "−";
            default:  return "+";
        }
    }

    private static String buildExpr(ArrayList<String> list) {
        StringBuilder sb = new StringBuilder();
        for (String token : list) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            boolean isOp = token.length() == 1 && "+-*/".contains(token);
            sb.append(isOp ? opSymbol(token) : fmt(token));
        }
        return sb.toString();
    }

    private void update() {
        histView.setText(lastHist);
        if (error) {
            exprView.setText("");
            resView.setText(t("Xato", "Ошибка", "Error"));
            resView.setTextSize(34);
            return;
        }
        ArrayList<String> shown = new ArrayList<>(tokens);
        if (!cur.isEmpty() && !tokens.isEmpty()) {
            shown.add(cur);
        }
        exprView.setText(tokens.isEmpty() ? "" : buildExpr(shown));

        String main = cur;
        if (main.isEmpty() && tokens.size() >= 2) {
            main = tokens.get(tokens.size() - 2);
        }
        String text = fmt(main);
        resView.setText(text);
        int length = text.length();
        resView.setTextSize(length <= 9 ? 46 : length <= 13 ? 38 : length <= 17 ? 30 : 24);
    }

    /* ---------------- chips ---------------- */

    private BigDecimal currentValue() {
        try {
            String main = cur;
            if (main.isEmpty() && tokens.size() >= 2) {
                main = tokens.get(tokens.size() - 2);
            }
            return new BigDecimal(main);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private void copyResult() {
        if (error) {
            return;
        }
        try {
            ClipboardManager cm = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("result", plain(currentValue())));
            Toast.makeText(getContext(), t("Nusxa olindi", "Скопировано", "Copied"), Toast.LENGTH_SHORT).show();
        } catch (Throwable ignored) {
        }
    }

    private void showHistory() {
        final Activity activity = fragment.getParentActivity();
        if (activity == null) {
            return;
        }
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(4), dp(20), dp(4));
        final AlertDialog[] holder = new AlertDialog[1];
        if (history.isEmpty()) {
            TextView empty = new TextView(activity);
            empty.setText(t("Hali hisoblash yo'q", "Пока пусто", "Nothing yet"));
            empty.setTextSize(15);
            empty.setTextColor(ColorUtils.setAlphaComponent(textColor, 150));
            empty.setPadding(0, dp(8), 0, dp(8));
            box.addView(empty);
        }
        for (final String[] entry : history) {
            LinearLayout item = new LinearLayout(activity);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setPadding(0, dp(8), 0, dp(8));
            TextView expr = new TextView(activity);
            expr.setText(entry[0] + " =");
            expr.setTextSize(13);
            expr.setTextColor(ColorUtils.setAlphaComponent(textColor, 140));
            item.addView(expr);
            TextView res = new TextView(activity);
            res.setText(fmt(entry[1]));
            res.setTextSize(20);
            res.setTypeface(AndroidUtilities.bold());
            res.setTextColor(textColor);
            item.addView(res);
            item.setOnClickListener(v -> {
                if (holder[0] != null) {
                    holder[0].dismiss();
                }
                tokens.clear();
                cur = entry[1];
                lastHist = entry[0] + " =";
                fresh = true;
                error = false;
                update();
            });
            box.addView(item, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(box, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(t("Tarix", "История", "History"));
        builder.setView(scroll);
        builder.setPositiveButton(t("Yopish", "Закрыть", "Close"), null);
        if (!history.isEmpty()) {
            builder.setNeutralButton(t("Tozalash", "Очистить", "Clear"), (d, w) -> history.clear());
        }
        holder[0] = builder.create();
        holder[0].show();
    }

    /** Writes the current number as a new debt (so'm) into the Qarzlar list. */
    private void addToDebts(String presetName) {
        final Activity activity = fragment.getParentActivity();
        if (activity == null) {
            return;
        }
        final BigDecimal value = currentValue().abs().setScale(0, RoundingMode.HALF_UP);
        if (error || value.signum() == 0 || value.compareTo(BigDecimal.valueOf(MAX_AMOUNT)) > 0) {
            Toast.makeText(activity, t("Avval summani hisoblang", "Сначала посчитайте сумму", "Enter an amount first"),
                    Toast.LENGTH_SHORT).show();
            return;
        }
        final long amount = value.longValue();

        final EditText nameInput = new EditText(activity);
        nameInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        nameInput.setSingleLine(true);
        nameInput.setHint(t("Kim bilan (ism)", "С кем (имя)", "Who (name)"));
        nameInput.setText(presetName);
        nameInput.setSelection(nameInput.getText().length());

        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        TextView amountView = new TextView(activity);
        amountView.setText(fmt(String.valueOf(amount)) + " " + t("so'm", "сум", "UZS"));
        amountView.setTextSize(22);
        amountView.setTypeface(AndroidUtilities.bold());
        amountView.setTextColor(textColor);
        box.addView(amountView);
        box.addView(nameInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(t("Qarzga yozish", "Записать в долги", "Add to debts"));
        builder.setView(box);
        builder.setPositiveButton(t("Menga qarzdor", "Должен мне", "Owes me"),
                (d, w) -> saveDebt(activity, nameInput.getText().toString(), amount, 1));
        builder.setNeutralButton(t("Men qarzdorman", "Я должен", "I owe"),
                (d, w) -> saveDebt(activity, nameInput.getText().toString(), amount, -1));
        builder.setNegativeButton(t("Bekor qilish", "Отмена", "Cancel"), null);
        builder.create().show();
    }

    private void saveDebt(Activity activity, String name, long amount, int dir) {
        name = name.trim();
        if (name.isEmpty()) {
            Toast.makeText(activity, t("Ismni yozing", "Введите имя", "Enter a name"), Toast.LENGTH_SHORT).show();
            addToDebts("");
            return;
        }
        if (name.length() > 60) {
            name = name.substring(0, 60);
        }
        try {
            SharedPreferences p = activity.getSharedPreferences("taqvim_debts", Context.MODE_PRIVATE);
            JSONArray array = new JSONArray(p.getString("list", "[]"));
            if (array.length() >= MAX_DEBTS) {
                Toast.makeText(activity, t("Qarzlar ro'yxati to'lgan", "Список долгов заполнен", "The debts list is full"),
                        Toast.LENGTH_SHORT).show();
                return;
            }
            JSONObject o = new JSONObject();
            o.put("id", String.valueOf(System.nanoTime()));
            o.put("who", name);
            o.put("amount", amount);
            o.put("cur", "UZS");
            o.put("dir", dir);
            o.put("due", 0);
            o.put("paid", false);
            array.put(o);
            p.edit().putString("list", array.toString()).apply();
            Toast.makeText(activity, t("Qarzlar bo'limiga yozildi", "Записано в долги", "Added to debts"),
                    Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(activity, t("Saqlab bo'lmadi", "Не удалось сохранить", "Could not save"),
                    Toast.LENGTH_SHORT).show();
        }
    }
}
