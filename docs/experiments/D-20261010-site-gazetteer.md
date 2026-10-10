# D · 2026-10-10 · Site gazetteer research (`site.json`)

- **Who:** D
- **Goal:** real campus names for the site gazetteer (lead's decision: real buildings, not a fictional site), used by the suggestion engine's gazetteer snap (spec D16).
- **Status:** place list **confirmed by the team on 2026-10-10** (`"verified": true`). Himalaya rooms 101-105, 201-205 and 301-305 added from that check. Assets and people remain demo values.

## Setup
- OpenStreetMap main API, bounding box `78.3430,17.4400,78.3540,17.4490` (`/api/0.6/map`), 116 named features, filtered by hand to on-campus ones. Overpass (`overpass-api.de`, `overpass.kumi.systems`) returned server errors, so the raw map call was used instead.
- Official infrastructure page: https://www.iiit.ac.in/about/infrastructure/ (hostels, messes, canteens, sports, health centre, amphitheatre).
- Web search for blocks missing from OSM and for room numbers.

## Result
56 places (41 from the sources below, SH-3 and 14 Himalaya rooms from the team's check), 20 assets, 6 people. No duplicate IDs, no alias shared by two entries, every `building`/`place` reference resolves. The file passes the `tools/blindcheck.words` patterns (no college or city name inside it).

| Entry | Evidence | Confidence |
|---|---|---|
| Himalaya Block | OSM "Himalaya Academic Block" | high |
| Himalaya 105 (H-105, "BODH-105") | Event pages: CVIT ML summer school 2017 and 2019 (https://cvit.iiit.ac.in/mlsummerschool2017/program.html), CCNSB program (http://ccnsb.iiit.ac.in/~ctcns/Program.html), iHub training listing 2026 (https://ihub-data.ai/archives/courses/aiml-training-program-sep-2026/) | high |
| Vindhya A2, A4, A6, C2, C4, C6 | OSM nodes; "Vindhya A2" = director's office per a CVIT directions page | medium (other wings likely exist, e.g. A3; add on site) |
| Nilgiri Block | Foursquare "Nilgiri (IIIT Main Building)", Wikimedia Commons file "IIIT Hyderabad Nilgiri.jpg", a ResearchGate figure "Block II, Nilgiri Building" | medium |
| Kohli Research Block (KRB), KRB Auditorium | News of its opening (https://www.ap7am.com/en/37245/ktr-opens-kohli-research-block-at-iiit), ICFHR 2022 venue page (https://iapr.org/archives/icfhr2022/venue.php.html), HSRC talk notices naming "KRB Auditorium" | high |
| SH-1, SH-2, SH-3 | OSM nodes (SH-1, SH-2); team confirmed all three | high |
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
- Place names are confirmed by the team; they can be presented as real site data. Asset IDs and people must still be described as demo values.
- Optional later additions: room numbers in Nilgiri, KRB and Vindhya, and which building houses the HPC data centre.
- Add real Whisper mishearings from E's recordings as aliases when they arrive.

## Update 2026-10-11 00:45: real list moved out of the submission
The lead ruled that the submission must not reveal the college (lead-to-D, section 3). The real list moved to `dev/site/campus.json` (not a mirrored folder, so it never reaches the submission repo). The default `core/src/main/resources/site/site.json` is now a **fictional** site (North/South/Lab/Research blocks, Conference Rooms 1-3, Room 2 and Room 20 for the "must not snap" test, fictional hostels), and every test and fixture uses fictional names. `DevCampusSiteTest` still checks that the real file loads whenever it is present. A scan of `core/ app/ gradle/ tools/` with the current blindcheck words plus every campus name above found no hits.

## Update 2026-10-11 01:00: real list is no longer committed
User decision (lead-to-D, 00:35): real names are for the live demo only and the real list is never committed to `master`. `dev/site/campus.json` was removed from git (`git rm --cached`) and kept on D's laptop only, hidden by `.git/info/exclude`. It is still in this branch's history (commits `c34020d`..`80bd605`), but not in the tree that gets merged. For the demo, the app loads `filesDir/site.json` (pushed to the kit over USB) through `SiteDirectory.loadPreferring(file)`, which falls back to the bundled fictional site if the file is missing or broken.

## Update 2026-10-11 01:20: fictional site is a one-to-one rename (for E's gold)
Requested by A (comm A/004) and E (comm E/003): the fictional `site.json` is now a one-to-one rename of the real list (56 places, 20 assets; for example the real lecture block becomes North Block with rooms North 101-305, the real wing block becomes Lab Block with Lab A2-C6) plus five fictional-only places (Conference Rooms 1-3, Room 2, Room 20). A dev-only map, `dev/site/real-to-fictional.json` (git-ignored, never committed; sent to E off-repo), lists every entry and 118 free-text phrases. Check: applying the phrases to E's gold (dev and holdout) maps **58/58** values that name a real site entry to the matching fictional entry, and leaves no real campus word in the files. Both files are generated from one rename table (a script outside the repo), so they cannot disagree. `:core:test` 673/673 with no test changes; `blindcheck.words` scan of `site.json`: 0 hits.
