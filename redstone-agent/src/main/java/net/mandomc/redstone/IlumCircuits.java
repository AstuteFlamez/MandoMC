package net.mandomc.redstone;

/** Bootstrap-visible helper: Paper's isolated class loader cannot see the agent's application loader. */
public final class IlumCircuits {
    private static final java.util.Map<Object, Boolean> WORLDS = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    private IlumCircuits() { }
    public static boolean ilum(Object level) {
        if (level == null) return false;
        return Boolean.TRUE.equals(WORLDS.computeIfAbsent(level, candidate -> {
            try {
                Object dimension = candidate.getClass().getMethod("dimension").invoke(candidate);
                if (dimension == null) return null; // World still constructing: do not cache a denial.
                return "minecraft:ilum".equals(dimension.getClass().getMethod("identifier").invoke(dimension).toString());
            } catch (ReflectiveOperationException ignored) { return false; }
        }));
    }
}
