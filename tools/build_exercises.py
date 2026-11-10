#!/usr/bin/env python3
"""Builds the bundled exercise catalogue from free-exercise-db (public domain).

Usage: build_exercises.py <free-exercise-db checkout> <app/src/main/assets>

Keeps strength exercises only, normalises equipment, marks the common ones and shrinks both photos of each exercise to small WebP files.
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
# Exercises most people actually do; they are listed first in the picker.
COMMON = {
    # chest
    "Barbell Bench Press - Medium Grip",
    "Barbell Incline Bench Press - Medium Grip",
    "Decline Barbell Bench Press",
    "Wide-Grip Barbell Bench Press",
    "Dumbbell Bench Press",
    "Incline Dumbbell Press",
    "Decline Dumbbell Bench Press",
    "Dumbbell Flyes",
    "Incline Dumbbell Flyes",
    "Machine Bench Press",
    "Leverage Chest Press",
    "Leverage Incline Chest Press",
    "Smith Machine Bench Press",
    "Smith Machine Incline Bench Press",
    "Butterfly",
    "Cable Crossover",
    "Low Cable Crossover",
    "Cable Chest Press",
    "Pushups",
    "Incline Push-Up",
    "Bent-Arm Dumbbell Pullover",
    # back
    "Pullups",
    "Chin-Up",
    "Wide-Grip Lat Pulldown",
    "Close-Grip Front Lat Pulldown",
    "V-Bar Pulldown",
    "Underhand Cable Pulldowns",
    "Straight-Arm Pulldown",
    "Seated Cable Rows",
    "Bent Over Barbell Row",
    "Reverse Grip Bent-Over Rows",
    "One-Arm Dumbbell Row",
    "Bent Over Two-Dumbbell Row",
    "Dumbbell Incline Row",
    "T-Bar Row with Handle",
    "Lying T-Bar Row",
    "Leverage Iso Row",
    "Leverage High Row",
    "Barbell Deadlift",
    "Rack Pulls",
    "Hyperextensions With No Hyperextension Bench",
    "Barbell Shrug",
    "Dumbbell Shrug",
    # shoulders
    "Standing Military Press",
    "Seated Barbell Military Press",
    "Barbell Shoulder Press",
    "Dumbbell Shoulder Press",
    "Seated Dumbbell Press",
    "Arnold Dumbbell Press",
    "Machine Shoulder (Military) Press",
    "Leverage Shoulder Press",
    "Smith Machine Overhead Shoulder Press",
    "Side Lateral Raise",
    "Seated Side Lateral Raise",
    "Cable Seated Lateral Raise",
    "Standing Low-Pulley Deltoid Raise",
    "Front Dumbbell Raise",
    "Front Cable Raise",
    "Reverse Flyes",
    "Reverse Machine Flyes",
    "Cable Rear Delt Fly",
    "Face Pull",
    "Upright Barbell Row",
    "Standing Dumbbell Upright Row",
    # biceps
    "Barbell Curl",
    "EZ-Bar Curl",
    "Dumbbell Bicep Curl",
    "Dumbbell Alternate Bicep Curl",
    "Hammer Curls",
    "Incline Dumbbell Curl",
    "Concentration Curls",
    "Preacher Curl",
    "Machine Preacher Curls",
    "Machine Bicep Curl",
    "Standing Biceps Cable Curl",
    "Cable Hammer Curls - Rope Attachment",
    # triceps
    "Triceps Pushdown",
    "Triceps Pushdown - Rope Attachment",
    "Triceps Pushdown - V-Bar Attachment",
    "Reverse Grip Triceps Pushdown",
    "Cable Rope Overhead Triceps Extension",
    "EZ-Bar Skullcrusher",
    "Lying Dumbbell Tricep Extension",
    "Standing Dumbbell Triceps Extension",
    "Tricep Dumbbell Kickback",
    "Close-Grip Barbell Bench Press",
    "Dips - Triceps Version",
    "Bench Dips",
    "Dip Machine",
    "Machine Triceps Extension",
    # legs
    "Barbell Squat",
    "Barbell Full Squat",
    "Front Barbell Squat",
    "Smith Machine Squat",
    "Hack Squat",
    "Leg Press",
    "Leg Extensions",
    "Dumbbell Lunges",
    "Barbell Lunge",
    "Barbell Walking Lunge",
    "Split Squat with Dumbbells",
    "Dumbbell Squat",
    "Bodyweight Squat",
    "Romanian Deadlift",
    "Stiff-Legged Barbell Deadlift",
    "Stiff-Legged Dumbbell Deadlift",
    "Sumo Deadlift",
    "Lying Leg Curls",
    "Seated Leg Curl",
    "Barbell Hip Thrust",
    "Barbell Glute Bridge",
    "One-Legged Cable Kickback",
    "Pull Through",
    "Thigh Abductor",
    "Thigh Adductor",
    "Standing Calf Raises",
    "Seated Calf Raise",
    "Calf Press On The Leg Press Machine",
    "Good Morning",
    # abs
    "Crunches",
    "Sit-Up",
    "Plank",
    "Hanging Leg Raise",
    "Cable Crunch",
    "Ab Crunch Machine",
    "Russian Twist",
    "Reverse Crunch",
    "Air Bike",
    "Decline Crunch",
    # forearms
    "Palms-Up Barbell Wrist Curl Over A Bench",
    "Seated Dumbbell Palms-Up Wrist Curl",
}


def main(src, out):
    data = json.load(open(os.path.join(src, "dist", "exercises.json"), encoding="utf-8"))
    names = {x["name"] for x in data}
    missing = sorted(COMMON - names)
    if missing:
        sys.exit("Common names without a matching exercise: %s" % missing)
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
            "common": x["name"] in COMMON,
            "equip": equip,
            "primary": x["primaryMuscles"],
            "secondary": x["secondaryMuscles"],
            "compound": x.get("mechanic") == "compound",
            "images": images,
            "steps": x["instructions"],
        })
    result.sort(key=lambda e: (not e["common"], e["en"]))
    with open(os.path.join(out, "exercises.json"), "w", encoding="utf-8") as f:
        json.dump(result, f, ensure_ascii=False, separators=(",", ":"))
    print(len(result), "exercises,", sum(1 for e in result if e["common"]), "marked common")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
