"""Source of the gold set. Edit the items here, then run:  python3 build_gold.py
It writes app/src/main/assets/eval/gold_v1.jsonl (labelling rules: docs/eval-guide.md).

Campus place names are PLACEHOLDERS until D's site.json exists: every one is in SITE_NAMES so a
single swap keeps the gold, the recording sheets and the gazetteer in step.
OSHA items are first-person spoken rewrites of Simar123456/spectra-osha-sir (test split); the
source record id is kept in "source_ref".
"""

from __future__ import annotations

import json
from pathlib import Path

from score import FIELDS, MANDATORY

OUT = Path(__file__).resolve().parents[2] / "app/src/main/assets/eval/gold_v1.jsonl"

# Placeholder campus names (verify against site.json; D owns the real list).
SITE_NAMES = ["Kadamba Mess", "Himalaya building", "H-105", "Vindhya", "Kohli Research Block", "KRB", "Bakul Nivas",
              "Old Boys Hostel", "Yuktahar mess", "Felicity Ground", "Nilgiri", "North mess", "Palash Nivas",
              "Parijaat Nivas", "Amphitheatre", "admin block", "library", "makerspace", "health centre",
              "sports complex", "campus store"]


# ---- slot helpers --------------------------------------------------------------------------

def v(value, *alts):
    slot = {"status": "REPORTED", "value": value}
    if alts:
        slot["alts"] = list(alts)
    return slot


def names(*people):
    return {"status": "REPORTED", "value": list(people)}


def sug(value, *alts):
    slot = v(value, *alts)
    slot["status"] = "SUGGESTED"
    return slot


def none():
    return {"status": "EXPLICIT_NONE"}


def unk():
    return {"status": "UNKNOWN"}


def dt(text, time=None, day=None, pod=None, approx=False, minutes_ago=None, status="REPORTED"):
    """day: date offset vs capture (0 today, -1 yesterday, None = no day said: C keeps the date missing)."""
    slot = {"status": status, "date_offset_days": day, "time": time, "part_of_day": pod,
            "approximate": approx, "text": text}
    if minutes_ago is not None:
        slot["minutes_ago"] = minutes_ago
    return slot


ITEMS: list[dict] = []


def item(id_, source, turns, tags=(), missing=None, source_ref=None, **fields):
    gold = {f: fields.get(f, {"status": "MISSING"}) for f in FIELDS}
    if missing is None:
        missing = [f for f in MANDATORY if gold[f]["status"] not in ("REPORTED", "USER_CONFIRMED", "USER_EDITED")]
    row = {"id": id_, "split": None, "source": source, "language": "en", "turns": list(turns),
           "gold": gold, "missing_mandatory": missing, "tags": list(tags)}
    if source_ref:
        row["source_ref"] = source_ref
    ITEMS.append(row)


DECK = "There was a water leak near Conference Room 2 around 10 AM. Maintenance has already isolated the area."

# ---- facilities (hand-written) -------------------------------------------------------------

item("g01", "handwritten", [DECK], ["deck"],
     incident_type=v("water_leak_flood"), location=v("Conference Room 2"),
     date_time=dt("around 10 AM", "10:00", approx=True),
     immediate_actions=v("isolated the area", "maintenance has already isolated the area"))

item("g02", "handwritten",
     ["There's smoke coming out of the microwave in the Kadamba Mess kitchen, it started about fifteen minutes ago. "
      "I switched off the power at the mains. Nobody was hurt and it's minor, the smoke is clearing."],
     ["relative_time", "negation"],
     incident_type=v("fire_smoke"), severity=v("minor"), location=v("Kadamba Mess kitchen", "Kadamba Mess"),
     date_time=dt("about fifteen minutes ago", approx=True, minutes_ago=15), persons_involved=none(),
     equipment_asset=v("microwave"), immediate_actions=v("switched off the power at the mains"))

item("g03", "handwritten",
     ["Hi, the lift in Himalaya building is stuck between the first and second floor since around 3:30 this afternoon. "
      "Two students were inside, Arjun and Meera, they're out now. Security called the lift technician."],
     ["ongoing"],
     incident_type=v("equipment_failure"), location=v("Himalaya building", "Himalaya building lift"),
     date_time=dt("around 3:30 this afternoon", "15:30", day=0, approx=True),
     persons_involved=names("Arjun", "Meera"), equipment_asset=v("lift", "elevator"),
     immediate_actions=v("called the lift technician", "security called the lift technician"))

item("g04", "handwritten",
     ["The whole third floor of Vindhya has had no power since 9 PM yesterday, and the UPS in the server room keeps beeping. "
      "I've informed the electrical maintenance team."],
     ["ongoing"],
     incident_type=v("electrical"), location=v("third floor of Vindhya", "Vindhya third floor"),
     date_time=dt("9 PM yesterday", "21:00", day=-1), equipment_asset=v("UPS"),
     immediate_actions=v("informed the electrical maintenance team"))

item("g05", "handwritten",
     ["About ten minutes ago a bottle of acid spilled near the fume hood in the fourth floor lab of Kohli Research Block. "
      "We opened the windows and put the spill kit on it. Priya was handling it, she's fine. It's moderate, the lab is closed now."],
     ["relative_time", "trap_equipment"],
     incident_type=v("spill_chemical"), severity=v("moderate"),
     location=v("fourth floor lab of Kohli Research Block", "Kohli Research Block fourth floor lab", "KRB fourth floor lab"),
     date_time=dt("About ten minutes ago", approx=True, minutes_ago=10), persons_involved=names("Priya"),
     immediate_actions=v("opened the windows and put the spill kit on it"))

item("g06", "handwritten",
     ["Almost had an accident outside Bakul Nivas. A ceiling panel in the corridor fell down right next to a student "
      "around 7 this morning. No one got hurt. The area is cordoned off with chairs now."],
     ["negation"],
     incident_type=v("near_miss"), location=v("corridor outside Bakul Nivas", "Bakul Nivas corridor", "Bakul Nivas"),
     date_time=dt("around 7 this morning", "07:00", day=0, approx=True), persons_involved=none(),
     equipment_asset=v("ceiling panel"), immediate_actions=v("cordoned off with chairs", "area is cordoned off"))

item("g07", "handwritten",
     ["Water is dripping from the ceiling onto the computers in the library reading room on the first floor. "
      "It's still leaking. I covered the desktops with a plastic sheet."],
     ["ongoing"],
     incident_type=v("water_leak_flood"), location=v("library reading room on the first floor", "library reading room"),
     equipment_asset=v("computers", "desktops"), immediate_actions=v("covered the desktops with a plastic sheet"))

item("g08", "handwritten",
     ["My bicycle was stolen from the cycle stand outside the Old Boys Hostel last night. "
      "I've told the security guard at the main gate."],
     ["relative_time"],
     incident_type=v("security"), location=v("cycle stand outside the Old Boys Hostel", "Old Boys Hostel"),
     date_time=dt("last night", day=-1, pod="night", approx=True), equipment_asset=v("bicycle"),
     immediate_actions=v("told the security guard at the main gate"))

item("g09", "handwritten",
     ["There's a strong gas smell behind Yuktahar mess near the gas cylinders, since maybe half an hour. "
      "I think it's a leak. Nobody has gone near it yet."],
     ["hedged", "relative_time"],
     incident_type=v("spill_chemical", "environmental"), location=v("behind Yuktahar mess"),
     date_time=dt("since maybe half an hour", approx=True, minutes_ago=30, status="SUGGESTED"),
     equipment_asset=v("gas cylinders"))

item("g10", "handwritten",
     ["A tree branch fell on a parked car near the Felicity Ground gate during the storm at around 5 PM. "
      "The windshield is cracked. It's minor, no injuries. I've put a cone next to it and informed the estate office."],
     ["negation"],
     incident_type=v("property_damage"), severity=v("minor"),
     location=v("near the Felicity Ground gate", "Felicity Ground gate"),
     date_time=dt("at around 5 PM", "17:00", approx=True), persons_involved=none(),
     equipment_asset=v("parked car", "car"), immediate_actions=v("put a cone next to it and informed the estate office"))

# ---- broad, not injury-centred -------------------------------------------------------------

item("g11", "broad",
     ["The main network switch in the server room on the ground floor of Nilgiri is sparking and there's a burning smell. "
      "It happened at 2:15 PM. I've cut the power to the rack. It's severe, the whole building is offline."],
     ["ongoing"],
     incident_type=v("electrical", "fire_smoke"), severity=v("severe"),
     location=v("server room on the ground floor of Nilgiri", "Nilgiri server room"),
     date_time=dt("at 2:15 PM", "14:15"), equipment_asset=v("main network switch", "network switch"),
     immediate_actions=v("cut the power to the rack"))

item("g12", "broad",
     ["A visitor fainted at the reception desk in the admin block about five minutes ago. She's conscious now and sitting down. "
      "Ravi from the front office saw it happen, and we've called the campus doctor."],
     ["relative_time"],
     incident_type=v("illness"), location=v("reception desk in the admin block", "admin block reception"),
     date_time=dt("about five minutes ago", approx=True, minutes_ago=5), witnesses=names("Ravi"),
     immediate_actions=v("called the campus doctor"))

item("g13", "broad",
     ["A delivery van reversed out of the parking lot near the main gate without looking and nearly hit a security guard, "
      "around 6:45 this evening. The guard is okay. I noted down the van number."],
     [],
     incident_type=v("near_miss"), location=v("parking lot near the main gate"),
     date_time=dt("around 6:45 this evening", "18:45", day=0, approx=True), equipment_asset=v("delivery van", "van"),
     immediate_actions=v("noted down the van number"))

item("g14", "broad",
     ["Oil caught fire in a pan in the North mess kitchen at lunch, around 1 PM. The cook, Suresh, put it out with the fire blanket. "
      "Nobody was hurt, just a lot of smoke."],
     ["negation", "named_but_unhurt"],
     incident_type=v("fire_smoke"), location=v("North mess kitchen"),
     date_time=dt("around 1 PM", "13:00", approx=True), persons_involved=names("Suresh"),
     equipment_asset=v("pan"), immediate_actions=v("put it out with the fire blanket"))

item("g15", "broad",
     ["Raw sewage is overflowing from the manhole next to Palash Nivas since this morning and it's spreading onto the walkway. "
      "I've informed housekeeping but no one has come yet."],
     ["ongoing"],
     incident_type=v("environmental"), location=v("manhole next to Palash Nivas", "next to Palash Nivas", "Palash Nivas"),
     date_time=dt("since this morning", day=0, pod="morning", approx=True), immediate_actions=v("informed housekeeping"))

item("g16", "broad",
     ["Someone smashed the glass door of the vending machine on the ground floor of Himalaya. I saw it at 8:10 PM. "
      "The glass pieces are still on the floor. Meera and I saw two people running away."],
     [],
     incident_type=v("property_damage", "security"), location=v("ground floor of Himalaya", "Himalaya ground floor"),
     date_time=dt("at 8:10 PM", "20:10"), equipment_asset=v("vending machine"), witnesses=names("Meera"))

item("g17", "broad",
     ["The 3D printer in the makerspace overheated and the nozzle has melted. I noticed it when I came in at 9 this morning, "
      "it was left running overnight. I've unplugged it."],
     [],
     incident_type=v("equipment_failure"), location=v("makerspace"),
     date_time=dt("at 9 this morning", "09:00", day=0), equipment_asset=v("3D printer"),
     immediate_actions=v("unplugged it"))

item("g18", "broad",
     ["There's a crack in the water tank on the roof of the library building and water is seeping out. "
      "We will call the plumber tomorrow morning. It's minor, not urgent."],
     ["planned"],
     incident_type=v("water_leak_flood"), severity=v("minor"), location=v("roof of the library building"),
     equipment_asset=v("water tank"))

item("g19", "broad",
     ["I think the fire alarm went off in Parijaat Nivas, maybe the second floor, around 11:30 PM. Everyone came out. "
      "I'm not sure if there was actual smoke."],
     ["hedged"],
     incident_type=v("fire_smoke"), location=sug("second floor of Parijaat Nivas", "Parijaat Nivas"),
     date_time=dt("around 11:30 PM", "23:30", approx=True), equipment_asset=v("fire alarm"))

item("g20", "broad",
     ["A student slipped on the wet floor outside the washroom near the Amphitheatre and twisted her ankle, around 4 PM. "
      "Her name is Ananya. Rahul was there but he didn't see her fall. We gave her an ice pack."],
     ["negation"],
     incident_type=v("injury"), location=v("outside the washroom near the Amphitheatre", "washroom near the Amphitheatre"),
     date_time=dt("around 4 PM", "16:00", approx=True), persons_involved=names("Ananya"),
     immediate_actions=v("gave her an ice pack"))

# ---- OSHA severe-injury narratives, rewritten as spoken first-person reports ---------------

item("g21", "osha",
     ["I'm reporting a burn at the urea plant. One of our operators, Kiran, was clearing a blocked pipeline when hot water "
      "splashed on his neck and chest, around 10:20. We sent him to the hospital. It's severe."],
     [], source_ref="2023010001",
     incident_type=v("injury"), severity=v("severe"), location=v("urea plant"),
     date_time=dt("around 10:20", "10:20", approx=True), persons_involved=names("Kiran"),
     equipment_asset=v("pipeline", "blocked pipeline"), immediate_actions=v("sent him to the hospital"))

item("g22", "osha",
     ["A worker in the warehouse got hit by a forklift while he was helping another worker, at about 3 in the afternoon. "
      "His ankle looks broken. The forklift driver was Mohan. We've stopped all forklift movement in that aisle."],
     [], source_ref="2023010020",
     incident_type=v("injury"), location=v("warehouse"), date_time=dt("at about 3 in the afternoon", "15:00", approx=True),
     persons_involved=names("Mohan"), equipment_asset=v("forklift"),
     immediate_actions=v("stopped all forklift movement in that aisle"))

item("g23", "osha",
     ["While I was fixing the cover on the hot water heater in the hostel laundry room, a screw shot out and hit my eye. "
      "My eye is scratched and bleeding a little. It happened twenty minutes ago."],
     ["relative_time"], source_ref="2023010030",
     incident_type=v("injury"), location=v("hostel laundry room"),
     date_time=dt("twenty minutes ago", minutes_ago=20), equipment_asset=v("hot water heater"))

item("g24", "osha",
     ["Someone hit their head on the compressed air tank while checking a drip from the water line in the basement plant room. "
      "We think he has a concussion. This was yesterday at around 6 PM. Deepak saw it."],
     ["relative_time"], source_ref="2023010027",
     incident_type=v("injury"), location=v("basement plant room"),
     date_time=dt("yesterday at around 6 PM", "18:00", day=-1, approx=True),
     equipment_asset=v("compressed air tank"), witnesses=names("Deepak"))

item("g25", "osha",
     ["I tripped over a lamp cord at the donation drive-through outside the store, the lamp broke and cut my elbow badly. "
      "It's bleeding a lot, I'm applying pressure with a towel. Please send help."],
     ["ongoing"], source_ref="2023010012",
     incident_type=v("injury"), location=v("donation drive-through outside the store", "drive-through outside the store"),
     equipment_asset=v("lamp", "lamp cord"), immediate_actions=v("applying pressure with a towel"))

item("g26", "osha",
     ["A staff member fell near the sales counter of the campus store and hit her head on the counter. "
      "She has a cut above her eye. It happened at 11 this morning. We cleaned the wound with the first aid kit."],
     [], source_ref="2023010014",
     incident_type=v("injury"), location=v("sales counter of the campus store", "campus store"),
     date_time=dt("at 11 this morning", "11:00", day=0), immediate_actions=v("cleaned the wound with the first aid kit"))

item("g27", "osha",
     ["In the third floor workshop at 11:45, Imran was grinding welds on a tank when the grinder kicked back and cut his "
      "ring finger to the bone. We stopped the work and he's gone to the clinic. It's serious."],
     [], source_ref="2023010061",
     incident_type=v("injury"), severity=v("severe"), location=v("third floor workshop"),
     date_time=dt("at 11:45", "11:45"), persons_involved=names("Imran"), equipment_asset=v("grinder"),
     immediate_actions=v("stopped the work"))

item("g28", "osha",
     ["A patient in the health centre pushed one of our nurses, Lakshmi, and she fell and hit her head. She seems dizzy. "
      "This was at 8:30 tonight. The guard has calmed the patient down now."],
     [], source_ref="2023010065",
     incident_type=v("injury", "security"), location=v("health centre"),
     date_time=dt("at 8:30 tonight", "20:30", day=0), persons_involved=names("Lakshmi"),
     immediate_actions=v("calmed the patient down", "the guard has calmed the patient down"))

item("g29", "osha",
     ["Our delivery boy was attacked by a robber near the back gate around 9 PM yesterday and stabbed in the back. "
      "He's in the hospital now. We have filed a police complaint."],
     ["relative_time"], source_ref="2023010051",
     incident_type=v("security", "injury"), location=v("near the back gate", "back gate"),
     date_time=dt("around 9 PM yesterday", "21:00", day=-1, approx=True), immediate_actions=v("filed a police complaint"))

item("g30", "osha",
     ["Two technicians were drilling into the turbine floor when the drill got stuck and became live, and it hit both of them. "
      "Ramesh and Vijay are injured. It happened maybe ten minutes back. We switched off the main breaker."],
     ["hedged", "relative_time"], source_ref="2023010038",
     incident_type=v("electrical", "injury", "equipment_failure"), location=v("turbine floor"),
     date_time=dt("maybe ten minutes back", approx=True, minutes_ago=10, status="SUGGESTED"),
     persons_involved=names("Ramesh", "Vijay"), equipment_asset=v("drill"),
     immediate_actions=v("switched off the main breaker"))

item("g31", "osha",
     ["A stack of heavy bulk bags fell on a worker in aisle 6 of the store room. He has a broken rib and a dislocated shoulder. "
      "I'm calling the ambulance now. It's critical."],
     ["ongoing"], source_ref="2023010084",
     incident_type=v("injury"), severity=v("critical"), location=v("aisle 6 of the store room", "store room"),
     equipment_asset=v("bulk bags"), immediate_actions=v("calling the ambulance"))

item("g32", "osha",
     ["While we were removing an old electric pole near the sports complex, the power lines touched Naveen's hands. "
      "He got a shock and burns on both hands. This was at 4:40 PM. The power company has cut the supply."],
     [], source_ref="2023010083",
     incident_type=v("electrical", "injury"), location=v("near the sports complex", "sports complex"),
     date_time=dt("at 4:40 PM", "16:40"), persons_involved=names("Naveen"),
     equipment_asset=v("power lines", "electric pole"), immediate_actions=v("cut the supply", "the power company has cut the supply"))

# ---- multi-turn: follow-up answers, corrections, over-answers ------------------------------

item("g33", "multiturn", [DECK, "Minor, nobody was hurt."], ["deck", "negation"], missing=["severity"],
     incident_type=v("water_leak_flood"), severity=v("minor"), location=v("Conference Room 2"),
     date_time=dt("around 10 AM", "10:00", approx=True), persons_involved=none(),
     immediate_actions=v("isolated the area", "maintenance has already isolated the area"))

item("g34", "multiturn", [DECK, "Actually it was Conference Room 3."], ["deck", "correction"], missing=["severity"],
     incident_type=v("water_leak_flood"), location=v("Conference Room 3"),
     date_time=dt("around 10 AM", "10:00", approx=True),
     immediate_actions=v("isolated the area", "maintenance has already isolated the area"))

item("g35", "multiturn",
     ["The projector in H-105 caught fire at ten, no wait, half past ten this morning. I used the extinguisher.",
      "It's moderate, nobody was hurt."],
     ["correction", "negation"], missing=["severity"],
     incident_type=v("fire_smoke"), severity=v("moderate"), location=v("H-105", "H 105"),
     date_time=dt("half past ten this morning", "10:30", day=0), persons_involved=none(),
     equipment_asset=v("projector"), immediate_actions=v("used the extinguisher"))

item("g36", "multiturn",
     ["There's a big crack in the wall of the Vindhya stairwell and it looks like it's getting wider.",
      "I don't know when it started.", "It's moderate."],
     ["ongoing", "unknown_answer"], missing=["date_time", "severity"],
     incident_type=v("property_damage"), severity=v("moderate"), location=v("Vindhya stairwell"), date_time=unk())

item("g37", "multiturn",
     ["Someone fainted in the library.", "Around 2 PM, it's minor, she's okay now, and Kavya saw it happen."],
     ["over_answer"], missing=["date_time", "severity"],
     incident_type=v("illness"), severity=v("minor"), location=v("library"),
     date_time=dt("Around 2 PM", "14:00", approx=True), witnesses=names("Kavya"))

item("g38", "multiturn",
     ["A water pipe burst and the corridor is flooding, it started five minutes ago!",
      "It's on the ground floor of Bakul Nivas, near room 104.", "Severe, water is getting into the rooms."],
     ["ongoing", "relative_time"], missing=["location", "severity"],
     incident_type=v("water_leak_flood"), severity=v("severe"),
     location=v("ground floor of Bakul Nivas, near room 104", "Bakul Nivas ground floor near room 104"),
     date_time=dt("five minutes ago", minutes_ago=5), equipment_asset=v("water pipe"))

item("g39", "multiturn",
     ["My laptop charger exploded with a bang in the KRB second floor lab at around 3 PM, there were sparks.",
      "No, nobody was hurt. It's minor. Arjun saw it too."],
     ["negation"], missing=["severity"],
     incident_type=v("electrical", "fire_smoke"), severity=v("minor"),
     location=v("KRB second floor lab", "Kohli Research Block second floor lab"),
     date_time=dt("at around 3 PM", "15:00", approx=True), persons_involved=none(),
     equipment_asset=v("laptop charger"), witnesses=names("Arjun"))

item("g40", "multiturn",
     ["Something happened near the Nilgiri parking, there's glass everywhere.",
      "Two bikes collided at about 7:15 this evening, and one rider, Sameer, has a cut on his arm.", "Moderate."],
     ["vague_start"], missing=["incident_type", "date_time", "severity"],
     incident_type=v("injury", "property_damage"), severity=v("moderate"), location=v("Nilgiri parking"),
     date_time=dt("at about 7:15 this evening", "19:15", day=0, approx=True), persons_involved=names("Sameer"),
     equipment_asset=v("bikes", "two bikes"))


def assign_splits(items: list[dict]) -> None:
    """Every third item (g03, g06, ...) is holdout; g01 always dev."""
    for n, it in enumerate(items, start=1):
        it["split"] = "holdout" if n % 3 == 0 else "dev"


if __name__ == "__main__":
    assign_splits(ITEMS)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text("".join(json.dumps(i, ensure_ascii=False) + "\n" for i in ITEMS), encoding="utf-8")
    print(f"wrote {len(ITEMS)} items to {OUT}")
