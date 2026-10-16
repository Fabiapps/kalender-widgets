# Kalender-Widgets

Android-Widget **„Jetzt & Danach“**: große Uhr, aktueller Termin mit Fortschrittspunkten und nächster Termin.
In der App lassen sich **Wallpaper-Farben** (Material You) und **Transparenz** an- und ausschalten.

> Hinweis: Der Code wurde nicht auf einem echten Gerät getestet. Wenn der Build fehlschlägt oder das Widget
> „Widget kann nicht geladen werden“ anzeigt, das Fehlerprotokoll kopieren und zurückmelden.

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
   Öffentlich ist für Updates per Obtainium einfacher; im Code stehen keine persönlichen Daten.
2. Im Repository: **Settings → Secrets and variables → Actions → New repository secret**
   - `KEYSTORE_BASE64` = kompletter Inhalt der Datei `release.b64`
   - `KEYSTORE_PASSWORD` = dein Passwort aus Schritt 1
   - nur falls du einen anderen Alias als `kalenderwidgets` gewählt hast: `KEY_ALIAS`

## 3. Dateien hochladen

1. ZIP entpacken.
2. Im leeren Repository auf **„uploading an existing file“** klicken.
3. Den **Inhalt** des Ordners `kalender-widgets` hineinziehen, nicht den Ordner selbst.
   Der Ordner `.github` ist versteckt: im Dateimanager mit **Strg+H** einblenden und mit auswählen.
4. **Commit changes**.

Falls `.github` beim Hochladen fehlt: **Add file → Create new file**, als Namen `.github/workflows/build.yml`
eintippen und den Inhalt der Datei hineinkopieren.

## 4. Build abwarten

- Reiter **Actions**: Der Lauf „APK bauen“ startet automatisch und dauert ein paar Minuten.
- Grüner Haken = fertig. Die APK liegt dann rechts auf der Repo-Startseite unter **Releases**.
- Roter Fehler: auf den Lauf klicken, das Protokoll des fehlgeschlagenen Schritts kopieren und zurückmelden.
- Neu bauen ohne Änderung: **Actions → APK bauen → Run workflow**.

## 5. Auf dem Handy installieren

- Release-Seite im Handy-Browser öffnen, `.apk` herunterladen und öffnen.
  Beim ersten Mal muss der Browser die Installation unbekannter Apps erlauben.
  Play Protect kann warnen, weil die App nicht aus dem Play Store kommt.
- Bequemer für Updates: die Open-Source-App **Obtainium** installieren und dort die URL deines Repositorys
  hinzufügen. Bei einem privaten Repository braucht Obtainium einen GitHub-Zugangstoken.

## 6. Einrichten

1. App **Kalender-Widgets** öffnen und **Kalenderzugriff erlauben**.
2. Homescreen gedrückt halten → **Widgets** → **Kalender-Widgets** → **Jetzt & Danach** ziehen.
3. Schalter für Wallpaper-Farben und Transparenz in der App nach Geschmack setzen.

**Arbeitskalender:** Das Widget sieht nur Kalender, die in die Android-Kalenderdatenbank synchronisiert werden.
Bei der Outlook-App muss die Kalender-Synchronisierung in deren Einstellungen aktiv sein.

---

## Verhalten im Überblick

- **Angezeigt:** sichtbare Kalender, ohne abgesagte und von dir abgelehnte Termine.
  Laufen zwei Termine gleichzeitig, wird der zuletzt begonnene gezeigt.
- **Ganztägige Termine heute:** eigene Zeile oben rechts mit hohlem Ring, bei mehreren mit „+N“.
  Mehrtägige Termine (z. B. Urlaub) erscheinen an jedem ihrer Tage. „Jetzt“ zeigt weiterhin nur Termine mit Uhrzeit.
- **Ganztägige Termine danach:** Sie zählen als Beginn ihres Tages (00:00 Uhr). Liegt ihr Tag vor oder auf dem Tag
  des nächsten Termins mit Uhrzeit, stehen sie unter „Danach“, z. B. „DANACH · MORGEN / Feiertag / ganztägig“.
  Um Mitternacht wandern sie in die Zeile für heute. Wer am selben Tag lieber den Termin mit Uhrzeit sehen will:
  In `WidgetUpdater.kt` steht bei `allDayIsNext`, welche Zeile dafür zu ändern ist.
- **Kein Termin gerade:** „Frei – bis 15:00“ bzw. „für heute“.
- **Antippen:** Termin öffnet sich in der Kalender-App, der Rest des Widgets öffnet den heutigen Tag.
- **Aktualisierung:** bei Änderungen im Kalender, zu Terminbeginn und -ende, mit jedem Fortschrittspunkt,
  in den 90 Minuten vor einem Termin minütlich, sonst spätestens alle 15 Minuten.
  Bei ausgeschaltetem Display wird das Handy dafür nicht geweckt.
- **Kein Internetzugriff:** Die App fordert keine Internet-Berechtigung an.

## Bekannte Grenzen

- **Schrift:** Das Widget nutzt die Systemschrift. Ist auf dem Handy Geist als Systemschrift eingestellt,
  erscheint das Widget vermutlich in Geist – das ist nicht getestet. Für Labels wird die System-Monospace-Schrift genutzt.
- **Kein echter Blur:** Der Transparenz-Modus ist halbtransparent mit feiner Kante, ohne Weichzeichnung.
- **Sideloading-Regeln:** Google führt eine Verifizierungspflicht für App-Entwickler ein, die laut Berichten
  ab 2027 weltweit greifen soll. Danach kann für selbst gebaute Apps ein zusätzlicher Installationsweg nötig sein.
