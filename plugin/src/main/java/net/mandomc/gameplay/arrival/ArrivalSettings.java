package net.mandomc.gameplay.arrival;

public record ArrivalSettings(String spawnWorld, String survivalWorld, int warmupSeconds) {
    public ArrivalSettings {
        validateWorld("arrival.spawn-world", spawnWorld);
        validateWorld("arrival.survival-world", survivalWorld);
        if (spawnWorld.equals(survivalWorld)) {
            throw new IllegalArgumentException("config.yml: arrival worlds must be different");
        }
        if (warmupSeconds < 1 || warmupSeconds > 30) {
            throw new IllegalArgumentException("config.yml: arrival.warmup-seconds must be between 1 and 30");
        }
    }

    private static void validateWorld(String key, String value) {
        if (value == null || !value.matches("[a-z0-9._-]+:[a-z0-9/._-]+")) {
            throw new IllegalArgumentException("config.yml: " + key + " must be a namespaced world key");
        }
    }
}
