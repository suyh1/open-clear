package family.chumen;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

/** Always-open search surface. The label moves above the text as the field gains focus. */
final class DestinationField extends LinearLayout {
    final EditText input;
    private final TextView caption;
    private final ImageButton clear;
    private final GradientDrawable outline = new GradientDrawable();
    private boolean invalid;
    private final String fieldName;

    DestinationField(Context context, String fieldName) {
        super(context);
        this.fieldName = fieldName;
        setGravity(Gravity.CENTER_VERTICAL);
        setPadding(dp(14), dp(6), dp(4), dp(6));
        setMinimumHeight(dp(74));
        outline.setColor(Color.WHITE);
        outline.setCornerRadius(dp(18));
        setBackground(outline);
        ImageView glass = new ImageView(context);
        glass.setImageResource(R.drawable.search);
        glass.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(glass, dp(24), dp(24));

        LinearLayout stack = new LinearLayout(context);
        stack.setOrientation(VERTICAL);
        stack.setGravity(Gravity.CENTER_VERTICAL);
        LayoutParams column = new LayoutParams(0, -2, 1);
        column.leftMargin = dp(10);
        addView(stack, column);
        caption = new TextView(context);
        caption.setText(fieldName);
        caption.setTextSize(16);
        caption.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        caption.setTextColor(0xff315d50);
        stack.addView(caption);
        input = new EditText(context);
        input.setId(View.generateViewId());
        input.setSingleLine();
        input.setTextSize(23);
        input.setTextColor(0xff153e34);
        input.setHintTextColor(0xff576961);
        input.setBackgroundColor(Color.TRANSPARENT);
        input.setPadding(0, 0, 0, 0);
        caption.setLabelFor(input.getId());
        stack.addView(input, new LayoutParams(-1, -2));
        clear = new ImageButton(context);
        clear.setImageResource(R.drawable.close);
        clear.setContentDescription("清空" + fieldName);
        clear.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22315d50), null, null));
        clear.setOnClickListener(v -> { input.requestFocus(); input.setText(""); });
        addView(clear, dp(48), dp(48));
        setOnClickListener(v -> {
            input.requestFocus();
            context.getSystemService(android.view.inputmethod.InputMethodManager.class)
                    .showSoftInput(input, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
        });
        setFocusable(false);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        input.setOnFocusChangeListener((v, focused) -> refresh());
        input.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) { invalid = false; refresh(); }
            public void afterTextChanged(Editable text) {}
        });
        refresh();
    }
    void showError() { invalid = true; refresh(); }
    void clearError() { if (invalid) { invalid = false; refresh(); } }
    private void refresh() {
        boolean hasText = input.length() > 0;
        boolean raised = input.hasFocus() || hasText;
        caption.setVisibility(raised ? VISIBLE : GONE);
        input.setHint(raised ? (fieldName.equals("城市名称") ? "例如：成都" : "地名或地址") : fieldName);
        clear.setVisibility(hasText ? VISIBLE : GONE);
        int color = invalid ? 0xffa42e21 : input.hasFocus() ? 0xff246651 : 0xff9eaea5;
        outline.setStroke(dp(invalid || input.hasFocus() ? 2 : 1), color);
    }
    private int dp(int size) { return Math.round(getResources().getDisplayMetrics().density * size); }
}
