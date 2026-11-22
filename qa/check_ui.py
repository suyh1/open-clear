"""Check the installed Android UI at two display sizes and capture its actual output."""
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET


def adb(*args):
    return subprocess.check_output(["adb", *args])


def screen():
    adb("shell", "uiautomator", "dump", "/sdcard/chumen-ui.xml")
    return ET.fromstring(adb("exec-out", "cat", "/sdcard/chumen-ui.xml"))


def control(label):
    for _ in range(3):
        for node in screen().iter("node"):
            if label in (node.get("text"), node.get("content-desc"), node.get("class")):
                return node
    raise AssertionError(f"Missing control: {label}")


def tap(label):
    try:
        node = control(label)
    except AssertionError:
        # A large font can move a button beyond the vertical scroll viewport.
        adb("shell", "input", "swipe", "280", "1050", "280", "420", "380")
        node = control(label)
    left, top, right, bottom = map(int, re.findall(r"\d+", node.get("bounds")))
    assert right > left and bottom > top, label
    adb("shell", "input", "tap", str((left + right) // 2), str((top + bottom) // 2))


def capture(name):
    Path("ui-captures", name + ".png").write_bytes(adb("exec-out", "screencap", "-p"))


Path("ui-captures").mkdir(exist_ok=True)
adb("install", "-r", "Chumen.apk")
adb("shell", "wm", "density", "320")
adb("shell", "settings", "put", "secure", "show_ime_with_hard_keyboard", "0")
for name, width, font in [("standard", 786, "1.0"), ("large", 640, "1.6")]:
    adb("shell", "settings", "put", "global", "animator_duration_scale", "1" if name == "standard" else "0")
    adb("shell", "wm", "size", f"{width}x1520")
    adb("shell", "settings", "put", "system", "font_scale", font)
    adb("shell", "am", "force-stop", "family.chumen")
    adb("shell", "am", "start", "-W", "-n", "family.chumen/.MainActivity")
    control("打车")
    capture(name + "-home")
    tap("查公交地铁")
    control("android.widget.EditText")
    if b"mInputShown=true" in adb("shell", "dumpsys", "input_method"):
        adb("shell", "input", "keyevent", "BACK")
    control("查看路线")
    assert control("当前位置").get("selected") == "true"
    capture(name + "-search")
    adb("shell", "input", "text", "Shanghai")
    tap("清空目的地")
    assert control("android.widget.EditText").get("text") != "Shanghai"
    tap("查看路线")
    control("请先输入目的地")
    capture(name + "-validation")
    tap("输入起点")
    assert control("输入起点").get("selected") == "true"
    adb("shell", "input", "text", "Shanghai")
    control("清空起点")
    tap("当前位置")
    tap("输入起点")
    control("Shanghai")
    tap("清空起点")
    tap("当前位置")
    tap("设置常用城市")
    control("城市名称")
    capture(name + "-city")
    tap("展开面板")
    tap("收起面板")
    tap("城市名称")
    adb("shell", "input", "text", "Shanghai")
    tap("保存")
    time.sleep(0.5)
    tap("常用城市 · Shanghai")
    tap("清空城市名称")
    tap("取消")
    tap("常用城市 · Shanghai")
    tap("清空城市名称")
    tap("保存")
    control("设置常用城市")
    tap("返回首页")
    control("查公交地铁")
