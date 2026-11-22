"""Check the installed Android UI at two display sizes and capture its actual output."""
from pathlib import Path
import re
import subprocess
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
    left, top, right, bottom = map(int, re.findall(r"\d+", control(label).get("bounds")))
    assert right > left and bottom > top, label
    adb("shell", "input", "tap", str((left + right) // 2), str((top + bottom) // 2))


def capture(name):
    Path("ui-captures", name + ".png").write_bytes(adb("exec-out", "screencap", "-p"))


Path("ui-captures").mkdir(exist_ok=True)
adb("install", "-r", "Chumen.apk")
adb("shell", "wm", "density", "320")
adb("shell", "settings", "put", "secure", "show_ime_with_hard_keyboard", "0")
for name, width, font in [("standard", 786, "1.0"), ("large", 640, "1.6")]:
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
    capture(name + "-search")
    adb("shell", "input", "text", "Shanghai")
    tap("清空目的地")
    assert control("android.widget.EditText").get("text") != "Shanghai"
    tap("查看路线")
    control("请先输入目的地")
    capture(name + "-validation")
    tap("返回首页")
    control("查公交地铁")
