package net.mandomc.gameplay.combat;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

/** Block-exact geometry shared by client glass and the movement guard. */
public final class CombatBoundary {
    public record Cell(int x, int y, int z) {
        public Cell offset(int dx, int dy, int dz) { return new Cell(x + dx, y + dy, z + dz); }
    }
    private CombatBoundary() { }

    /** Visits every voxel crossed, including a thin corner clipped between otherwise safe endpoints. */
    public static boolean enters(double x, double y, double z, double tx, double ty, double tz,
                                 Predicate<Cell> safe) {
        if (!Double.isFinite(x + y + z + tx + ty + tz)) return true;
        Cell cell = new Cell(floor(x), floor(y), floor(z));
        if (safe.test(cell)) return false; // A tagged player already inside can still leave.
        Cell end = new Cell(floor(tx), floor(ty), floor(tz));
        double dx = tx - x, dy = ty - y, dz = tz - z;
        int sx = Double.compare(dx, 0), sy = Double.compare(dy, 0), sz = Double.compare(dz, 0);
        double ax = step(dx), ay = step(dy), az = step(dz);
        double nx = next(x, cell.x(), dx), ny = next(y, cell.y(), dy), nz = next(z, cell.z(), dz);
        for (int count = 0; count < 4096; count++) {
            if (cell.equals(end)) return false;
            double crossing = Math.min(nx, Math.min(ny, nz));
            // A negative-direction boundary at t=1 belongs to the endpoint voxel, not the voxel beyond it.
            if (crossing >= 1) return safe.test(end);
            // At an exact corner, advance all tied axes; a mere edge touch is not penetration.
            boolean bx = nx == crossing, by = ny == crossing, bz = nz == crossing;
            cell = cell.offset(bx ? sx : 0, by ? sy : 0, bz ? sz : 0);
            if (bx) nx += ax;
            if (by) ny += ay;
            if (bz) nz += az;
            if (safe.test(cell)) return true;
        }
        return true; // Bound work for invalid or extreme movement packets.
    }

    private static int floor(double value) { return (int) Math.floor(value); }
    private static double step(double delta) { return delta == 0 ? Double.POSITIVE_INFINITY : 1 / Math.abs(delta); }
    private static double next(double position, int block, double delta) {
        if (delta == 0) return Double.POSITIVE_INFINITY;
        return ((delta > 0 ? block + 1d : block) - position) / delta;
    }

    /** Safe-side surface only. Interior cells and boundaries between two safe regions stay invisible. */
    public static Set<Cell> wall(Cell center, int radius, int below, int above, Predicate<Cell> safe) {
        Set<Cell> cells = new HashSet<>();
        for (int x = center.x() - radius; x <= center.x() + radius; x++) {
            for (int z = center.z() - radius; z <= center.z() + radius; z++) {
                for (int y = center.y() - below; y <= center.y() + above; y++) {
                    var cell = new Cell(x, y, z);
                    if (safe.test(cell) && (!safe.test(cell.offset(-1, 0, 0)) || !safe.test(cell.offset(1, 0, 0))
                            || !safe.test(cell.offset(0, -1, 0)) || !safe.test(cell.offset(0, 1, 0))
                            || !safe.test(cell.offset(0, 0, -1)) || !safe.test(cell.offset(0, 0, 1)))) cells.add(cell);
                }
            }
        }
        return cells;
    }
}
