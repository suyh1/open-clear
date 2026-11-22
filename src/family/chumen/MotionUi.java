package family.chumen;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.*;
import android.view.accessibility.AccessibilityManager;
import android.view.animation.Interpolator;
import android.widget.Button;

final class MotionUi {
    static final Interpolator SPRING = t -> t >= 1 ? 1 :
            (float)(1 - Math.exp(-7.5 * t) * Math.cos(10 * t));
    static final Interpolator FLUID = t -> t >= 1 ? 1 :
            (float)(1 - (1 + 8 * t) * Math.exp(-8 * t));

    static boolean enabled(View v) {
        AccessibilityManager am = v.getContext().getSystemService(AccessibilityManager.class);
        return ValueAnimator.areAnimatorsEnabled() && (am == null || !am.isTouchExplorationEnabled());
    }
    static int dp(View v, float n) {
        return Math.round(v.getResources().getDisplayMetrics().density * n);
    }
    static void enter(View v, int index) {
        if (!enabled(v)) return;
        v.setAlpha(0);
        v.setTranslationY(dp(v, 9));
        v.animate().alpha(1).translationY(0).setDuration(240)
                .setStartDelay(Math.min(index, 6) * 28L).setInterpolator(SPRING).start();
    }

    static class PressButton extends Button {
        private final boolean tilt;
        private final Paint shine = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Matrix transform = new Matrix();
        private final LinearGradient mirror = new LinearGradient(-1, 0, 1, 0,
                new int[]{0x00ffffff, 0x10ffffff, 0x55ffffff, 0x0affffff, 0x00ffffff},
                new float[]{0, .36f, .5f, .62f, 1}, Shader.TileMode.CLAMP);
        private final Paint shade = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float touchX, touchY;
        PressButton(Context c, boolean tilt) {
            super(c);
            this.tilt = tilt;
            setCameraDistance(dp(this, 1400));
            shine.setShader(mirror);
            shade.setStyle(Paint.Style.STROKE);
            shade.setStrokeWidth(dp(this, 3));
        }
        @Override public boolean onTouchEvent(MotionEvent e) {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN || e.getActionMasked() == MotionEvent.ACTION_MOVE) {
                touchX = Math.max(0, Math.min(getWidth(), e.getX()));
                touchY = Math.max(0, Math.min(getHeight(), e.getY()));
                if (tilt && MotionUi.enabled(this)) {
                    setRotationX(6 * (0.5f - touchY / Math.max(1, getHeight())));
                    setRotationY(9 * (touchX / Math.max(1, getWidth()) - .5f));
                    invalidate();
                }
            }
            boolean handled = super.onTouchEvent(e);
            int action = e.getActionMasked();
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) animatePress(false);
            return handled;
        }
        @Override public void setPressed(boolean down) {
            boolean changed = down != isPressed();
            super.setPressed(down);
            if (changed) animatePress(down);
        }
        private void animatePress(boolean down) {
            if (!MotionUi.enabled(this)) {
                setScaleX(1); setScaleY(1); setRotationX(0); setRotationY(0);
                return;
            }
            float scale = down ? .96f : 1;
            ViewPropertyAnimator a = animate().scaleX(scale).scaleY(scale).setStartDelay(0)
                    .setDuration(down ? 90 : 370).setInterpolator(down ? FLUID : SPRING);
            if (!down) a.rotationX(0).rotationY(0);
            a.start();
        }
        @Override public void draw(Canvas c) {
            super.draw(c);
            if (!MotionUi.enabled(this)) return;
            float amount = Math.max(0, Math.min(1, (1 - getScaleX()) / .04f));
            if (amount < .02f) return;
            float radius = dp(this, 14);
            if (tilt) {
                transform.setScale(getWidth() * .44f, 1);
                transform.postRotate(-24);
                transform.postTranslate(touchX, touchY);
                mirror.setLocalMatrix(transform);
                shine.setAlpha((int)(255 * amount));
                c.drawRoundRect(0, 0, getWidth(), getHeight(), radius, radius, shine);
            }
            shade.setColor(Color.argb((int)(40 * amount), 8, 28, 24));
            c.drawRoundRect(1, 1, getWidth()-1, getHeight()-1, radius, radius, shade);
        }
    }

    static final class LookupButton extends PressButton {
        private final android.graphics.drawable.GradientDrawable capsule=new android.graphics.drawable.GradientDrawable();
        private ValueAnimator morph;
        private boolean busy;
        private float progress;
        LookupButton(Context c) { super(c,false); }
        void prepare() {
            capsule.setColor(0xff194f41);
            capsule.setCornerRadius(dp(this,30));
            setBackground(new android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(0x336cb89c),capsule,null));
        }
        void setLoading(boolean next) {
            if (busy==next) return;
            busy=next;
            if(morph!=null) morph.cancel();
            morph=ValueAnimator.ofFloat(progress,next?1:0);
            morph.setDuration(enabled(this)?260:0);
            morph.setInterpolator(FLUID);
            morph.addUpdateListener(a -> {
                progress=(float)a.getAnimatedValue();
                capsule.setCornerRadius(dp(this,30-14*progress));
                setMinHeight(dp(this,58+18*progress));
            });
            morph.start();
        }
    }

    /** A softly lit border around the navigation surface; a shared shader avoids per-frame allocations. */
    static class AuroraBar extends android.widget.LinearLayout {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Matrix rotation = new Matrix();
        private SweepGradient rim;
        private LinearGradient wash;
        AuroraBar(Context c) { super(c); setWillNotDraw(false); setLayerType(LAYER_TYPE_SOFTWARE, null); }
        @Override protected void onSizeChanged(int w, int h, int oldW, int oldH) {
            if (w == 0 || h == 0) return;
            rim = new SweepGradient(w*.5f, h*.5f, new int[]{0xff80baa5,0xfff2d492,0xff83bdd3,0xff80baa5}, null);
            wash = new LinearGradient(0,h,w,0,new int[]{0xfff1faf4,0xfffafaf0,0xffe0f2e9},null,Shader.TileMode.CLAMP);
        }
        @Override protected void onDraw(Canvas c) {
            if (rim == null) return;
            boolean move = MotionUi.enabled(this) && hasWindowFocus() && isShown();
            float t = move ? (SystemClock.uptimeMillis() % 12000) / 12000f : 0;
            float pad = dp(this, 5), r = dp(this, 26);
            rotation.setRotate(t * 360, getWidth()*.5f, getHeight()*.5f);
            rim.setLocalMatrix(rotation);
            paint.setShader(rim);
            paint.setShadowLayer(pad, 0, 1, Color.argb(55+(int)(18*Math.sin(t*6.283)),43,129,99));
            c.drawRoundRect(pad,pad,getWidth()-pad,getHeight()-pad,r,r,paint);
            paint.clearShadowLayer();
            paint.setShader(wash);
            float inner = pad + dp(this, 1.5f);
            c.drawRoundRect(inner,inner,getWidth()-inner,getHeight()-inner,r-2,r-2,paint);
            if (move) postInvalidateDelayed(80);
        }
        @Override public void onWindowFocusChanged(boolean f) { super.onWindowFocusChanged(f); invalidate(); }
    }

    static class Skeleton extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Skeleton(Context context) { super(context); setContentDescription("正在查找地点"); }
        @Override protected void onDraw(Canvas c) {
            float unit = getHeight()/2f;
            boolean motion = MotionUi.enabled(this) && hasWindowFocus();
            float wave = motion ? (float)Math.sin(SystemClock.uptimeMillis()/270.0) : 0;
            p.setColor(Color.rgb(207+(int)(6*wave),223+(int)(5*wave),213+(int)(5*wave)));
            for (int i=0; i<2; i++) {
                float y = (i+.5f)*unit;
                c.drawCircle(dp(this,16),y,dp(this,5),p);
                c.drawRoundRect(dp(this,33), y-dp(this,5),getWidth()*(i==0?.72f:.53f),y+dp(this,5),8,8,p);
            }
            if (motion) postInvalidateDelayed(65);
        }
    }
}
