# D · 2026-10-10 · Site gazetteer research (`site.json`)

- **Who:** D
- **Goal:** real campus names for `core/src/main/resources/site/site.json` (lead's decision: real buildings, not a fictional site), used by the suggestion engine's gazetteer snap (spec D16).
- **Status:** draft, **not verified on site**. `"verified": false` stays in the file until someone walks the campus and ticks the list below.

## Setup
- OpenStreetMap main API, bounding box `78.3430,17.4400,78.3540,17.4490` (`/api/0.6/map`), 116 named features, filtered by hand to on-campus ones. Overpass (`overpass-api.de`, `overpass.kumi.systems`) returned server errors, so the raw map call was used instead.
- Official infrastructure page: https://www.iiit.ac.in/about/infrastructure/ (hostels, messes, canteens, sports, health centre, amphitheatre).
- Web search for blocks missing from OSM and for room numbers.

## Result
41 places, 20 assets, 6 people. No duplicate IDs, no alias shared by two entries, every `building`/`place` reference resolves. The file passes the `tools/blindcheck.words` patterns (no college or city name inside it).

| Entry | Evidence | Confidence |
|---|---|---|
| Himalaya Block | OSM "Himalaya Academic Block" | high |
| Himalaya 105 (H-105, "BODH-105") | Event pages: CVIT ML summer school 2017 and 2019 (https://cvit.iiit.ac.in/mlsummerschool2017/program.html), CCNSB program (http://ccnsb.iiit.ac.in/~ctcns/Program.html), iHub training listing 2026 (https://ihub-data.ai/archives/courses/aiml-training-program-sep-2026/) | high |
| Vindhya A2, A4, A6, C2, C4, C6 | OSM nodes; "Vindhya A2" = director's office per a CVIT directions page | medium (other wings likely exist, e.g. A3; add on site) |
| Nilgiri Block | Foursquare "Nilgiri (IIIT Main Building)", Wikimedia Commons file "IIIT Hyderabad Nilgiri.jpg", a ResearchGate figure "Block II, Nilgiri Building" | medium |
| Kohli Research Block (KRB), KRB Auditorium | News of its opening (https://www.ap7am.com/en/37245/ktr-opens-kohli-research-block-at-iiit), ICFHR 2022 venue page (https://iapr.org/archives/icfhr2022/venue.php.html), HSRC talk notices naming "KRB Auditorium" | high |
| SH-1, SH-2 | OSM nodes only | medium (confirm they are "Seminar Hall 1/2") |
| Library, Academic Office | OSM | high |
| Bakul, Old/New Parijaat, Palash (OBH), Kadamba Nivas (NBH) | Official page + OSM ("Kadamba Nivas (New Boys Hostel)") | high |
| Kadamba, Yuktahar, Palash (North/South), Bakul messes | Official page + OSM | high |
| Vindhya, Juice, BSB, David's, CIE canteens | Official page (Vindhya canteen) + OSM | medium |
| Football, Felicity, Basketball, Open Air Gym, Amphitheatre, Main Gate (Gate 1) | OSM + official page | high |
| Aarogya Centre, Guest House | Official page; ICFHR page ("Guest House Lawns") | high |
| Ananda Nivas, Buddha Nivas | Official page ("Anand Nivas", "Budh Nivas") + OSM | high |
| Kadamb Stationery Shop | OSM | medium |

**Left out on purpose:** OSM "D Block" (a university building whose role is unclear, and it clashes with the faculty-housing "C & D Block"), research-group names (CVEST, CSG, Precog, LSI, SERC, HSRC, CQST, EERC: labs, not places someone reports from), and everything outside the campus wall (stadiums, DLF, banks, police stations).

**Assets and people are demo values.** No public source lists asset tags, and real staff names must not be compiled from the web. Asset IDs follow `KIND-PLACE-NN`; people are invented and carry only a role.

## Verdict and next steps
- Good enough to build and test the snapper against. Not good enough to claim "real site data" to judges until it is checked.
- **On-site check (anyone on campus, 15 minutes):** confirm the Vindhya wings and add the missing ones, SH-1/SH-2 names, which building houses the HPC data centre, a few real room numbers in Himalaya/Nilgiri/KRB, and whether "Tarangini" names the amphitheatre.
- Add real Whisper mishearings from E's recordings as aliases when they arrive.
