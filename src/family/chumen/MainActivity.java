package family.chumen;

import android.app.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.*;
import android.location.*;
import android.net.Uri;
import android.os.*;
import android.text.*;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
    private static final int GREEN = 0xff194f41, INK = 0xff173c32, GOLD = 0xffffd77e;
    private static final int PAPER = 0xfff6f8f3, SOFT = 0xffe4eee5, MUTED = 0xff4e655a;
    private final ExecutorService pool = Executors.newSingleThreadExecutor();
    private final ExecutorService suggestions = Executors.newFixedThreadPool(2);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private LinearLayout page, body, results, widePasses;
    private EditText dest;
    private DestinationField destinationField;
    private DestinationField startField;
    private Button currentSegment, manualSegment;
    private Places.Place selectedStart, waitingDestination;
    private PlaceSheet sheet;
    private boolean manual, editingStart, binding, ready;
    private String startDraft = "";
    private TextView status;
    private MotionUi.LookupButton find;
    private Button cityButton, locationButton;
    private TripLocation location;
    private String suggestionBias = "";
    private boolean showHints;
    private boolean searching, inForm, active;
    private int generation;
    private Future<?> pending;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        location = new TripLocation(this, text -> {
            if (!inForm || locationButton == null) return;
            locationButton.setText(text);
            if (!manual && showHints && !suggestionBias.equals(location.nearby())) queueSuggestions();
        });
        if (state != null && state.getBoolean("form")) {
            manual = state.getBoolean("manual");
            startDraft = state.getString("startText", "");
            if (state.containsKey("startLat")) selectedStart = new Places.Place(startDraft,
                    state.getString("startAddress",""),state.getDouble("startLat"),state.getDouble("startLon"),true);
            form(state.getString("text",""),false);
        }
        else home();
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putBoolean("form", inForm);
        if (inForm) {
            state.putString("text", dest.getText().toString());
            state.putBoolean("manual",manual);
            state.putString("startText",startField.input.getText().toString());
            if (selectedStart != null) {
                state.putDouble("startLat",selectedStart.lat); state.putDouble("startLon",selectedStart.lon);
                state.putString("startAddress",selectedStart.address);
            }
        }
    }
    @Override protected void onStart() {
        super.onStart();
        active = true;
        if (inForm) {
            if (showHints) queueSuggestions();
            if (!manual) location.start();
        }
    }
    @Override protected void onStop() {
        active = false;
        location.stop();
        if (searching) message("点「查看路线」继续查询", false);
        cancel();
        super.onStop();
    }
    @Override protected void onDestroy() {
        if (sheet != null) { sheet.setOnDismissListener(null); sheet.dispose(); }
        pool.shutdownNow();
        suggestions.shutdownNow();
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
    @Override public void onBackPressed() {
        if (inForm) home();
        else super.onBackPressed();
    }

    private int dp(int n) { return Math.round(getResources().getDisplayMetrics().density * n); }
    private void screen(boolean form) {
        ready = false;
        cancel();
        location.stop();
        inForm = form;
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(PAPER);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setGravity(Gravity.CENTER_VERTICAL);
        body.setFocusableInTouchMode(true);
        int inset = Math.max(dp(20), (getResources().getDisplayMetrics().widthPixels - dp(540)) / 2);
        body.setPadding(inset, dp(20), inset, dp(24));
        scroll.addView(body);
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(PAPER);
        page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(page);
    }
    private void add(LinearLayout parent, View child, int gap) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(gap);
        parent.addView(child, params);
    }
    private TextView label(String text, int size, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(INK);
        view.setTextSize(size);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }
    private GradientDrawable panel(int color) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(14));
        return shape;
    }
    private Button button(String text, int color, int ink, int size) {
        Button b = new MotionUi.PressButton(this,false);
        styleButton(b, text, color, ink, size);
        return b;
    }
    private void styleButton(Button b, String text, int color, int ink, int size) {
        b.setAllCaps(false);
        b.setText(text);
        b.setTextSize(size);
        b.setTextColor(ink);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setMinHeight(dp(58));
        b.setPadding(dp(16), dp(12), dp(16), dp(12));
        b.setStateListAnimator(null);
        StateListDrawable states = new StateListDrawable();
        GradientDrawable focus = panel(color);
        focus.setStroke(dp(3), color == GREEN ? GOLD : GREEN);
        states.addState(new int[]{android.R.attr.state_focused}, focus);
        states.addState(new int[]{}, panel(color));
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x28000000), states, null));
    }
    private void home() {
        screen(false);
        manual = false; editingStart=false; selectedStart=null; waitingDestination=null; startDraft="";
        widePasses = null;
        ImageView mark = new ImageView(this);
        mark.setImageResource(R.drawable.launcher);
        mark.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        body.addView(mark, dp(46), dp(46));
        add(body, label("怎么出门？", 34, true), 18);
        if (getResources().getConfiguration().screenWidthDp >= 540
                && getResources().getConfiguration().fontScale < 1.4f) {
            widePasses = new LinearLayout(this);
            add(body,widePasses,14);
        }
        Button transit = homeAction("查公交地铁", GREEN, -1, R.drawable.train);
        transit.setOnClickListener(v -> form("", true));
        Button taxi = homeAction("打车", GOLD, 0xff382b0d, R.drawable.taxi);
        taxi.setOnClickListener(v -> openAmap(Places.TAXI));
    }
    private Button homeAction(String text, int color, int ink, int icon) {
        Button b = new TravelPass(this, icon);
        styleButton(b, text, color, ink, 30);
        b.setMinHeight(dp(icon == R.drawable.train ? 152 : 124));
        b.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        b.setPadding(dp(20), dp(18), dp(16), dp(18));
        if(widePasses == null) add(body,b,14);
        else {
            LinearLayout.LayoutParams cell = new LinearLayout.LayoutParams(0,-1,icon==R.drawable.train?1.2f:1f);
            if(widePasses.getChildCount()>0) cell.leftMargin=dp(12);
            widePasses.addView(b,cell);
        }
        return b;
    }
    private String city() { return getPreferences(MODE_PRIVATE).getString("city", ""); }
    private void form(String initial, boolean keyboard) {
        screen(true);
        showHints = true;
        body.setGravity(Gravity.TOP);
        MotionUi.AuroraBar nav = new MotionUi.AuroraBar(this);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        nav.setPadding(dp(10),dp(6),dp(10),dp(6));
        LinearLayout.LayoutParams navParams = new LinearLayout.LayoutParams(-1,-2);
        int inset = body.getPaddingLeft();
        navParams.setMargins(inset,dp(4),inset,0);
        page.addView(nav,0,navParams);
        Button back = button("首页",0,INK,18);
        back.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.back,0,0,0);
        back.setContentDescription("返回首页");
        back.setOnClickListener(v -> home());
        nav.addView(back,-2,-2);
        cityButton = button("",SOFT,GREEN,18);
        cityLabel();
        cityButton.setOnClickListener(v -> editCity());
        nav.addView(cityButton,new LinearLayout.LayoutParams(0,-2,1));
        add(body, label("去哪儿？", 32, true), 0);
        LinearLayout segments = new LinearLayout(this);
        segments.setPadding(dp(4),dp(4),dp(4),dp(4));
        segments.setBackground(panel(SOFT));
        add(body,segments,10);
        currentSegment = button("当前位置",SOFT,INK,19);
        manualSegment = button("输入起点",SOFT,INK,19);
        for(Button segment : new Button[]{currentSegment,manualSegment}) {
            segment.setPadding(dp(4),dp(6),dp(4),dp(6));
            segments.addView(segment,new LinearLayout.LayoutParams(0,-2,1));
        }
        currentSegment.setOnClickListener(v -> setOriginMode(false,true));
        manualSegment.setOnClickListener(v -> setOriginMode(true,true));
        startField = new DestinationField(this,"起点");
        startField.input.setText(startDraft);
        startField.input.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        add(body,startField,8);
        locationButton = button("使用当前位置",PAPER,GREEN,18);
        locationButton.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
        locationButton.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.pin,0,0,0);
        locationButton.setCompoundDrawablePadding(dp(8));
        locationButton.setOnClickListener(v -> requestFix());
        add(body,locationButton,0);
        destinationField = new DestinationField(this,"目的地");
        dest = destinationField.input;
        dest.setText(initial);
        dest.setSelection(initial.length());
        dest.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        add(body,destinationField,8);
        status = label("",18,false);
        status.setVisibility(View.GONE);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        add(body,status,8);
        results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        results.setBackground(panel(0xffffffff));
        results.setClipToOutline(true);
        GradientDrawable line=panel(0xffd9e2d9);
        line.setSize(1,dp(1));
        results.setDividerDrawable(line);
        results.setDividerPadding(dp(14));
        results.setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE);
        add(body,results,4);
        find=new MotionUi.LookupButton(this);
        styleButton(find,"查看路线",GREEN,-1,26);
        find.prepare();
        find.setOnClickListener(v -> resolve(false,null));
        add(body,find,8);
        TextView attribution=label("输入的地名将发送给 Esri。定位后，优先查找附近地点。",16,false);
        attribution.setTextColor(MUTED);
        add(body,attribution,12);
        bindField(startField,true);
        bindField(destinationField,false);
        ready=true;
        setOriginMode(manual,false);
        if(keyboard) focus(dest,true);
    }
    private void focus(EditText field, boolean keyboard) {
        field.requestFocus();
        if(keyboard) field.post(() -> {
            if(field.hasFocus() && field.isShown())
                getSystemService(InputMethodManager.class).showSoftInput(field,InputMethodManager.SHOW_IMPLICIT);
        });
    }
    private void bindField(DestinationField field, boolean start) {
        View.OnFocusChangeListener style=field.input.getOnFocusChangeListener();
        field.input.setOnFocusChangeListener((v,hasFocus) -> {
            style.onFocusChange(v,hasFocus);
            if(ready && hasFocus && !binding && inForm && sheet==null) {
                editingStart=start;
                queueSuggestions();
            }
        });
        field.input.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s,int pos,int count,int after) {}
            public void onTextChanged(CharSequence s,int pos,int before,int count) {
                if(!ready || binding) return;
                if(start) selectedStart=null;
                else waitingDestination=null;
                editingStart=start;
                queueSuggestions();
            }
            public void afterTextChanged(Editable s) {}
        });
        field.input.setOnEditorActionListener((v,action,event) -> {
            if(action!=EditorInfo.IME_ACTION_NEXT && action!=EditorInfo.IME_ACTION_SEARCH) return false;
            resolve(start,null); return true;
        });
    }
    private void setOriginMode(boolean custom, boolean interacted) {
        manual=custom;
        editingStart=custom && interacted;
        waitingDestination=null;
        clearResults();
        for(Button b:new Button[]{currentSegment,manualSegment}) {
            boolean on = b==(custom?manualSegment:currentSegment);
            b.setSelected(on);
            b.setTextColor(on?-1:INK);
            b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22000000),panel(on?GREEN:SOFT),null));
        }
        startField.setVisibility(custom?View.VISIBLE:View.GONE);
        locationButton.setVisibility(custom?View.GONE:View.VISIBLE);
        if(custom) location.stop();
        else if(active) location.start();
        if(interacted) focus(custom?startField.input:dest,true);
    }
    private void requestFix() {
        if (location.allowed()) {
            LocationManager manager = getSystemService(LocationManager.class);
            if (manager != null && !manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
                    && !manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
            } else location.start();
            return;
        }
        String coarse = android.Manifest.permission.ACCESS_COARSE_LOCATION;
        if (getPreferences(MODE_PRIVATE).getBoolean("locationDenied", false)
                && !shouldShowRequestPermissionRationale(coarse)) {
            new AlertDialog.Builder(this).setMessage("在系统设置的「权限」里允许位置访问，即可优先查找附近地点。")
                    .setNegativeButton("继续输入地点", null).setPositiveButton("去设置", (d,w) ->
                        startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:" + getPackageName())))).show();
        } else {
            requestPermissions(new String[]{coarse, android.Manifest.permission.ACCESS_FINE_LOCATION}, 12);
        }
    }
    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(code, permissions, grants);
        if (code != 12 || grants.length == 0) return;
        getPreferences(MODE_PRIVATE).edit().putBoolean("locationDenied", !location.allowed()).apply();
        if (active && inForm) location.start();
    }
    private void cityLabel() {
        String city=city();
        cityButton.setText(city.isEmpty()?"设置常用城市":"常用城市 · "+city);
        if(MotionUi.enabled(cityButton) && getResources().getConfiguration().fontScale<1.4f) {
            cityButton.setSingleLine();
            cityButton.setEllipsize(TextUtils.TruncateAt.MARQUEE);
            cityButton.setMarqueeRepeatLimit(2);
            cityButton.setSelected(!city.isEmpty());
        } else {
            cityButton.setSingleLine(false);
        }
    }
    private PlaceSheet newSheet(View anchor) {
        getSystemService(InputMethodManager.class).hideSoftInputFromWindow(anchor.getWindowToken(),0);
        PlaceSheet opened=new PlaceSheet(this,anchor);
        sheet=opened;
        opened.setOnDismissListener(d -> { if(sheet==opened) sheet=null; });
        return opened;
    }
    private void editCity() {
        if(sheet!=null) return;
        clearResults();
        showHints=false;
        PlaceSheet opened=newSheet(cityButton);
        LinearLayout content=opened.content;
        TextView heading=label("常用城市",30,true);
        heading.setCompoundDrawablesRelativeWithIntrinsicBounds(0,0,R.drawable.city,0);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        add(content,heading,0);
        add(content,label("查地点时优先在这个城市搜索。留空可搜索其他城市。",17,false),6);
        DestinationField cityInput=new DestinationField(this,"城市名称");
        cityInput.input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        cityInput.input.setText(city());
        cityInput.input.setSelection(cityInput.input.length());
        add(content,cityInput,14);
        Runnable save=() -> {
            String value=cityInput.input.getText().toString().trim();
            getPreferences(MODE_PRIVATE).edit().putString("city",value).apply();
            cityLabel();
            opened.dismiss();
        };
        Button saveButton=button("保存",GREEN,-1,22);
        saveButton.setOnClickListener(v -> save.run());
        add(content,saveButton,14);
        Button discard=button("取消",0,INK,18);
        discard.setOnClickListener(v -> opened.dismiss());
        add(content,discard,2);
        cityInput.input.setOnEditorActionListener((v,action,event) -> {
            if(action!=EditorInfo.IME_ACTION_DONE) return false;
            save.run(); return true;
        });
        opened.setOnDismissListener(d -> {
            if(sheet!=opened) return;
            sheet=null;
            if(ready && active && inForm) queueSuggestions();
        });
        opened.show();
    }
    private void cancel() {
        generation++;
        searching = false;
        if (pending != null) {
            pending.cancel(true);
            pending = null;
        }
        handler.removeCallbacksAndMessages(null);
        if(results!=null && results.getChildCount()==1 && results.getChildAt(0) instanceof MotionUi.Skeleton)
            results.removeAllViews();
        if (find != null) { find.setLoading(false); find.setEnabled(true); find.setText("查看路线"); }
    }
    private void clearResults() {
        cancel();
        startField.clearError();
        destinationField.clearError();
        results.removeAllViews();
        message("", false);
    }
    private void message(String text, boolean error) {
        status.setTextColor(error ? 0xffa42e21 : MUTED);
        status.setText(text);
        status.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
    }
    private void resultRow(String name, String address, String match, View.OnClickListener detail,
                           View.OnClickListener quickSelect) {
        android.text.SpannableStringBuilder text = new android.text.SpannableStringBuilder(name);
        int start = name.indexOf(match);
        if (!match.isEmpty() && start >= 0) {
            text.setSpan(new android.text.style.StyleSpan(Typeface.BOLD), start, start + match.length(), 0);
        }
        if (!address.isEmpty() && !address.equals(name)) {
            text.append("\n").append(address);
            text.setSpan(new android.text.style.RelativeSizeSpan(0.88f), name.length()+1, text.length(), 0);
            text.setSpan(new android.text.style.ForegroundColorSpan(MUTED), name.length()+1, text.length(), 0);
        }
        Button row = button("", 0xffffffff, INK, 21);
        row.setTypeface(Typeface.DEFAULT);
        row.setText(text);
        row.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        row.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.pin, 0, 0, 0);
        row.setCompoundDrawablePadding(dp(12));
        row.setOnClickListener(detail);
        View entry=row;
        if(quickSelect!=null) {
            LinearLayout group=new LinearLayout(this);
            group.setGravity(Gravity.CENTER_VERTICAL);
            group.addView(row,new LinearLayout.LayoutParams(0,-2,1));
            Button use=button("选用",SOFT,GREEN,17);
            use.setMinWidth(dp(55));
            use.setMinimumWidth(dp(55));
            use.setPadding(dp(8),dp(5),dp(8),dp(5));
            use.setOnClickListener(quickSelect);
            LinearLayout.LayoutParams shortcut=new LinearLayout.LayoutParams(-2,-2);
            shortcut.rightMargin=dp(6);
            group.addView(use,shortcut);
            entry=group;
        }
        add(results,entry,0);
        MotionUi.enter(entry,results.getChildCount()-1);
    }
    private void skeleton() {
        results.removeAllViews();
        results.addView(new MotionUi.Skeleton(this),-1,dp(62));
    }
    private void placeResultsAfter(boolean start) {
        View field=start?startField:destinationField;
        if(body.indexOfChild(status)==body.indexOfChild(field)+1) return;
        body.removeView(status);
        body.removeView(results);
        int index=body.indexOfChild(field)+1;
        body.addView(status,index);
        body.addView(results,index+1);
    }
    private void queueSuggestions() {
        if (!ready || !inForm || sheet!=null) return;
        showHints = true;
        clearResults();
        if (!active) return;
        final boolean start = manual && editingStart;
        placeResultsAfter(start);
        String query = (start?startField.input:dest).getText().toString().trim(), city = city(), nearby = searchBias(start);
        suggestionBias = nearby;
        if (query.isEmpty()) return;
        int token = generation;
        handler.postDelayed(() -> {
            skeleton();
            pending = suggestions.submit(() -> {
                List<Places.Suggestion> hints;
                try { hints = Places.suggest(query, city, nearby); }
                catch (Exception offline) { hints = Collections.emptyList(); }
                final List<Places.Suggestion> found = hints;
                runOnUiThread(() -> {
                    if (!active || !inForm || token != generation) return;
                    results.removeAllViews();
                    if (found.isEmpty()) {
                        message("暂无联想，可继续输入或点「查看路线」", false);
                        return;
                    }
                    for (Places.Suggestion hint : found) {
                        resultRow(hint.text,"",query,v -> suggestionDetails(v,start,hint),v -> resolve(start,hint));
                    }
                    results.announceForAccessibility("有" + found.size() + "个建议地点");
                });
            });
        }, 350);
    }
    private String searchBias(boolean start) {
        if(!manual) return location.nearby();
        if(start || selectedStart==null) return "";
        return String.format(Locale.ROOT,"&location=%.2f,%.2f",selectedStart.lon,selectedStart.lat);
    }
    private void suggestionDetails(View anchor, boolean start, Places.Suggestion hint) {
        showPlace(anchor,start,hint.text,"",() -> resolve(start,hint));
    }
    private void requireText(DestinationField field,String prompt) {
        focus(field.input,true);
        clearResults();
        field.showError();
        message(prompt,true);
    }
    private void resolve(boolean start, Places.Suggestion picked) {
        if(searching || sheet!=null) return;
        final DestinationField field=start?startField:destinationField;
        final String q=field.input.getText().toString().trim(), city=city(), nearby=searchBias(start);
        if(q.isEmpty()) {
            requireText(field,start?"请先输入起点":"请先输入目的地");
            return;
        }
        if(!start && manual && startField.input.getText().toString().trim().isEmpty()) {
            requireText(startField,"请输入起点，或选择「当前位置」");
            return;
        }
        if(start && selectedStart!=null && picked==null) {
            choose(true,selectedStart); return;
        }
        getSystemService(InputMethodManager.class).hideSoftInputFromWindow(field.getWindowToken(),0);
        placeResultsAfter(start);
        clearResults();
        showHints=false;
        final int token=generation;
        searching=true;
        find.setEnabled(false);
        find.setLoading(true);
        find.setText(start?"正在查找起点…":"正在查找目的地…");
        handler.postDelayed(() -> { if(searching && token==generation) skeleton(); },220);
        handler.postDelayed(() -> {
            if(token!=generation) return;
            clearResults();
            message("查找超时，请检查网络后重试",true);
        },18000);
        pending=pool.submit(() -> {
            List<Places.Place> places=Collections.emptyList();
            boolean failed=false;
            try { places=Places.search(q,city,nearby,picked); }
            catch(Exception unavailable) { failed=true; }
            if(Thread.currentThread().isInterrupted()) return;
            if(places.isEmpty() && Geocoder.isPresent()) {
                try { places=deviceSearch(picked==null?q:picked.text,city); }
                catch(Exception unavailable) { failed=true; }
            }
            final List<Places.Place> found=places;
            final boolean error=failed;
            runOnUiThread(() -> {
                if(!active || token!=generation) return;
                clearResults();
                if(found.isEmpty()) {
                    String which=start?"起点":"目的地";
                    message(error?"地点服务暂时不可用，请检查网络后再试。":"没找到这个"+which+"。试试加上城市或街道。",true);
                } else if(found.size()==1 && found.get(0).precise) {
                    choose(start,found.get(0));
                } else {
                    message(start?"请选择出发地点":"请选择目的地",false);
                    if(!start && found.size()>1) offerComparison(found);
                    for(Places.Place place:found) {
                        resultRow(place.name,place.address,q,
                                v -> showPlace(v,start,place.name,place.address,() -> choose(start,place)),null);
                    }
                }
            });
        });
    }
    private void choose(boolean start, Places.Place place) {
        if(start) {
            selectedStart=place;
            binding=true;
            startField.input.setText(place.name);
            startField.input.setSelection(place.name.length());
            binding=false;
            clearResults();
            editingStart=false;
            if(waitingDestination!=null) {
                Places.Place end=waitingDestination;
                waitingDestination=null;
                openAmap(end.route(place.asOrigin()));
            } else {
                focus(dest,dest.length()==0);
                if(dest.length()>0) message("已设置起点，点「查看路线」继续",false);
            }
        } else if(manual && selectedStart==null) {
            waitingDestination=place;
            binding=true;
            dest.setText(place.name);
            dest.setSelection(place.name.length());
            binding=false;
            resolve(true,null);
        } else {
            openAmap(place.route(manual?selectedStart.asOrigin():location.origin()));
        }
    }
    private void showPlace(View anchor,boolean start,String name,String address,Runnable accept) {
        if(sheet!=null) return;
        showHints=false;
        PlaceSheet opened=newSheet(anchor);
        add(opened.content,label(name,28,true),0);
        if(!address.isEmpty() && !address.equals(name)) add(opened.content,label(address,19,false),10);
        Button use=button(start?"从这里出发":"去这里",GREEN,-1,23);
        use.setOnClickListener(v -> opened.closeThen(() -> {
            sheet=null;
            accept.run();
        }));
        add(opened.content,use,18);
        Button back=button("返回地点列表",0,INK,18);
        back.setOnClickListener(v -> opened.dismiss());
        add(opened.content,back,2);
        opened.show();
    }
    private void offerComparison(List<Places.Place> choices) {
        final double lat,lon;
        if(manual && selectedStart!=null) { lat=selectedStart.lat; lon=selectedStart.lon; }
        else if(!manual) {
            Location fix=location.current();
            if(fix==null) return;
            lat=fix.getLatitude(); lon=fix.getLongitude();
        } else return;
        Button compare=button("比较距起点的距离",SOFT,GREEN,18);
        compare.setOnClickListener(v -> {
            if(sheet!=null) return;
            PlaceSheet opened=newSheet(v);
            add(opened.content,label("直线距离",27,true),0);
            add(opened.content,label("左右滑动查看各地点距起点的距离",17,false),4);
            DistanceProfile profile=new DistanceProfile(this,choices,lat,lon);
            add(opened.content,profile,14);
            Button use=button("去选中的地点",GREEN,-1,20);
            use.setOnClickListener(w -> opened.closeThen(() -> {
                sheet=null;
                choose(false,choices.get(profile.index()));
            }));
            add(opened.content,use,10);
            opened.show();
        });
        add(results,compare,0);
    }
    @SuppressWarnings("deprecation")
    private List<Places.Place> deviceSearch(String query, String city) throws Exception {
        Geocoder service = new Geocoder(getApplicationContext(), Locale.SIMPLIFIED_CHINESE);
        String full = city.isEmpty() || query.startsWith(city) ? query : city + " " + query;
        List<Address> rows = service.getFromLocationName(full, 6);
        List<Places.Place> matches = new ArrayList<>();
        if (rows == null) return matches;
        for (Address row : rows) {
            if (!row.hasLatitude() || !row.hasLongitude()) continue;
            String name = row.getFeatureName(), address = row.getAddressLine(0);
            if (address == null) address = TextUtils.join(" ", Arrays.asList(
                    safe(row.getAdminArea()), safe(row.getLocality()), safe(name))).trim();
            if (!city.isEmpty() && !address.contains(city.replaceFirst("市$", ""))) continue;
            if (TextUtils.isEmpty(name)) name = address;
            if (TextUtils.isEmpty(name)) continue;
            matches.add(new Places.Place(name, address, row.getLatitude(), row.getLongitude(), name.equals(query)));
        }
        return matches;
    }
    private static String safe(String text) { return text == null ? "" : text; }
    private void openAmap(String uri) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(uri)).setPackage("com.autonavi.minimap"));
        } catch (ActivityNotFoundException unavailable) {
            new AlertDialog.Builder(this).setTitle("需要高德地图")
                    .setMessage("请先在小米应用商店安装或更新高德地图，然后回到「出门」再试。")
                    .setPositiveButton("知道了", null).show();
        }
    }
}
