"""Erzeugt die *_clear.xml-Layouts für den Hintergrund „Transparent“.

Kopiert die Widget-Layouts und gibt jeder Schrift (TextView, TextClock) einen weichen Schatten,
damit weiße Schrift auf jedem Hintergrundbild lesbar bleibt. Nach jeder Änderung an einem der
Ausgangslayouts neu ausführen:  python3 tools/make_clear_layouts.py
(widget_sentence_clear.xml entsteht separat zusammen mit widget_sentence.xml.)
"""
import pathlib
import re

LAYOUTS = ["widget_now_next", "widget_day_bar", "widget_day_bar_compact", "widget_month", "widget_month_event_item"]
SHADOW = '\n{indent}android:shadowColor="#99000000"\n{indent}android:shadowDy="1"\n{indent}android:shadowRadius="4"'
DIR = pathlib.Path(__file__).resolve().parent.parent / "app/src/main/res/layout"

for name in LAYOUTS:
    xml = (DIR / f"{name}.xml").read_text(encoding="utf-8")

    def add_shadow(m: re.Match) -> str:
        tag = m.group(0)
        if "android:shadowColor" in tag:
            return tag
        indent = re.search(r"\n(\s+)android:", tag).group(1)
        return re.sub(r"(\s*/?>)$", SHADOW.format(indent=indent) + r"\1", tag, count=1)

    out, n = re.subn(r"<(TextView|TextClock)\b[^>]*>", add_shadow, xml)
    note = (f"<!-- Erzeugt aus {name}.xml von tools/make_clear_layouts.py: dasselbe Layout mit Schatten unter\n"
            f"     der Schrift für den Hintergrund „Transparent“. Nicht von Hand ändern. -->\n")
    out = out.replace("?>\n", "?>\n" + note, 1)
    (DIR / f"{name}_clear.xml").write_text(out, encoding="utf-8")
    print(f"{name}_clear.xml: {n} Schriften mit Schatten")
