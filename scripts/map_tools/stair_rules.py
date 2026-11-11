"""Collision-aware rules for bottom road stairs; world reads/writes are supplied by the caller.

Facing points uphill. A same-Y full cube at the low face obstructs the half step.
Full cubes at the high face are valid landings. Turning/multi-entry tiles use a
bottom slab instead of an incorrectly oriented stair. Asset stairs are excluded.
"""
DIRECTIONS = {'east': (1, 0), 'west': (-1, 0), 'south': (0, 1), 'north': (0, -1)}
EMPTY = {'air', 'cave_air', 'void_air', 'short_dry_grass', 'dead_bush'}
ROAD = {'mud_bricks', 'packed_mud', 'terracotta', 'mud_brick_stairs', 'mud_brick_slab'}


def base(state):
    return state.split('[')[0]


def properties(state):
    return dict(p.split('=') for p in state.partition('[')[2].rstrip(']').split(',') if '=' in p)


def full_cube(state):
    return base(state) not in EMPTY and not any(t in base(state) for t in ('stairs', 'slab', 'fence', 'wall', 'door', 'lantern', 'torch', 'carpet', 'chain', 'barrel'))


def low_face_blocked(get, position, state):
    props = properties(state)
    if base(state) != 'mud_brick_stairs' or props.get('half') != 'bottom':
        return False
    x, y, z = position
    dx, dz = DIRECTIONS[props['facing']]
    return full_cube(get(x-dx, y, z-dz))


def repair_low_faces(get, put, stairs):
    changes = []
    for position in sorted(stairs):
        state = get(*position)
        if not low_face_blocked(get, position, state):
            continue
        x, y, z = position
        approaches = []
        for facing, (dx, dz) in DIRECTIONS.items():
            if base(get(x-dx, y, z-dz)) in EMPTY and base(get(x-dx, y-1, z-dz)) in ROAD:
                approaches.append(facing)
        # Slabs accept half-step approaches from several directions without a false riser.
        target = ('mud_brick_stairs[facing='+approaches[0]+',half=bottom,shape=straight,waterlogged=false]'
                  if len(approaches) == 1 else 'mud_brick_slab[type=bottom,waterlogged=false]')
        put(x, y, z, target)
        changes.append((position, state, target))
    return changes


def quadrant_height(state, y, qx, qz):
    """Walkable collision top at the center of a half-block quadrant, in half-block units."""
    p = properties(state)
    if '_slab' in base(state):
        return y*2+1 if p.get('type') == 'bottom' else y*2+2
    if '_stairs' in base(state) and p.get('half') == 'bottom':
        high = {'east': qx == 1, 'west': qx == 0, 'south': qz == 1, 'north': qz == 0}[p['facing']]
        return y*2+1+int(high)
    return y*2+2
