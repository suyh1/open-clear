package family.chumen;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.widget.Button;

/** Reserve the entire label first, then fit the vehicle illustration into the remaining space. */
final class TravelPass extends Button {
    private final Drawable vehicle;
    TravelPass(Context context, int illustration) {
        super(context);
        vehicle = context.getDrawable(illustration).mutate();
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float density = getResources().getDisplayMetrics().density;
        float endOfText = getPaddingLeft() + getPaint().measureText(getText().toString()) + 8 * density;
        float available = getWidth() - endOfText - 6 * density;
        int width = (int)Math.min(158 * density, Math.min(available, getHeight() * 1.28f));
        if (width < 30 * density) return;
        int right = getWidth() - Math.round(6 * density);
        int height = width * 3 / 4;
        int top = (getHeight() - height) / 2;
        vehicle.setBounds(right - width, top, right, top + height);
        vehicle.draw(canvas);
    }
}
