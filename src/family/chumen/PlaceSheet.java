package family.chumen;

import android.animation.*;
import android.app.*;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;

/** The same surface grows from the trigger and returns to it on back or cancel. */
final class PlaceSheet extends Dialog {
    final LinearLayout content;
    private final FrameLayout root;
    private final LinearLayout sheet;
    private final View scrim, anchor;
    private final android.graphics.drawable.GradientDrawable surface = new GradientDrawable();
    private final Button handle;
    private ValueAnimator animation;
    private boolean expanded, closing, entered;
    private float openness;
    private int compactHeight, fullHeight;
    private Runnable afterClose;
    private BitmapDrawable sharedFace;

    PlaceSheet(Activity owner, View anchor) {
        super(owner);
        this.anchor = anchor;
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        root = new FrameLayout(owner);
        scrim = new View(owner);
        scrim.setBackgroundColor(0x660e251c);
        scrim.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        scrim.setOnClickListener(v -> dismiss());
        root.addView(scrim, -1, -1);
        sheet = new LinearLayout(owner);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setGravity(Gravity.TOP);
        sheet.setFocusableInTouchMode(true);
        sheet.setPadding(dp(14),0,dp(14),dp(8));
        surface.setColor(0xfff8faf4);
        sheet.setBackground(surface);
        sheet.setClipToOutline(true);
        sheet.setElevation(dp(8));
        root.addView(sheet, new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL));
        handle = new Button(owner);
        handle.setAllCaps(false);
        handle.setText("━");
        handle.setTextSize(22);
        handle.setTextColor(0xff678479);
        handle.setBackgroundColor(Color.TRANSPARENT);
        handle.setMinHeight(dp(46));
        handle.setPadding(0,0,0,0);
        handle.setContentDescription("展开面板");
        handle.setOnClickListener(v -> snap(!expanded));
        handle.setOnTouchListener(new Drag());
        sheet.addView(handle,-1,dp(48));
        ScrollView scroll = new ScrollView(owner);
        scroll.setFillViewport(false);
        sheet.addView(scroll, new LinearLayout.LayoutParams(-1,0,1));
        content = new LinearLayout(owner);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(6),0,dp(6),dp(12));
        scroll.addView(content);
        setContentView(root);
        Window window = getWindow();
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        window.setWindowAnimations(0);
        root.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob) -> {
            if (b-t == ob-ot && r-l == or-ol) return;
            fullHeight = Math.max(1,b-t - dp(8));
            int width = Math.min(r-l, dp(540));
            content.measure(View.MeasureSpec.makeMeasureSpec(Math.max(1,width-dp(28)),View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
            compactHeight = Math.min(fullHeight, Math.min(Math.round(fullHeight*.83f),
                    dp(58) + content.getMeasuredHeight()));
            FrameLayout.LayoutParams p = (FrameLayout.LayoutParams)sheet.getLayoutParams();
            p.width = width;
            p.height = expanded ? fullHeight : compactHeight;
            sheet.setLayoutParams(p);
            if (!entered) { entered = true; sheet.post(() -> morph(true)); }
        });
        sheet.setAlpha(0);
        scrim.setAlpha(0);
    }
    @Override protected void onStart() {
        super.onStart();
        getWindow().setLayout(-1,-1);
    }
    private int dp(float n) { return MotionUi.dp(root,n); }
    void closeThen(Runnable action) { afterClose = action; dismiss(); }
    @Override public void dismiss() {
        if (closing) return;
        closing = true;
        if (isShowing() && sheet.getHeight()>0 && MotionUi.enabled(sheet)) morph(false);
        else finishClose();
    }
    void dispose() {
        afterClose = null;
        closing = true;
        if (animation != null) animation.cancel();
        releaseFace();
        super.dismiss();
    }
    private void finishClose() {
        releaseFace();
        super.dismiss();
        if (afterClose != null) { Runnable run = afterClose; afterClose = null; run.run(); }
    }
    private void releaseFace() {
        if(sharedFace==null) return;
        sheet.getOverlay().remove(sharedFace);
        anchor.setAlpha(1);
        sharedFace=null;
    }
    private void morph(boolean opening) {
        if (opening && (closing || !isShowing())) return;
        if (animation != null) animation.cancel();
        int[] src = new int[2], dst = new int[2];
        boolean linked = anchor != null && anchor.isShown() && anchor.isAttachedToWindow()
                && anchor.getGlobalVisibleRect(new Rect());
        if (linked) anchor.getLocationOnScreen(src);
        if (opening && linked && MotionUi.enabled(sheet)) {
            float scale=Math.min(1f,1024f/Math.max(anchor.getWidth(),anchor.getHeight()));
            Bitmap bitmap=Bitmap.createBitmap(Math.max(1,Math.round(anchor.getWidth()*scale)),
                    Math.max(1,Math.round(anchor.getHeight()*scale)),Bitmap.Config.ARGB_8888);
            Canvas snapshot=new Canvas(bitmap);
            snapshot.scale(scale,scale);
            anchor.draw(snapshot);
            sharedFace=new BitmapDrawable(sheet.getResources(),bitmap);
            sharedFace.setGravity(Gravity.FILL);
            sheet.getOverlay().add(sharedFace);
            anchor.setAlpha(0);
        }
        if(sharedFace!=null) sharedFace.setBounds(0,0,sheet.getWidth(),sheet.getHeight());
        root.getLocationOnScreen(dst);
        float sx = linked ? Math.min(1,anchor.getWidth()/(float)Math.max(1,sheet.getWidth())) : .95f;
        float sy = linked ? Math.min(1,anchor.getHeight()/(float)Math.max(1,sheet.getHeight())) : .85f;
        float x = linked ? src[0]-dst[0]-sheet.getLeft() : 0;
        float y = linked ? src[1]-dst[1]-sheet.getTop() : dp(40);
        sheet.setPivotX(0); sheet.setPivotY(0);
        sheet.setAlpha(1);
        animation = ValueAnimator.ofFloat(openness, opening ? 1 : 0);
        animation.setDuration(MotionUi.enabled(sheet) ? (opening ? 390 : 240) : 0);
        animation.setInterpolator(MotionUi.FLUID);
        animation.addUpdateListener(a -> {
            float f = (float)a.getAnimatedValue(), k = 1-f;
            openness=f;
            sheet.setScaleX(sx + (1-sx)*f); sheet.setScaleY(sy + (1-sy)*f);
            sheet.setTranslationX(x*k); sheet.setTranslationY(y*k);
            surface.setCornerRadius(dp(22 + 26*k));
            scrim.setAlpha(f);
            content.setAlpha(Math.max(0, (f-.15f)/.85f));
            handle.setAlpha(f);
            if(sharedFace!=null) sharedFace.setAlpha(Math.round(255*Math.max(0,1-2.5f*f)));
        });
        animation.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;
            @Override public void onAnimationCancel(Animator a) { cancelled = true; }
            @Override public void onAnimationEnd(Animator a) { if (!opening && !cancelled) finishClose(); }
        });
        animation.start();
    }
    private void height(int h) {
        ViewGroup.LayoutParams p = sheet.getLayoutParams();
        p.height = Math.max(dp(64),h);
        sheet.setLayoutParams(p);
    }
    private void snap(boolean up) {
        if (closing || openness < .999f) return;
        expanded = up;
        handle.setContentDescription(up ? "收起面板" : "展开面板");
        if (animation != null) animation.cancel();
        animation = ValueAnimator.ofInt(sheet.getHeight(),up ? fullHeight : compactHeight);
        animation.setInterpolator(MotionUi.SPRING);
        animation.setDuration(MotionUi.enabled(sheet) ? 340 : 0);
        animation.addUpdateListener(a -> height((int)a.getAnimatedValue()));
        animation.start();
    }
    private final class Drag implements View.OnTouchListener {
        float start;
        int initial;
        boolean moved;
        VelocityTracker tracker;
        private void track(MotionEvent event) {
            // The handle moves under the finger. Velocity must use screen coordinates.
            MotionEvent screenEvent=MotionEvent.obtain(event);
            screenEvent.offsetLocation(event.getRawX()-event.getX(),event.getRawY()-event.getY());
            tracker.addMovement(screenEvent);
            screenEvent.recycle();
        }
        public boolean onTouch(View v, MotionEvent e) {
            switch(e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (closing) return false;
                    if (animation != null && animation.isRunning()) return false;
                    start=e.getRawY(); initial=sheet.getHeight(); moved=false;
                    if (tracker != null) tracker.recycle();
                    tracker=VelocityTracker.obtain(); track(e); return true;
                case MotionEvent.ACTION_MOVE:
                    if (tracker == null) return false;
                    track(e);
                    float delta=start-e.getRawY();
                    if (Math.abs(delta)>ViewConfiguration.get(v.getContext()).getScaledTouchSlop()) moved=true;
                    if (moved) {
                        float target=initial+delta;
                        if(target>fullHeight) target=fullHeight + Math.min(dp(36),(target-fullHeight)*.2f);
                        if(target<compactHeight) target=compactHeight-(compactHeight-target)*.45f;
                        height(Math.round(target));
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if(tracker==null) return false;
                    track(e); tracker.computeCurrentVelocity(1000);
                    float speed=tracker.getYVelocity(); tracker.recycle(); tracker=null;
                    if(e.getActionMasked()==MotionEvent.ACTION_CANCEL) snap(expanded);
                    else if(!moved) v.performClick();
                    else if(!expanded && (speed>dp(850) || start-e.getRawY() < -dp(92))) dismiss();
                    else snap(Math.abs(speed)>dp(420) ? speed<0 : sheet.getHeight()>(fullHeight+compactHeight)/2);
                    return true;
                default: return false;
            }
        }
    }
}
