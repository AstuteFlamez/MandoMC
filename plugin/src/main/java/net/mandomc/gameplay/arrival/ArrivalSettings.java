package net.mandomc.gameplay.arrival;

public record ArrivalSettings(String spawnWorld, String survivalWorld) {
    public ArrivalSettings {
        validateWorld("arrival.spawn-world", spawnWorld);
        if (survivalWorld != null) validateWorld("arrival.survival-world", survivalWorld);
        if (spawnWorld.equals(survivalWorld)) {
            throw new IllegalArgumentException("config.yml: arrival worlds must be different");
        }

    }

    private static void validateWorld(String key, String value) {
        if (value == null || !value.matches("[a-z0-9._-]+:[a-z0-9/._-]+")) {
            throw new IllegalArgumentException("config.yml: " + key + " must be a namespaced world key");
        }
    }
}
