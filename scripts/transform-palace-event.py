#!/usr/bin/env python3
"""Transform the original Jabba event using the actual palace paste, without changing terrain.

Input files are private JSON-formatted YAML exports. Run against the original world
configuration once, then validate every door and spawn in the destination world.
"""
import argparse
import copy
import json
from pathlib import Path

TURN = {"north": "west", "east": "north", "south": "east", "west": "south"}


def position(point):
    return [point[2] - 2035, point[1] + 20, 1835 - point[0]]


def vector(point):
    return [point[2], point[1], -point[0]]


def block_state(value):
    if "[" not in value:
        return value
    name, properties = value[:-1].split("[", 1)
    result = {}
    for pair in properties.split(","):
        key, item = pair.split("=", 1)
        if key == "facing":
            item = TURN.get(item, item)
        elif key == "axis" and item in ("x", "z"):
            item = "z" if item == "x" else "x"
        elif key == "rotation":
            item = str((int(item) - 4) % 16)
        result[TURN.get(key, key)] = item
    return name + "[" + ",".join(f"{key}={result[key]}" for key in sorted(result)) + "]"


def transform(event, doors, destination):
    if event["world"] != "minecraft:tatooine" or doors["world"] != event["world"]:
        raise ValueError("Expected matching original Tatooine files; refusing a second transform")
    event, doors = copy.deepcopy(event), copy.deepcopy(doors)
    event["world"] = doors["world"] = destination
    corners = [position(event["bounds"][key]) for key in ("min", "max")]
    event["bounds"] = {
        key: [operation(point[axis] for point in corners) for axis in range(3)]
        for key, operation in (("min", min), ("max", max))
    }
    for room in event["rooms"].values():
        room["movement"] = vector(room["movement"])
        room["spawns"] = [position(point) for point in room["spawns"]]
        room["chests"] = [position(point) + [TURN[point[3].lower()].upper()] for point in room["chests"]]
    for gate in doors["gates"].values():
        rows = []
        for row in gate["blocks"]:
            coordinates, state = row.split("|", 1)
            point = position(list(map(int, coordinates.split(","))))
            rows.append(",".join(map(str, point)) + "|" + block_state(state))
        gate["blocks"] = rows
        if "clear-imported" in gate:
            gate["clear-imported"] = [
                ",".join(map(str, position(list(map(int, row.split(","))))))
                for row in gate["clear-imported"]
            ]
    return event, doors


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("event", type=Path)
    parser.add_argument("doors", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--world", default="minecraft:tatooine_review")
    args = parser.parse_args()
    event, doors = transform(json.loads(args.event.read_text()), json.loads(args.doors.read_text()), args.world)
    # Surveyed exception: the exact transformed legacy guard point intersects a wall.
    # Move the spawn onto existing clear floor; never excavate the building to fit it.
    if event["rooms"]["2"]["spawns"][1] != [140, 107, 149]:
        raise ValueError("Room 2 source changed; resurvey its spawn before migrating")
    event["rooms"]["2"]["spawns"][1] = [143, 107, 148]
    args.output.mkdir(parents=True, exist_ok=True)
    (args.output / "jabba.yml").write_text(json.dumps(event, indent=2) + "\n")
    (args.output / "doors.yml").write_text(json.dumps(doors, indent=2) + "\n")
    print(f"Transformed {len(event['rooms'])} rooms, "
          f"{sum(len(room['chests']) for room in event['rooms'].values())} caches and "
          f"{sum(len(gate['blocks']) for gate in doors['gates'].values())} gate cells")


if __name__ == "__main__":
    main()
