package de.mrt.rich2euro.model

import android.net.Uri

enum class CoinSide {
    OBVERSE,
    REVERSE
}

enum class AppTab(val routeId: String) {
    ANALYZE("route_analyze"),
    HISTORY("route_history"),
    GUIDE("route_guide")
}

data class CoinAnalysisResult(
    val id: String,
    val timestampMillis: Long,
    val isValidTwoEuroCoin: Boolean,
    val coinTitle: String,
    val countryName: String,
    val mintMark: String,
    val coinCategory: String,
    val rarityLevel: String,
    val conditionGrade: String,
    val estimatedValueRangeEur: String,
    val landUndMotiv: String,
    val praegejahr: String,
    val auflageUndSeltenheit: String,
    val zustandUndErhaltung: String,
    val realistischerMarktwert: String,
    val hinweisFakePreise: String,
    val obverseUri: Uri,
    val reverseUri: Uri
) {
    fun toShareableGermanReport(): String {
        return buildString {
            appendLine("🪙 Rich2Euro – Numismatisches Gutachten")
            appendLine("Münze: $coinTitle")
            appendLine("Kategorie: $coinCategory | Seltenheit: $rarityLevel")
            appendLine("Geschätzter Marktwert: $estimatedValueRangeEur")
            appendLine("────────────────────────────────")
            appendLine()
            appendLine("1. Land & Motiv:")
            appendLine(landUndMotiv)
            appendLine()
            appendLine("2. Prägejahr:")
            appendLine(praegejahr)
            appendLine()
            appendLine("3. Auflage & Seltenheit:")
            appendLine(auflageUndSeltenheit)
            appendLine()
            appendLine("4. Zustand & Erhaltung ($conditionGrade):")
            appendLine(zustandUndErhaltung)
            appendLine()
            appendLine("5. Realistischer Marktwert:")
            appendLine(realistischerMarktwert)
            appendLine()
            appendLine("6. Wichtiger Hinweis zu Fake-Preisen:")
            appendLine(hinweisFakePreise)
        }
    }
}

data class NumismaticGuideTopic(
    val id: String,
    val badge: String,
    val title: String,
    val summary: String,
    val bulletPoints: List<String>
)

object NumismaticGuideRepository {
    val topics: List<NumismaticGuideTopic> = listOf(
        NumismaticGuideTopic(
            id = "kleinstaaten",
            badge = "ECHTE RARITÄTEN",
            title = "Kleinstaaten: Monaco, Vatikan, San Marino & Andorra",
            summary = "Während Umlaufmünzen großer Euro-Staaten oft in zweistelliger Millionenhöhe geprägt werden, geben die vier europäischen Kleinstaaten strikt limitierte Kontingente heraus.",
            bulletPoints = listOf(
                "Monaco 2007 „Grace Kelly“ (Auflage: 20.001 Stück) gilt als berühmteste 2-Euro-Gedenkmünze und erzielt im unzirkulierten Originaletui vierstellige Sammlerpreise.",
                "Ausgaben aus dem Vatikan, San Marino und Andorra erscheinen überwiegend in offiziellen Blistern (Coincards) oder Etuis mit Auflagen zwischen ca. 50.000 und 100.000 Exemplaren.",
                "Normale 2-Euro-Gedenkmünzen aus Deutschland, Frankreich oder Italien (z. B. Bundesländer-Serie) haben meist 20 bis 30 Millionen Stück Auflage und sind im Umlaufzustand exakt 2,00 € wert."
            )
        ),
        NumismaticGuideTopic(
            id = "fake_preise",
            badge = "MARKT-AUFKLÄRUNG",
            title = "Warum 20.000-€-Angebote auf eBay fast immer Mondpreise sind",
            summary = "Auf Online-Plattformen kann jeder Verkäufer beliebige Wunschpreise eintragen. Entscheidend ist ausschließlich, zu welchen Preisen Münzen tatsächlich verkauft wurden.",
            bulletPoints = listOf(
                "Mythos „S im Stern“ (Griechenland 2002): Das „S“ im unteren Stern steht für „Suomi“ (Finnland), da Finnland 2002 beim Prägen der griechischen Erstausgabe aushalf (Auflage: 70 Millionen Stück – Wert im Umlauf: 2,00 €).",
                "Mythos „Strichmännchen“ (WWU 2009): Das bewusst archaisch gestaltete Gemeinschaftsmotiv zur Wirtschafts- und Währungsunion wurde über 80 Millionen Mal geprägt und ist keine Fehlprägung.",
                "Minimale Dezentrierungen der Pille oder leichte Randunregelmäßigkeiten liegen innerhalb der regulären Prägetoleranz und begründen keinen Sammleraufschlag."
            )
        ),
        NumismaticGuideTopic(
            id = "echte_fehlpraegungen",
            badge = "FEHLPRÄGUNGEN",
            title = "Worauf es bei echten, wertvollen Fehlprägungen ankommt",
            summary = "Echte Fehlprägungen entstehen unabsichtlich in der staatlichen Münzprägeanstalt und überstehen die optische Qualitätskontrolle nur extrem selten.",
            bulletPoints = listOf(
                "Spiegelei-Prägung: Das goldfarbene Innenteil (Pille) sitzt stark verschoben und fließt oval über den silberfarbenen Außenring.",
                "Monometall-Ronde: Die gesamte 2-Euro-Münze wurde versehentlich aus einem einzigen Metallstück (ohne Bimetall-Trennung) geprägt.",
                "Zainende / Sichel-Ausschnitt: Aus der Münzronde fehlt ein sauber gebogener Halbmond, weil das Metallband beim Ausstanzen am Ende überlappte.",
                "Starke Stempeldrehung (ab ca. 90°–180°): Wenn man die Münze wie eine Buchseite horizontal wendet, steht die Rückseite schief oder komplett auf dem Kopf."
            )
        ),
        NumismaticGuideTopic(
            id = "erhaltungsgrade",
            badge = "ZUSTAND & WERT",
            title = "Erhaltungsgrade: Warum Kratzer den Sammlerwert drastisch senken",
            summary = "In der Numismatik entscheidet die Oberflächenerhaltung maßgeblich über den Preis. Eine Münze aus dem Portemonnaie verliert gegenüber Bankfrisch-Ware oft 70–95 % ihres Aufschlags.",
            bulletPoints = listOf(
                "Polierte Platte / Spiegelglanz (PP/SP): Höchste Sammlerqualität mit spiegelndem Hintergrund und mattiertem Relief, einzeln gekapselt.",
                "Stempelglanz / Unzirkuliert (ST/UNC): Rollenfrische Münze ohne jeden Umlaufkontakt; feinste produktionsbedingte Schlagspuren sind möglich.",
                "Vorzüglich (vz) bis Sehr schön (ss): Münze war im Zahlungsverkehr; feine Kratzer, matter Glanz und kleine Randkerben senken den Sammlerwert meist zurück auf den Nennwert von 2,00 €.",
                "Niemals putzen oder polieren! Mechanisches Reiben oder Silberputzmittel zerstört die originale Prägehaut und macht Sammlermünzen für Numismatiker wertlos."
            )
        ),
        NumismaticGuideTopic(
            id = "praegestaetten_de",
            badge = "DEUTSCHLAND A–J",
            title = "Die fünf deutschen Prägestätten (A, D, F, G, J)",
            summary = "Jede deutsche 2-Euro-Münze trägt einen kleinen Großbuchstaben auf der Motivseite, der die jeweilige staatliche Münzprägeanstalt kennzeichnet.",
            bulletPoints = listOf(
                "A = Staatliche Münze Berlin (seit 1280)",
                "D = Bayerisches Hauptmünzamt München",
                "F = Staatliche Münzen Baden-Württemberg, Standort Stuttgart",
                "G = Staatliche Münzen Baden-Württemberg, Standort Karlsruhe",
                "J = Hamburgische Münze (Hansestadt Hamburg)"
            )
        )
    )
}
