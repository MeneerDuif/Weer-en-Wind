# Weer en Wind

Privacyvriendelijke weer-app voor Android, gebouwd op basis van het externe design document "Weer-app bouwen".
Geen account, geen advertenties, geen analytics, geen Google-diensten en geen locatie-toestemming.

## Installeren

1. Ga naar **Releases** (rechts op de repo-pagina) en download `WeerEnWind.apk` van de nieuwste release.
2. Open het bestand op je telefoon en sta "installeren uit onbekende bron" toe als Android dat vraagt.
3. Elke push naar `main` bouwt automatisch een nieuwe release; die installeert over de vorige heen.

## Gekozen standaarden (vragenlijst niet ingevuld)

| Onderwerp | Keuze |
| --- | --- |
| Gebruik | Eén persoon, fiets, kijkt vóór vertrek; 24–48 uur vooruit |
| Plekken | Twee naast elkaar (A en B), maximaal 10 opgeslagen; zoeken op naam, wijk, buurt of postcode |
| Locatie | Nooit; geen GPS, geen wifi-herkenning (die vereist op Android locatie-toestemming) |
| Landen | Nederland (PDOK) + buitenland (Open-Meteo-geocoder) |
| Thema | **AMOLED-zwart standaard**: #000000, tekst #E8E8E8, randen #1C1C1C, kleur alleen voor betekenis |
| Extra thema's | Systeem, Grafiet, Nachtblauw, Nachtrood, Hoog contrast, Licht, Retro weerkaart |
| Eenheden | °C, wind in Beaufort (of km/u, m/s), neerslag in mm |
| Adviezen | Regenjas, warme jas, gladheid, harde wind, zonnebrand; drempels instelbaar |
| Regen | Per kwartier (3 uur), buienbalk 24 uur, "Hoe nat word ik?", beste vertrekmoment, droge vensters |
| Meldingen | Geen (versie 2) |
| Widget | 2×1: twee plekken, of nu → over 3 uur; ververst hooguit elke 2 uur |
| Verversen | Bij openen als data ouder is dan 30 minuten |
| Privacy | Alleen Open-Meteo en PDOK; coördinaten afgerond op 3 decimalen; privacy-logboek van 7 dagen |
| Gebruik | Alleen voor jezelf (Open-Meteo gratis = niet-commercieel) |

### Kleuren AMOLED-zwart

| Rol | Hex |
| --- | --- |
| Achtergrond / kaart | `#000000` |
| Rand | `#1C1C1C` |
| Tekst | `#E8E8E8` |
| Gedempt | `#8C8C8C` |
| Zwak | `#4A4A4A` |
| Regen | `#5AB0FF` |
| Warm | `#FFB454` |
| Koud | `#8FB4FF` |
| Wind | `#5FD8C0` |
| Waarschuwing | `#FF6B6B` |
| Zon | `#FFD166` |

## Bewust weggelaten (voor later)

Meldingen en officiële KNMI-waarschuwingen, wifi-herkenning, feedbackfunctie, website-versie, tijdelijke plekken, 14-daagse verwachting.

## Techniek

Kotlin + Jetpack Compose, minSdk 26. Eén netwerkpoort (`data/Net.kt`) die elk verzoek lokaal logt.
Alle plekken in één Open-Meteo-verzoek; opslag in de privémap van de app, `allowBackup=false`.
Builds worden ondertekend met `app/weer.keystore` (alleen voor eigen gebruik, niet voor een appwinkel).

Weerdata: [Open-Meteo](https://open-meteo.com/) (CC BY 4.0). Zoeken: [PDOK Locatieserver](https://www.pdok.nl/).
