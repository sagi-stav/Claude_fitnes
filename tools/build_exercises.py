#!/usr/bin/env python3
"""Builds the bundled exercise catalogue from free-exercise-db (public domain).

Usage: build_exercises.py <free-exercise-db checkout> <app/src/main/assets>

Keeps strength exercises only, normalises equipment, adds Hebrew names for the common
ones and shrinks both photos of each exercise to small WebP files.
"""
import json
import os
import sys

from PIL import Image

KEEP = {"strength", "powerlifting", "olympic weightlifting"}
EQUIP = {
    "barbell": "BARBELL", "dumbbell": "DUMBBELL", "machine": "MACHINE", "cable": "CABLE",
    "body only": "BODY", "kettlebells": "KETTLEBELL", "bands": "BAND", "e-z curl bar": "EZ_BAR",
}
HE = {
    # chest
    "Barbell Bench Press - Medium Grip": "לחיצת חזה במוט",
    "Barbell Incline Bench Press - Medium Grip": "לחיצת חזה בשיפוע עליון במוט",
    "Decline Barbell Bench Press": "לחיצת חזה בשיפוע שלילי במוט",
    "Wide-Grip Barbell Bench Press": "לחיצת חזה במוט באחיזה רחבה",
    "Dumbbell Bench Press": "לחיצת חזה במשקולות",
    "Incline Dumbbell Press": "לחיצת חזה בשיפוע עליון במשקולות",
    "Decline Dumbbell Bench Press": "לחיצת חזה בשיפוע שלילי במשקולות",
    "Dumbbell Flyes": "פרפר במשקולות",
    "Incline Dumbbell Flyes": "פרפר בשיפוע עליון במשקולות",
    "Machine Bench Press": "לחיצת חזה במכונה",
    "Leverage Chest Press": "לחיצת חזה במכונת מנופים",
    "Leverage Incline Chest Press": "לחיצת חזה בשיפוע עליון במכונת מנופים",
    "Smith Machine Bench Press": "לחיצת חזה בסמית׳",
    "Smith Machine Incline Bench Press": "לחיצת חזה בשיפוע עליון בסמית׳",
    "Butterfly": "פרפר במכונה (פק דק)",
    "Cable Crossover": "קרוס אובר בכבלים",
    "Low Cable Crossover": "קרוס אובר מפולי תחתון",
    "Cable Chest Press": "לחיצת חזה בכבלים",
    "Pushups": "שכיבות סמיכה",
    "Incline Push-Up": "שכיבות סמיכה בשיפוע",
    "Bent-Arm Dumbbell Pullover": "פולאובר במשקולת",
    # back
    "Pullups": "מתח",
    "Chin-Up": "מתח באחיזה הפוכה",
    "Wide-Grip Lat Pulldown": "פולי עליון באחיזה רחבה",
    "Close-Grip Front Lat Pulldown": "פולי עליון באחיזה צרה",
    "V-Bar Pulldown": "פולי עליון עם ידית V",
    "Underhand Cable Pulldowns": "פולי עליון באחיזה הפוכה",
    "Straight-Arm Pulldown": "פולאובר בכבל בידיים ישרות",
    "Seated Cable Rows": "חתירה בכבל בישיבה",
    "Bent Over Barbell Row": "חתירה במוט בהטיה",
    "Reverse Grip Bent-Over Rows": "חתירה במוט באחיזה הפוכה",
    "One-Arm Dumbbell Row": "חתירה במשקולת ביד אחת",
    "Bent Over Two-Dumbbell Row": "חתירה בשתי משקולות בהטיה",
    "Dumbbell Incline Row": "חתירה במשקולות על ספסל משופע",
    "T-Bar Row with Handle": "חתירת טי־בר",
    "Lying T-Bar Row": "חתירת טי־בר בשכיבה",
    "Leverage Iso Row": "חתירה במכונת מנופים",
    "Leverage High Row": "חתירה גבוהה במכונת מנופים",
    "Barbell Deadlift": "דדליפט",
    "Rack Pulls": "דדליפט חלקי מהמדף",
    "Hyperextensions With No Hyperextension Bench": "פשיטת גו",
    "Barbell Shrug": "משיכת כתפיים במוט",
    "Dumbbell Shrug": "משיכת כתפיים במשקולות",
    # shoulders
    "Standing Military Press": "לחיצת כתפיים במוט בעמידה",
    "Seated Barbell Military Press": "לחיצת כתפיים במוט בישיבה",
    "Barbell Shoulder Press": "לחיצת כתפיים במוט",
    "Dumbbell Shoulder Press": "לחיצת כתפיים במשקולות",
    "Seated Dumbbell Press": "לחיצת כתפיים במשקולות בישיבה",
    "Arnold Dumbbell Press": "לחיצת ארנולד",
    "Machine Shoulder (Military) Press": "לחיצת כתפיים במכונה",
    "Leverage Shoulder Press": "לחיצת כתפיים במכונת מנופים",
    "Smith Machine Overhead Shoulder Press": "לחיצת כתפיים בסמית׳",
    "Side Lateral Raise": "הרחקת כתפיים לצדדים במשקולות",
    "Seated Side Lateral Raise": "הרחקת כתפיים לצדדים בישיבה",
    "Cable Seated Lateral Raise": "הרחקת כתפיים בכבל",
    "Standing Low-Pulley Deltoid Raise": "הרחקת כתף בפולי תחתון",
    "Front Dumbbell Raise": "הרמה לפנים במשקולות",
    "Front Cable Raise": "הרמה לפנים בכבל",
    "Reverse Flyes": "פרפר הפוך במשקולות",
    "Reverse Machine Flyes": "פרפר הפוך במכונה",
    "Cable Rear Delt Fly": "פרפר הפוך בכבלים",
    "Face Pull": "פייס פול",
    "Upright Barbell Row": "חתירה זקופה במוט",
    "Standing Dumbbell Upright Row": "חתירה זקופה במשקולות",
    # biceps
    "Barbell Curl": "כפיפת מרפקים במוט",
    "EZ-Bar Curl": "כפיפת מרפקים במוט EZ",
    "Dumbbell Bicep Curl": "כפיפת מרפקים במשקולות",
    "Dumbbell Alternate Bicep Curl": "כפיפת מרפקים לסירוגין במשקולות",
    "Hammer Curls": "כפיפת פטישים",
    "Incline Dumbbell Curl": "כפיפת מרפקים על ספסל משופע",
    "Concentration Curls": "כפיפת ריכוז",
    "Preacher Curl": "כפיפת מרפקים על כיסא כומר",
    "Machine Preacher Curls": "כפיפת מרפקים במכונת כומר",
    "Machine Bicep Curl": "כפיפת מרפקים במכונה",
    "Standing Biceps Cable Curl": "כפיפת מרפקים בכבל",
    "Cable Hammer Curls - Rope Attachment": "כפיפת פטישים בכבל עם חבל",
    # triceps
    "Triceps Pushdown": "פשיטת מרפקים בפולי עליון",
    "Triceps Pushdown - Rope Attachment": "פשיטת מרפקים בפולי עם חבל",
    "Triceps Pushdown - V-Bar Attachment": "פשיטת מרפקים בפולי עם ידית V",
    "Reverse Grip Triceps Pushdown": "פשיטת מרפקים בפולי באחיזה הפוכה",
    "Cable Rope Overhead Triceps Extension": "פשיטת מרפקים מעל הראש בכבל",
    "EZ-Bar Skullcrusher": "פשיטת מרפקים בשכיבה במוט EZ",
    "Lying Dumbbell Tricep Extension": "פשיטת מרפקים בשכיבה במשקולות",
    "Standing Dumbbell Triceps Extension": "פשיטת מרפקים מעל הראש במשקולת",
    "Tricep Dumbbell Kickback": "קיק־בק במשקולת",
    "Close-Grip Barbell Bench Press": "לחיצת חזה באחיזה צרה",
    "Dips - Triceps Version": "מקבילים",
    "Bench Dips": "מקבילים על ספסל",
    "Dip Machine": "מקבילים במכונה",
    "Machine Triceps Extension": "פשיטת מרפקים במכונה",
    # legs
    "Barbell Squat": "סקוואט במוט",
    "Barbell Full Squat": "סקוואט עמוק במוט",
    "Front Barbell Squat": "סקוואט קדמי",
    "Smith Machine Squat": "סקוואט בסמית׳",
    "Hack Squat": "האק סקוואט במכונה",
    "Leg Press": "לחיצת רגליים",
    "Leg Extensions": "פשיטת ברכיים במכונה",
    "Dumbbell Lunges": "מכרעים במשקולות",
    "Barbell Lunge": "מכרעים במוט",
    "Barbell Walking Lunge": "מכרעים בהליכה במוט",
    "Split Squat with Dumbbells": "סקוואט בולגרי במשקולות",
    "Dumbbell Squat": "סקוואט במשקולות",
    "Bodyweight Squat": "סקוואט במשקל גוף",
    "Romanian Deadlift": "דדליפט רומני",
    "Stiff-Legged Barbell Deadlift": "דדליפט ברגליים ישרות",
    "Stiff-Legged Dumbbell Deadlift": "דדליפט ברגליים ישרות במשקולות",
    "Sumo Deadlift": "דדליפט סומו",
    "Lying Leg Curls": "כפיפת ברכיים בשכיבה",
    "Seated Leg Curl": "כפיפת ברכיים בישיבה",
    "Barbell Hip Thrust": "היפ ת׳ראסט במוט",
    "Barbell Glute Bridge": "גשר ישבן במוט",
    "One-Legged Cable Kickback": "בעיטה לאחור בכבל",
    "Pull Through": "פול ת׳רו בכבל",
    "Thigh Abductor": "הרחקת ירכיים במכונה",
    "Thigh Adductor": "קירוב ירכיים במכונה",
    "Standing Calf Raises": "הרמת עקבים בעמידה במכונה",
    "Seated Calf Raise": "הרמת עקבים בישיבה",
    "Calf Press On The Leg Press Machine": "הרמת עקבים במכונת לחיצת רגליים",
    "Good Morning": "גוד מורנינג",
    # abs
    "Crunches": "כפיפות בטן",
    "Sit-Up": "כפיפות בטן מלאות",
    "Plank": "פלאנק",
    "Hanging Leg Raise": "הרמת רגליים בתלייה",
    "Cable Crunch": "כפיפות בטן בכבל",
    "Ab Crunch Machine": "כפיפות בטן במכונה",
    "Russian Twist": "סיבובים רוסיים",
    "Reverse Crunch": "כפיפות בטן הפוכות",
    "Air Bike": "אופניים באוויר",
    "Decline Crunch": "כפיפות בטן בשיפוע",
    # forearms
    "Palms-Up Barbell Wrist Curl Over A Bench": "כפיפת שורש כף היד במוט",
    "Seated Dumbbell Palms-Up Wrist Curl": "כפיפת שורש כף היד במשקולות",
}


def main(src, out):
    data = json.load(open(os.path.join(src, "dist", "exercises.json"), encoding="utf-8"))
    names = {x["name"] for x in data}
    missing = sorted(set(HE) - names)
    if missing:
        sys.exit("Hebrew names without a matching exercise: %s" % missing)
    img_dir = os.path.join(out, "ex")
    os.makedirs(img_dir, exist_ok=True)
    result = []
    for x in data:
        if x["category"] not in KEEP or not x["primaryMuscles"]:
            continue
        equip = EQUIP.get(x["equipment"] or "", "OTHER")
        low = x["name"].lower()
        if "smith" in low:
            equip = "SMITH"
        elif "ez" in low.replace("-", " ").split() or "ez-bar" in low:
            equip = "EZ_BAR"
        images = []
        for i, rel in enumerate(x["images"][:2]):
            name = "%s_%d.webp" % (x["id"], i)
            dst = os.path.join(img_dir, name)
            if not os.path.exists(dst):
                im = Image.open(os.path.join(src, "exercises", rel)).convert("RGB")
                im.thumbnail((480, 480))
                im.save(dst, "WEBP", quality=55, method=6)
            images.append(name)
        result.append({
            "id": x["id"],
            "en": x["name"],
            "he": HE.get(x["name"]),
            "equip": equip,
            "primary": x["primaryMuscles"],
            "secondary": x["secondaryMuscles"],
            "compound": x.get("mechanic") == "compound",
            "images": images,
            "steps": x["instructions"],
        })
    result.sort(key=lambda e: (e["he"] is None, e["he"] or e["en"]))
    with open(os.path.join(out, "exercises.json"), "w", encoding="utf-8") as f:
        json.dump(result, f, ensure_ascii=False, separators=(",", ":"))
    print(len(result), "exercises,", sum(1 for e in result if e["he"]), "with Hebrew names")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
