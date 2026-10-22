# Kalender-Widgets

Drei Android-Widgets für den Homescreen:

- **„Jetzt & Danach"** (4×2): große Uhr, aktueller Termin mit Fortschrittspunkten, nächster Termin.
- **„Tagesleiste"** (4×2): Uhrzeit, Canvas-Zeitleiste des heutigen Tages, laufender und nächster Termin.
- **„Monat"** (4×4): Monatsraster mit Terminpunkten; Tippen auf einen Tag zeigt unten seine Termine.

Jedes Widget ist einzeln konfigurierbar (Erscheinungsbild, Wallpaper-Farben, Transparenz).
In der App lassen sich die **Standardwerte für neue Widgets** einstellen.

> Hinweis: Der Code wurde nicht auf einem echten Gerät getestet. Wenn der Build fehlschlägt oder das Widget
> „Widget kann nicht geladen werden" anzeigt, das Fehlerprotokoll kopieren und zurückmelden.

Voraussetzung: Android 12 oder neuer, ein GitHub-Konto.

---

## 1. Signaturschlüssel erstellen (einmalig, dringend empfohlen)

Android installiert ein Update nur, wenn es mit demselben Schlüssel signiert ist wie die installierte Version.
Ohne festen Schlüssel müsstest du die App vor jedem Update deinstallieren, und das Widget verschwindet vom Homescreen.

Auf dem Linux-Rechner im Terminal:

```bash
keytool -genkeypair -v -keystore release.jks -alias kalenderwidgets -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 release.jks > release.b64
```

- `keytool` fragt nach einem Passwort und ein paar Angaben (Name usw., dürfen beliebig sein).
- Falls `keytool` fehlt (Arch/CachyOS): `sudo pacman -S jdk-openjdk`
- `release.jks` gut aufbewahren und **niemals** ins Repository hochladen.

## 2. Repository anlegen und Schlüssel hinterlegen

1. Auf github.com: **New repository**, Name z. B. `kalender-widgets`, ohne README erstellen.
2. Im Repository: **Settings → Secrets and variables → Actions → New repository secret**
   - `KEYSTORE_BASE64` = kompletter Inhalt der Datei `release.b64`
   - `KEYSTORE_PASSWORD` = dein Passwort aus Schritt 1
   - nur falls du einen anderen Alias als `kalenderwidgets` gewählt hast: `KEY_ALIAS`

## 3. Dateien hochladen

1. ZIP entpacken.
2. Im leeren Repository auf **„uploading an existing file"** klicken.
3. Den **Inhalt** des Ordners `kalender-widgets` hineinziehen, nicht den Ordner selbst.
   Der Ordner `.github` ist versteckt: im Dateimanager mit **Strg+H** einblenden und mit auswählen.
4. **Commit changes**.

## 4. Build abwarten

- Reiter **Actions**: Der Lauf „APK bauen" startet automatisch und dauert ein paar Minuten.
- Grüner Haken = fertig. Die APK liegt dann rechts auf der Repo-Startseite unter **Releases**.
- Roter Fehler: auf den Lauf klicken, das Protokoll des fehlgeschlagenen Schritts kopieren und zurückmelden.
- Neu bauen ohne Änderung: **Actions → APK bauen → Run workflow**.

## 5. Auf dem Handy installieren

- Release-Seite im Handy-Browser öffnen, `.apk` herunterladen und öffnen.
- Bequemer für Updates: **Obtainium** installieren und dort die URL des Repositorys eintragen.

## 6. Einrichten

1. App **Kalender-Widgets** öffnen und **Kalenderzugriff erlauben**.
2. Standardwerte einstellen: Erscheinungsbild (Dunkel / Hell / Automatisch), Wallpaper-Farben, Transparenz.
3. Homescreen gedrückt halten → **Widgets** → **Kalender-Widgets** → Widget ziehen.
4. Beim Hinzufügen öffnet sich ein Einrichtungsdialog für dieses Widget.
   Nachträglich: Widget gedrückt halten → **Widget bearbeiten**.

---

## Widgets im Überblick

### Jetzt & Danach

- **Oben rechts:** ganztägige Termine heute (hohler Ring, bei mehreren „+N").
- **Jetzt:** laufender Termin mit Uhrzeit und Fortschrittspunkten; ohne Termin „Frei – bis …".
- **Danach:** Label zeigt nur den Zeitpunkt: „IN 28 MIN", „IN 2 STD", „MORGEN" oder das Datum. Bei ganztägigem Termin entsprechend. Kein nächstes Ereignis: Label ausgeblendet, nur „Nichts geplant".

### Tagesleiste

- **Kopfzeile:** aktuelle Uhrzeit links; Wochentag, Datum und ganztägige Termine heute rechts.
- **Zeitleiste:** Canvas-Bitmap, Zeitraum 08–20 Uhr (erweitert auf heutige Termine).
  Vergangene Termine gedämpft, laufender in Akzentfarbe, zukünftige in Textfarbe.
  Senkrechter Strich = jetzt. Darunter 5 Stundenlabels.
- **Fußzeile:** links aktueller Termin mit Endzeit (oder „Frei"), rechts nächster mit Startzeit.

### Erscheinungsbild

| Modus | Klassisch | Wallpaper-Farben |
|-------|-----------|-----------------|
| **Dunkel** | Schwarz, roter Akzent | Material-You-Dunkelpalette |
| **Hell** | Hellgrau #F4F4F2, roter Akzent | Material-You-Hellpalette |
| **Automatisch** | Folgt System-Dark-Mode | Folgt Dark-Mode + Wallpaper |

### Gemeinsames Verhalten

- **Angezeigt:** sichtbare Kalender, ohne abgesagte und von dir abgelehnte Termine.
- **Antippen:** Termin öffnet sich in der Kalender-App, Rest öffnet den heutigen Tag.
- **Aktualisierung:** bei Kalenderänderungen, zu Terminbeginn/-ende, minütlicher Countdown
  90 Min vor einem Termin, sonst alle 15 Minuten. Kein Wakeup bei ausgeschaltetem Display.
- **Kein Internetzugriff** angefordert.

## Bekannte Grenzen

- **Schrift:** Das Widget nutzt die Systemschrift.
- **Kein echter Blur:** Transparenz-Modus ist halbtransparent ohne Weichzeichnung.
- **Zeitleiste** nutzt `setImageViewBitmap`: bei Farbwechsel (Automatisch + Dark-Mode-Toggle)
  wird das Bitmap beim nächsten Update (spätestens 15 Min) neu gezeichnet. Für sofortigen
  Wechsel: App öffnen oder Widget aktualisieren.
