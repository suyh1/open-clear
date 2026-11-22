package family.chumen;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.location.Location;
import android.view.*;
import android.widget.*;
import java.util.List;

/** Compare the actual straight-line distances of the candidate destinations. */
final class DistanceProfile extends LinearLayout {
    private final List<Places.Place> places;
    private final int[] meters;
    private final TextView name;
    private final TextSwitcher meter;
    private final Plot plot;
    private int selected = -1;
    private int max = 1;

    DistanceProfile(Context c, List<Places.Place> places, double lat, double lon) {
        super(c);
        this.places = places;
        meters = new int[places.size()];
        float[] output = new float[1];
        for (int i=0;i<places.size();i++) {
            Places.Place p = places.get(i);
            Location.distanceBetween(lat,lon,p.lat,p.lon,output);
            meters[i] = Math.round(output[0]);
            max = Math.max(max,meters[i]);
        }
        setOrientation(VERTICAL);
        name = new TextView(c);
        name.setTextSize(20); name.setTextColor(0xff244c3d);
        addView(name);
        meter = new TextSwitcher(c);
        meter.setFactory(() -> {
            TextView n = new TextView(c);
            n.setTextSize(26); n.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            n.setTextColor(0xff194f41); return n;
        });
        addView(meter);
        plot = new Plot(c);
        addView(plot,-1,MotionUi.dp(this,100));
        select(0);
    }
    int index() { return selected; }
    private void select(int index) {
        if (index==selected) return;
        boolean forward = index > selected;
        if (selected>=0 && MotionUi.enabled(this)) {
            meter.setInAnimation(rolling(forward ? 1 : -1,0));
            meter.setOutAnimation(rolling(0,forward ? -1 : 1));
        }
        selected = index;
        name.setText(places.get(index).name);
        meter.setText(meters[index] + " 米");
        plot.setContentDescription("第" + (index+1) + "个地点，" + places.get(index).name
                + "，距起点约" + meters[index] + "米。左右滑动可比较。");
        plot.pulse();
    }
    private android.view.animation.Animation rolling(int from,int to) {
        android.view.animation.TranslateAnimation a = new android.view.animation.TranslateAnimation(
                0,0,from*MotionUi.dp(this,28),to*MotionUi.dp(this,28));
        a.setDuration(190); a.setInterpolator(MotionUi.FLUID); return a;
    }
    private final class Plot extends View {
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Path path = new Path();
        float glow;
        ValueAnimator pulse;
        Plot(Context c) { super(c); setFocusable(true); setClickable(true); }
        float x(int i) { return MotionUi.dp(this,13) + (getWidth()-2f*MotionUi.dp(this,13))*i/(meters.length-1); }
        float y(int i) { return getHeight()-MotionUi.dp(this,14)-
                (getHeight()-MotionUi.dp(this,30))*(meters[i]/(float)max); }
        void pulse() {
            if (pulse != null) pulse.cancel();
            if (!MotionUi.enabled(this)) { glow=0; invalidate(); return; }
            pulse=ValueAnimator.ofFloat(0,1,0); pulse.setDuration(520);
            pulse.addUpdateListener(a -> { glow=(float)a.getAnimatedValue(); invalidate(); });
            pulse.start();
        }
        @Override protected void onDraw(Canvas c) {
            paint.setColor(0xff4c8f74); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(MotionUi.dp(this,2));
            path.reset();
            for(int i=0;i<meters.length;i++) { if(i==0)path.moveTo(x(i),y(i)); else path.lineTo(x(i),y(i)); }
            c.drawPath(path,paint); paint.setStyle(Paint.Style.FILL);
            for(int i=0;i<meters.length;i++) c.drawCircle(x(i),y(i),MotionUi.dp(this,4),paint);
            paint.setColor(0x4086c4a6);
            c.drawCircle(x(selected),y(selected),MotionUi.dp(this,9+4*glow),paint);
            paint.setColor(0xff194f41); c.drawCircle(x(selected),y(selected),MotionUi.dp(this,5),paint);
        }
        @Override public boolean onTouchEvent(MotionEvent e) {
            switch(e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_MOVE:
                    getParent().requestDisallowInterceptTouchEvent(true);
                    int i=Math.round((e.getX()-x(0))*(meters.length-1)/(x(meters.length-1)-x(0)));
                    select(Math.max(0,Math.min(i,meters.length-1))); return true;
                case MotionEvent.ACTION_UP: performClick();
                case MotionEvent.ACTION_CANCEL: getParent().requestDisallowInterceptTouchEvent(false); return true;
                default: return false;
            }
        }
        @Override public boolean performClick() { super.performClick(); return true; }
        @Override public boolean onKeyDown(int code, android.view.KeyEvent e) {
            if (code==KeyEvent.KEYCODE_DPAD_LEFT || code==KeyEvent.KEYCODE_DPAD_RIGHT) {
                select(Math.max(0,Math.min(meters.length-1, selected+(code==KeyEvent.KEYCODE_DPAD_LEFT?-1:1))));
                return true;
            }
            return super.onKeyDown(code,e);
        }
        @Override protected void onDetachedFromWindow() {
            if(pulse!=null) pulse.cancel();
            super.onDetachedFromWindow();
        }
    }
}
