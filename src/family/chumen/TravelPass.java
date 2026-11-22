package family.chumen;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;

/** Keep both the sign and its illustration visible as the user increases the font size. */
final class TravelPass extends MotionUi.PressButton {
    private final Drawable vehicle;
    private boolean stacked;
    TravelPass(Context context, int illustration) {
        super(context, true);
        vehicle = context.getDrawable(illustration).mutate();
    }
    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        float density = getResources().getDisplayMetrics().density;
        // Use the longer action label for both passes so they always share a layout.
        float room = MeasureSpec.getSize(widthSpec) - getPaint().measureText("查公交地铁");
        stacked = room < 104 * density;
        int top = Math.round((stacked ? 62 : 18) * density);
        if (getPaddingTop() != top) {
            setPadding(getPaddingLeft(), top, getPaddingRight(), getPaddingBottom());
        }
        super.onMeasure(widthSpec, heightSpec);
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float density = getResources().getDisplayMetrics().density;
        float endOfText = getPaddingLeft() + getPaint().measureText(getText().toString()) + 8 * density;
        float available = getWidth() - endOfText - 6 * density;
        int width = stacked ? (int)(75 * density)
                : (int)Math.min(158 * density, Math.min(available, getHeight() * 1.28f));
        if (width < 30 * density) return;
        int right = getWidth() - Math.round(6 * density);
        int height = width * 3 / 4;
        int top = stacked ? (int)(5 * density) : (getHeight() - height) / 2;
        vehicle.setBounds(right - width, top, right, top + height);
        vehicle.draw(canvas);
    }
}
