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
    private static final int GREEN = 0xff105750, INK = 0xff162d2b, GOLD = 0xffffd46f;
    private final ExecutorService pool = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private LinearLayout body, results;
    private EditText dest;
    private TextView status;
    private Button find, cityButton;
    private boolean searching, inForm, active;
    private int generation;
    private Future<?> pending;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null && state.getBoolean("form")) form(state.getString("text", ""), false);
        else home();
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putBoolean("form", inForm);
        if (inForm) state.putString("text", dest.getText().toString());
    }
    @Override protected void onStart() { super.onStart(); active = true; }
    @Override protected void onStop() {
        active = false;
        if (searching) { cancel(); status.setText("点「查看路线」继续查询"); }
        super.onStop();
    }
    @Override protected void onDestroy() {
        pool.shutdownNow();
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
        inForm = form;
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setGravity(Gravity.CENTER_VERTICAL);
        body.setFocusableInTouchMode(true);
        body.setPadding(dp(22), dp(24), dp(22), dp(28));
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
        b.setAllCaps(false);
        b.setText(text);
        b.setTextSize(size);
        b.setTextColor(ink);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setMinHeight(dp(58));
        b.setPadding(dp(16), dp(12), dp(16), dp(12));
        b.setStateListAnimator(null);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33000000), panel(color), null));
        return b;
    }
    private void home() {
        screen(false);
        add(body, label("今天怎么出门？", 34, true), 0);
        Button transit = homeAction("查公交地铁", GREEN, -1, R.drawable.train);
        transit.setOnClickListener(v -> form("", true));
        Button taxi = homeAction("打车", GOLD, 0xff382b0d, R.drawable.taxi);
        taxi.setOnClickListener(v -> openAmap(Places.TAXI));
    }
    private Button homeAction(String text, int color, int ink, int icon) {
        Button b = button(text, color, ink, 32);
        b.setMinHeight(dp(108));
        b.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        b.setPadding(dp(22), dp(16), dp(16), dp(16));
        b.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, 0, 0);
        b.setCompoundDrawablePadding(dp(16));
        add(body, b, 20);
        return b;
    }
    private String city() { return getPreferences(MODE_PRIVATE).getString("city", ""); }
    private void form(String initial, boolean keyboard) {
        screen(true);
        body.setGravity(Gravity.TOP);
        Button back = button("返回首页", 0xffe6eeeb, INK, 19);
        body.addView(back);
        back.setOnClickListener(v -> home());
        TextView title = label("你要去哪儿？", 34, true);
        add(body, title, 24);
        dest = new EditText(this);
        dest.setId(View.generateViewId());
        title.setLabelFor(dest.getId());
        dest.setSingleLine();
        dest.setTextSize(25);
        dest.setMinHeight(dp(68));
        dest.setHint("输入地点名称或地址");
        dest.setText(initial);
        dest.setSelection(initial.length());
        dest.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        add(body, dest, 14);
        find = button("查看路线", GREEN, -1, 26);
        find.setOnClickListener(v -> search());
        add(body, find, 16);
        dest.setOnEditorActionListener((v, action, e) -> {
            if (action == EditorInfo.IME_ACTION_SEARCH) { search(); return true; }
            return false;
        });
        status = label("", 20, false);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        add(body, status, 12);
        results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        add(body, results, 0);
        cityButton = button("", 0xffe6eeeb, INK, 18);
        cityLabel();
        cityButton.setOnClickListener(v -> editCity());
        add(body, cityButton, 12);
        add(body, label("地点查询由 Esri 和手机系统提供", 16, false), 16);
        dest.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s,int start,int count,int after) {}
            public void onTextChanged(CharSequence s,int start,int before,int count) { clearResults(); }
            public void afterTextChanged(Editable s) {}
        });
        if (keyboard) {
            dest.requestFocus();
            dest.post(() -> ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE))
                    .showSoftInput(dest, InputMethodManager.SHOW_IMPLICIT));
        }
    }
    private void cityLabel() {
        cityButton.setText(city().isEmpty() ? "设置常用城市（可选）" : "常用城市 · " + city());
    }
    private void editCity() {
        clearResults();
        final EditText field = new EditText(this);
        field.setHint("例如：成都");
        field.setSingleLine();
        field.setTextSize(24);
        field.setText(city());
        new AlertDialog.Builder(this).setTitle("常用城市").setView(field)
                .setPositiveButton("保存", (d, w) -> {
                    getPreferences(MODE_PRIVATE).edit().putString("city", field.getText().toString().trim()).apply();
                    clearResults();
                    cityLabel();
                }).setNegativeButton("返回", null).show();
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
        status.setText("");
    }
    private void search() {
        if (searching) return;
        final String q = dest.getText().toString().trim(), city = city();
        if (q.isEmpty()) { dest.setError("请先输入目的地"); dest.requestFocus(); return; }
        ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(dest.getWindowToken(), 0);
        clearResults();
        final int token = generation;
        searching = true;
        find.setEnabled(false);
        find.setText("正在查找…");
        handler.postDelayed(() -> {
            if (token == generation) {
                cancel();
                status.setText("查找超时，请检查网络后重试");
            }
        }, 18000);
        pending = pool.submit(() -> {
            List<Places.Place> places = Collections.emptyList();
            boolean failed = false;
            try { places = Places.search(q, city); }
            catch (Exception unavailable) { failed = true; }
            if (Thread.currentThread().isInterrupted()) return;
            if (places.isEmpty() && Geocoder.isPresent()) {
                try { places = deviceSearch(q, city); }
                catch (Exception unavailable) { failed = true; }
            }
            final List<Places.Place> found = places;
            final boolean error = failed;
            runOnUiThread(() -> {
                if (!active || token != generation) return;
                cancel();
                if (found.isEmpty()) {
                    status.setText(error ? "地点服务暂时不可用，请检查网络后再试。" : "没找到这个地方。试试加上城市、区名或街道。");
                } else if (found.size() == 1 && found.get(0).precise) {
                    openAmap(found.get(0).route());
                } else {
                    status.setText("点一下你要去的地点");
                    for (Places.Place place : found) {
                        String title = place.address.equals(place.name) ? place.name : place.name + "\n" + place.address;
                        Button choice = button(title, 0xffe6eeeb, INK, 21);
                        choice.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                        choice.setOnClickListener(v -> openAmap(place.route()));
                        add(results, choice, 8);
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
