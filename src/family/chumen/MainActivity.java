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
    private LinearLayout body, results;
    private EditText dest;
    private DestinationField destinationField;
    private TextView status;
    private Button find, cityButton, locationButton;
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
            if (showHints && !suggestionBias.equals(location.nearby())) queueSuggestions();
        });
        if (state != null && state.getBoolean("form")) form(state.getString("text", ""), false);
        else home();
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putBoolean("form", inForm);
        if (inForm) state.putString("text", dest.getText().toString());
    }
    @Override protected void onStart() {
        super.onStart();
        active = true;
        if (inForm) {
            if (showHints) queueSuggestions();
            location.start();
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
        setContentView(scroll);
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
        Button b = new Button(this);
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
        ImageView mark = new ImageView(this);
        mark.setImageResource(R.drawable.launcher);
        mark.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        body.addView(mark, dp(46), dp(46));
        add(body, label("怎么出门？", 34, true), 18);
        Button transit = homeAction("查公交地铁", GREEN, -1, R.drawable.train);
        transit.setOnClickListener(v -> form("", true));
        Button taxi = homeAction("打车", GOLD, 0xff382b0d, R.drawable.taxi);
        taxi.setOnClickListener(v -> openAmap(Places.TAXI));
    }
    private Button homeAction(String text, int color, int ink, int icon) {
        Button b = new TravelPass(this, icon);
        styleButton(b, text, color, ink, 30);
        b.setMinHeight(dp(132));
        b.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        b.setPadding(dp(20), dp(18), dp(16), dp(18));
        add(body, b, 14);
        return b;
    }
    private String city() { return getPreferences(MODE_PRIVATE).getString("city", ""); }
    private void form(String initial, boolean keyboard) {
        screen(true);
        showHints = true;
        body.setGravity(Gravity.TOP);
        LinearLayout top = new LinearLayout(this);
        boolean largeType = getResources().getConfiguration().fontScale >= 1.4f
                && getResources().getConfiguration().screenWidthDp < 400;
        top.setOrientation(largeType ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        add(body, top, 0);
        Button back = button("首页", PAPER, INK, 19);
        back.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.back, 0, 0, 0);
        back.setContentDescription("返回首页");
        top.addView(back, -2, -2);
        back.setOnClickListener(v -> home());
        locationButton = button("使用当前位置", SOFT, INK, 18);
        locationButton.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.pin, 0, 0, 0);
        locationButton.setCompoundDrawablePadding(dp(6));
        LinearLayout.LayoutParams locationParams = largeType ? new LinearLayout.LayoutParams(-1, -2)
                : new LinearLayout.LayoutParams(0, -2, 1);
        if (!largeType) locationParams.leftMargin = dp(8);
        top.addView(locationButton, locationParams);
        locationButton.setOnClickListener(v -> requestFix());
        add(body, label("去哪儿？", 32, true), 18);
        destinationField = new DestinationField(this);
        dest = destinationField.input;
        dest.setText(initial);
        dest.setSelection(initial.length());
        dest.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        add(body, destinationField, 14);
        dest.setOnEditorActionListener((v, action, e) -> {
            if (action == EditorInfo.IME_ACTION_SEARCH) { search(null); return true; }
            return false;
        });
        status = label("", 18, false);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        status.setVisibility(View.GONE);
        add(body, status, 8);
        results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        results.setBackground(panel(0xffffffff));
        results.setClipToOutline(true);
        GradientDrawable line = new GradientDrawable();
        line.setColor(0xffd9e2d9);
        line.setSize(1, dp(1));
        results.setDividerDrawable(line);
        results.setDividerPadding(dp(14));
        results.setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE);
        add(body, results, 6);
        find = button("查看路线", GREEN, -1, 26);
        find.setOnClickListener(v -> search(null));
        add(body, find, 12);
        cityButton = button("", PAPER, GREEN, 18);
        cityLabel();
        cityButton.setOnClickListener(v -> editCity());
        add(body, cityButton, 12);
        TextView attribution = label("Esri 根据输入的地名提供联想。允许定位后，使用大致位置优先查找附近地点。", 16, false);
        attribution.setTextColor(MUTED);
        add(body, attribution, 10);
        dest.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s,int start,int count,int after) {}
            public void onTextChanged(CharSequence s,int start,int before,int count) { queueSuggestions(); }
            public void afterTextChanged(Editable s) {}
        });
        if (active) location.start();
        if (keyboard) {
            dest.requestFocus();
            dest.post(() -> ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE))
                    .showSoftInput(dest, InputMethodManager.SHOW_IMPLICIT));
        }
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
        cityButton.setText(city().isEmpty() ? "设置常用城市" : "常用城市 · " + city());
    }
    private void editCity() {
        showHints = false;
        clearResults();
        final EditText field = new EditText(this);
        field.setHint("例如：成都");
        field.setSingleLine();
        field.setTextSize(24);
        field.setText(city());
        new AlertDialog.Builder(this).setTitle("常用城市").setView(field)
                .setPositiveButton("保存", (d, w) -> {
                    getPreferences(MODE_PRIVATE).edit().putString("city", field.getText().toString().trim()).apply();
                    cityLabel();
                }).setNegativeButton("返回", null).setOnDismissListener(d -> {
                    if (active && inForm) queueSuggestions();
                }).show();
    }
    private void cancel() {
        generation++;
        searching = false;
        if (pending != null) {
            pending.cancel(true);
            pending = null;
        }
        handler.removeCallbacksAndMessages(null);
        if (find != null) { find.setEnabled(true); find.setText("查看路线"); }
    }
    private void clearResults() {
        cancel();
        results.removeAllViews();
        message("", false);
    }
    private void message(String text, boolean error) {
        status.setTextColor(error ? 0xffa42e21 : MUTED);
        status.setText(text);
        status.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
    }
    private void resultRow(String name, String address, String match, View.OnClickListener select) {
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
        row.setOnClickListener(select);
        add(results, row, 0);
    }
    private void queueSuggestions() {
        showHints = true;
        clearResults();
        if (!active || !inForm) return;
        String query = dest.getText().toString().trim(), city = city(), nearby = location.nearby();
        suggestionBias = nearby;
        if (query.isEmpty()) return;
        int token = generation;
        handler.postDelayed(() -> {
            pending = suggestions.submit(() -> {
                List<Places.Suggestion> hints;
                try { hints = Places.suggest(query, city, nearby); }
                catch (Exception offline) { hints = Collections.emptyList(); }
                final List<Places.Suggestion> found = hints;
                runOnUiThread(() -> {
                    if (!active || !inForm || token != generation) return;
                    if (found.isEmpty()) {
                        message("暂无联想，可继续输入或点「查看路线」", false);
                        return;
                    }
                    for (Places.Suggestion hint : found) {
                        resultRow(hint.text, "", query, v -> search(hint));
                    }
                    results.announceForAccessibility("有" + found.size() + "个建议地点");
                });
            });
        }, 350);
    }
    private void search(Places.Suggestion picked) {
        if (searching) return;
        final String q = dest.getText().toString().trim(), city = city(), nearby = location.nearby();
        if (q.isEmpty()) {
            dest.requestFocus();
            destinationField.showError();
            message("请先输入目的地", true);
            return;
        }
        ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(dest.getWindowToken(), 0);
        clearResults();
        showHints = false;
        final int token = generation;
        searching = true;
        find.setEnabled(false);
        find.setText("正在查找…");
        handler.postDelayed(() -> {
            if (token == generation) {
                cancel();
                message("查找超时，请检查网络后重试", true);
            }
        }, 18000);
        pending = pool.submit(() -> {
            List<Places.Place> places = Collections.emptyList();
            boolean failed = false;
            try { places = Places.search(q, city, nearby, picked); }
            catch (Exception unavailable) { failed = true; }
            if (Thread.currentThread().isInterrupted()) return;
            if (places.isEmpty() && Geocoder.isPresent()) {
                try { places = deviceSearch(picked == null ? q : picked.text, city); }
                catch (Exception unavailable) { failed = true; }
            }
            final List<Places.Place> found = places;
            final boolean error = failed;
            runOnUiThread(() -> {
                if (!active || token != generation) return;
                cancel();
                if (found.isEmpty()) {
                    message(error ? "地点服务暂时不可用，请检查网络后再试。" : "没找到这个地方。试试加上城市、区名或街道。", true);
                } else if (found.size() == 1 && found.get(0).precise) {
                    openAmap(found.get(0).route(location.origin()));
                } else {
                    message("点一下你要去的地点", false);
                    for (Places.Place place : found) {
                        resultRow(place.name, place.address, q, v -> openAmap(place.route(location.origin())));
                    }
                }
            });
        });
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
