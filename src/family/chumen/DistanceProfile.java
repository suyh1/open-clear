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
    private final MeterCounter meter;
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
        meter = new MeterCounter(c);
        addView(meter);
        plot = new Plot(c);
        addView(plot,-1,MotionUi.dp(this,100));
        select(0);
    }
    int index() { return selected; }
    private void select(int index) {
        if (index==selected) return;
        boolean animate = selected >= 0 && MotionUi.enabled(this);
        selected = index;
        name.setText(places.get(index).name);
        meter.setMeters(meters[index],animate);
        plot.setContentDescription("第" + (index+1) + "个地点，" + places.get(index).name
                + "，距起点约" + meters[index] + "米。左右滑动可比较。");
        plot.pulse();
    }
    private static final class MeterCounter extends TextView {
        private float displayed;
        private ValueAnimator wheel;
        MeterCounter(Context context) {
            super(context);
            setTextSize(26);
            setTypeface(Typeface.MONOSPACE,Typeface.BOLD);
            setTextColor(0xff194f41);
        }
        void setMeters(int value,boolean animate) {
            if(wheel!=null) wheel.cancel();
            setText(value + " 米");
            if(!animate) { displayed=value; invalidate(); return; }
            wheel=ValueAnimator.ofFloat(displayed,value);
            wheel.setDuration(270);
            wheel.setInterpolator(MotionUi.FLUID);
            wheel.addUpdateListener(a -> { displayed=(float)a.getAnimatedValue(); invalidate(); });
            wheel.start();
        }
        @Override protected void onDraw(Canvas c) {
            Paint p=getPaint();
            p.setColor(getCurrentTextColor());
            float slot=p.measureText("0"), baseline=getBaseline(), step=p.getFontSpacing(), x=0;
            int highest=1;
            while(highest<=displayed/10) highest*=10;
            c.save();
            c.clipRect(0,baseline+p.ascent(),getWidth(),baseline+p.descent());
            int whole=(int)displayed;
            for(int place=highest;place>0;place/=10) {
                int digit=whole/place%10;
                float roll=Math.max(0,displayed%place-(place-1));
                c.drawText(String.valueOf(digit),x,baseline-roll*step,p);
                if(roll>0) c.drawText(String.valueOf((digit+1)%10),x,baseline+(1-roll)*step,p);
                x+=slot;
            }
            c.drawText(" 米",x,baseline,p);
            c.restore();
        }
        @Override protected void onDetachedFromWindow() {
            if(wheel!=null) wheel.cancel();
            super.onDetachedFromWindow();
        }
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
