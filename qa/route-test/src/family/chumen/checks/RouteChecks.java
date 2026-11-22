package family.chumen.checks;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

/** Exercise the installed app with fixed POIs and intercept the final AMap VIEW intent. */
public final class RouteChecks extends Instrumentation {
    private Activity activity;
    private volatile Uri handedOff;

    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle report=new Bundle();
        try {
            URL.setURLStreamHandlerFactory(protocol -> !"https".equals(protocol) ? null : new URLStreamHandler() {
                @Override protected URLConnection openConnection(URL url) {
                    return new HttpURLConnection(url) {
                        public void connect() {}
                        public void disconnect() {}
                        public boolean usingProxy() { return false; }
                        @Override public InputStream getInputStream() throws IOException {
                            try { return new ByteArrayInputStream(fixture(url).getBytes(StandardCharsets.UTF_8)); }
                            catch(JSONException invalid) { throw new IOException(invalid); }
                        }
                    };
                }
            });
            addMonitor(new ActivityMonitor() {
                @Override public ActivityResult onStartActivity(Intent intent) {
                    if (!"com.autonavi.minimap".equals(intent.getPackage())) return null;
                    handedOff=intent.getData();
                    return new ActivityResult(Activity.RESULT_CANCELED,null);
                }
            });
            Intent launch=new Intent().setClassName("family.chumen","family.chumen.MainActivity");
            activity=startActivitySync(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            click("查公交地铁");
            write(0,"终点");
            click("查看路线");
            checkHandoff(null,"终点");

            click("输入起点");
            write(0,"起点 & A");
            write(1,"终点");
            click("查看路线");
            checkHandoff("起点 & A","终点");

            write(1,"候选");
            click("查看路线");
            click("比较距起点的距离");
            dialogNode("直线距离", false);
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            waitForIdleSync();
            click("候选乙\n测试区候选乙");
            dialogNode("去这里",true);
            checkHandoff("起点 & A","候选乙");

            click("当前位置");
            write(0,"终点");
            click("查看路线");
            checkHandoff(null,"终点");
            report.putString("route_checks","passed");
            finish(Activity.RESULT_OK,report);
        } catch(Throwable problem) {
            report.putString("route_error",android.util.Log.getStackTraceString(problem));
            finish(Activity.RESULT_CANCELED,report);
        }
    }
    private static JSONObject place(String name,double lat,double lon) throws JSONException {
        JSONObject attr=new JSONObject().put("PlaceName",name).put("LongLabel","测试区"+name).put("Addr_type","POI");
        return new JSONObject().put("score",100).put("address",name).put("attributes",attr)
                .put("location",new JSONObject().put("y",lat).put("x",lon));
    }
    private static String fixture(URL u) throws JSONException,IOException {
        if(!"geocode.arcgis.com".equals(u.getHost())) throw new IOException("Unexpected test host");
        if(u.getPath().endsWith("/suggest")) return "{\"suggestions\":[]}";
        String name=Uri.parse(u.toString()).getQueryParameter("SingleLine");
        JSONArray rows=new JSONArray();
        if("候选".equals(name)) {
            rows.put(place("候选甲",31.21,121.40)).put(place("候选乙",31.22,121.42));
        } else if("起点 & A".equals(name)) rows.put(place(name,31.20,121.41));
        else if("终点".equals(name)) rows.put(place(name,31.23,121.43));
        return new JSONObject().put("candidates",rows).toString();
    }
    private void checkHandoff(String start,String end) {
        for(int i=0;i<100 && handedOff==null;i++) SystemClock.sleep(100);
        Uri u=handedOff; handedOff=null;
        if(u==null || !"amapuri".equals(u.getScheme()) || !"1".equals(u.getQueryParameter("t"))
                || !end.equals(u.getQueryParameter("dname"))
                || !java.util.Objects.equals(start,u.getQueryParameter("sname")))
            throw new AssertionError("Unexpected AMap route: "+u);
        if(start==null) {
            if(u.getQueryParameter("slat")!=null || u.getQueryParameter("slon")!=null)
                throw new AssertionError("Current-location fallback incorrectly pinned");
        } else if(Double.parseDouble(u.getQueryParameter("slat"))!=31.20 ||
                Double.parseDouble(u.getQueryParameter("slon"))!=121.41) throw new AssertionError("Wrong start");
        double expectedLat=end.equals("终点")?31.23:31.22;
        if(Double.parseDouble(u.getQueryParameter("dlat"))!=expectedLat) throw new AssertionError("Wrong end");
    }
    private void write(int index,String text) {
        runOnMainSync(() -> {
            java.util.List<EditText> fields=new java.util.ArrayList<>();
            visit(activity.getWindow().getDecorView(),v -> { if(v instanceof EditText) fields.add((EditText)v); });
            EditText edit=fields.get(index); edit.requestFocus(); edit.setText(text);
        });
    }
    private static void visit(View v,java.util.function.Consumer<View> action) {
        if(!v.isShown()) return;
        action.accept(v);
        if(v instanceof ViewGroup) {
            ViewGroup g=(ViewGroup)v;
            for(int i=0;i<g.getChildCount();i++) visit(g.getChildAt(i),action);
        }
    }
    private void click(String text) {
        for(int n=0;n<80;n++) {
            AtomicReference<Button> hit=new AtomicReference<>();
            runOnMainSync(() -> {
                visit(activity.getWindow().getDecorView(),v -> {
                    if(v instanceof Button && v.isEnabled() && text.contentEquals(((Button)v).getText())) hit.set((Button)v);
                });
                if(hit.get()!=null) hit.get().performClick();
            });
            if(hit.get()!=null) return;
            SystemClock.sleep(100);
        }
        throw new AssertionError("Missing app button: "+text);
    }
    private void dialogNode(String text,boolean click) {
        for(int i=0;i<80;i++) {
            AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();
            if(root!=null) for(AccessibilityNodeInfo node:root.findAccessibilityNodeInfosByText(text)) {
                if(text.contentEquals(node.getText()) && (!click || node.performAction(AccessibilityNodeInfo.ACTION_CLICK))) return;
            }
            SystemClock.sleep(100);
        }
        throw new AssertionError("Missing sheet control: "+text);
    }
}
