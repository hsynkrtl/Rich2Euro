# Rich2Euro – Umstellung auf Paketkennung `de.mrt.rich2euro`

Vollständige Migration der externen Anwendungs-ID, der internen Modul- und Paketstruktur sowie der zugehörigen System- und Cloud-Dienste auf die einheitliche Kennung `de.mrt.rich2euro`, ohne bestehende Funktionen oder das Erscheinungsbild zu verändern.

## User Review & Critical Decisions

> [!IMPORTANT]
> Gemäß Ihrer Aktualisierung wird das Paketpräfix auf `de.mrt.` gesetzt. Durch die Änderung der Anwendungs-ID erkennt Android die App bei der Installation als eigenständiges Paket (`de.mrt.rich2euro`).

- **Aktualisierte Paketkennung**: Einheitliche Umstellung auf `de.mrt.rich2euro`.
- **Bestätigter Umfang**: Vollständige Anpassung sowohl der externen App-ID (für Installation, Dateifreigaben und Store-Veröffentlichung) als auch der gesamten internen Paket- und Namensraum-Architektur inklusive Tests und Cloud-Registrierung.
- **Bestätigte Metadaten**: Der sichtbare App-Name **Rich2Euro**, die Versionsnummer **1.0** sowie sämtliche bestehenden Analyse-, Kamera- und Multi-Server-Funktionen bleiben unverändert erhalten.

---

## 1. Overview & Core Concept

- **Was die Änderung bewirkt**: Die App **Rich2Euro** erhält durchgängig die Hersteller- und Paketidentität `de.mrt.rich2euro` – von der installierbaren APK/AAB-Kennung über den internen Ressourcen-Namensraum bis hin zu Datei-Provider-Autoritäten und der Google-Projektregistrierung.
- **Zielgruppe & Einsatzkontext**: Sammler und Nutzer europäischer 2-Euro-Münzen sowie der Herausgeber (`de.mrt`), der eine kompakte, saubere Domain-Kennung für Verteilung, Updates und Store-Veröffentlichungen nutzt.
- **Zentraler Mehrwert**: Kurze, eindeutige Marken- und Paketidentität (`de.mrt.rich2euro`) ohne Vorlagen-Altlasten, bei lückenloser Kompilierbarkeit und Testabdeckung.

---

## 2. User Experience & Visual Design

- **Unveränderte Kern-Nutzerabläufe (Zero Feature Loss)**:
  1. **App-Start & Eigener API-Schlüssel (BYOK)**: Beim ersten Start fragt die App den persönlichen API-Schlüssel ab (optional ergänzt um einen Backup-Schlüssel) und bietet unten den Direkt-Button zur offiziellen Google-API-Schlüsselseite.
  2. **Münzerfassung (Vorder- & Rückseite)**: Auswahl über die berechtigungsfreie Fotogalerie oder Aufnahme über die integrierte Live-Kamera mit **2-Euro-Kreissucher**, die das Münzbild exakt auf den angezeigten Kreis zuschneidet. Unterhalb der Karten liegen „Vorder- & Rückseite tauschen" und direkt darunter gestapelt „Auswahl zurücksetzen" (ohne Tipps-Rahmen und ohne Textüberlagerung auf dem oberen Hero-Bild).
  3. **Schnelle Multi-Server-Analyse & 6-Punkte-Gutachten**: Automatische Auswertung über den ausfallsicheren Multi-Server-Pool mit Live-Statusanzeige und sofortigem Serverwechsel bei Überlastung; strukturierte Ausgabe auf Deutsch zu *1. Land & Motiv*, *2. Prägejahr*, *3. Auflage & Seltenheit*, *4. Zustand & Erhaltung*, *5. Realistischer Marktwert* und *6. Wichtiger Hinweis zu Fake-Preisen* sowie lokaler Verlauf und Sammler-Ratgeber.
- **Visuelle Identität & Theme**:
  - *Ästhetische Richtung*: **Royal Euro & Bullion Gold** – seriöse numismatische Sammler-Ästhetik mit klarem Kontrast und ruhiger Hierarchie.
  - *Farbpalette & Stimmung*: Tiefes Royal-Euro-Blau (`#0B2545` / Primärakzente) kombiniert mit warmem Münzgold (`#C89B3C`) auf klaren Material-3-Oberflächen für Hell- und Dunkelmodus.
  - *Typografie & Hierarchie*: Lokale Display-Serife (*Playfair Display*) für Überschriften und Wertangaben gepaart mit präziser Sans-Serif (*Plus Jakarta Sans*) für Gutachtentexte und Bedienelemente.
  - *Layout & Komponenten*: Großzügiges 8-dp-Raster, Edge-to-Edge-Darstellung unter Beachtung aller Systemleisten-Abstände sowie barrierefreie Touch-Flächen (mindestens 48 dp).
- **Interaktives Feedback & Bewegung**: Sanfte Übergänge zwischen Eingabe, Kamera-Kreissucher, Live-Serverstatus während der Analyse und Detailgutachten bleiben vollständig erhalten.

---

## 3. Key Product Decisions & Trade-Offs

- **Entscheidung 1: Einheitliche Angleichung von App-ID und internem Namensraum auf `de.mrt.rich2euro`**
  - *Gewählter Ansatz*: Sowohl die externe `applicationId` als auch der interne `namespace` und sämtliche Quell- und Testpakete werden synchron auf `de.mrt.rich2euro` umgestellt.
  - *Warum*: Vermeidet Abweichungen zwischen generierten Ressourcenklassen, Manifest-Verweisen, Tests und Paketpfaden und sorgt für eine kompakte, konsistente Codebasis unter der `de.mrt`-Domain.
  - *Verworfene Alternative*: Nur die externe `applicationId` zu ändern und den Quellcode unter dem alten Vorlagen-Paket zu belassen – dies würde zu unnötiger Doppelstruktur führen.

- **Entscheidung 2: Dynamische System-Autoritäten & Cloud-Client-Synchronisierung**
  - *Gewählter Ansatz*: Dateifreigaben für Kameraaufnahmen nutzen weiterhin dynamisch `${applicationId}.fileprovider` (löst automatisch zu `de.mrt.rich2euro.fileprovider` auf), und die Cloud-Dienstkonfiguration wird für `de.mrt.rich2euro` registriert bzw. abgeglichen.
  - *Warum*: Verhindert Build-Abbrüche des Google-Services-Prüfschritts wegen fehlender Paketzuordnung und stellt sicher, dass temporäre Kamerabilder konfliktfrei über die neue Paketkennung verarbeitet werden.

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

- **Architektur- & Komponentendiagramm**:

```text
┌──────────────────────────────────────────────────────────────────────┐
│                 Android-Anwendungspaket: de.mrt.rich2euro            │
├──────────────────────────────────────────────────────────────────────┤
│  Manifest & System-Integration                                       │
│  ├── Haupt-Einstiegspunkt (Edge-to-Edge Activity)                    │
│  ├── Kamera- & Internet-Berechtigungen                               │
│  └── Dynamischer FileProvider (de.mrt.rich2euro.fileprovider)        │
├──────────────────────────────────────────────────────────────────────┤
│  UI-Schicht (de.mrt.rich2euro.ui.*)                                  │
│  ├── App-Navigation & Zustandssteuerung (ViewModel + StateFlow)      │
│  ├── API-Schlüssel-Einrichtung (Primär- & optionaler Backup-Key)     │
│  ├── Münz-Aufnahme & Kreissucher-Kamera (Exakter Kreiszuschnitt)     │
│  ├── 6-Punkte-Numismatik-Gutachten, Verlauf & Sammler-Ratgeber       │
│  └── Royal-Euro-&-Gold-Theme & Bimetall-Komponenten                  │
├──────────────────────────────────────────────────────────────────────┤
│  Domänen- & Datenschicht (de.mrt.rich2euro.model / .data)            │
│  ├── Lokaler DataStore-Schlüsselspeicher (BYOK-Persistenz)           │
│  └── Multi-Server-Analyse-Pool (18s Fast-Timeout & Sofort-Failover)  │
├──────────────────────────────────────────────────────────────────────┤
│  Qualitätssicherung & Build-Prüfung                                  │
│  ├── Cloud-Client-Abgleich für Paket de.mrt.rich2euro                │
│  └── Lokale JVM- & Robolectric-Tests im neuen Paketnamensraum        │
└──────────────────────────────────────────────────────────────────────┘
```

- **Datenmodell & Zustand**:
  - Alle Domänenmodelle (`CoinSide`, `CoinAnalysisResult`, `AppTab`, Ratgeber-Einträge), der `Rich2EuroUiState` sowie die lokale `DataStore`-Persistenz für API-Schlüssel behalten ihre exakte Struktur und ziehen geschlossen in den Namensraum `de.mrt.rich2euro` um.
- **Interaktive Komponenten- & Zustandszuordnung**:
  - **API-Key-Verwaltung**: Speichern, Bearbeiten und Öffnen der Google-AI-Studio-Seite aktualisieren reaktiv den lokalen Schlüsselspeicher und schalten direkt zur Münzanalyse frei.
  - **Kamera & Bildauswahl**: Der Kreissucher schneidet Aufnahmen präzise auf den Münzkreis zu und übergibt die URI über die neue Paketkennung an den Analyse-Zustand; Tauschen- und Zurücksetzen-Aktionen aktualisieren Vorder- und Rückseite unmittelbar.
  - **Multi-Server-Auswertung**: Startet die Kaskade über verfügbare schnelle Server, aktualisiert live den Status im Ladeindikator und überführt das strukturierte 6-Punkte-Ergebnis in Bericht und Sitzungsverlauf.
- **Integrations- & Verifikationsablauf**:
  1. Umzug sämtlicher Haupt-, Test- und Instrumentierungs-Quellen in die Paketstruktur `de.mrt.rich2euro` inklusive Bereinigung der alten Verzeichnisse.
  2. Aktualisierung von `namespace` und `applicationId` sowie Registrierung des neuen Paketnamens in der Projekt-Dienstkonfiguration.
  3. Vollständige Verifikation durch App-Kompilierung und Ausführung der lokalen Robolectric-Unit-Tests.
