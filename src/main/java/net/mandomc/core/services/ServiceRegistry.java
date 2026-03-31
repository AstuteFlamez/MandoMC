package net.mandomc.core.services;

import java.util.HashMap;
import java.util.Map;

/**
 * Composition root that holds all plugin services.
 *
 * Modules register services they produce and retrieve services they consume.
 * Only MandoMC.java holds a reference to this registry; modules receive it
 * via their {@code enable(ServiceRegistry)} call.
 *
 * Thread-safety: accessed only on the main thread during enable/disable cycles.
 */
public final class ServiceRegistry {

    private final Map<Class<?>, Object> services = new HashMap<>();

    /**
     * Registers a service implementation under the given type key.
     *
     * @param type    the service interface or class to register under
     * @param service the service instance
     * @param <T>     the service type
     * @throws IllegalArgumentException if a service is already registered for {@code type}
     */
    public <T> void register(Class<T> type, T service) {
        if (services.containsKey(type)) {
            throw new IllegalArgumentException(
                    "A service is already registered for: " + type.getName()
                            + ". Unregister it first or check module load order.");
        }
        services.put(type, service);
    }

    /**
     * Retrieves a service registered under the given type.
     *
     * @param type the service type to look up
     * @param <T>  the service type
     * @return the registered service instance
     * @throws ServiceNotFoundException if no service is registered for {@code type}
     */
    @SuppressWarnings("unchecked")
    public <T> T get(Class<T> type) {
        Object service = services.get(type);
        if (service == null) {
            throw new ServiceNotFoundException(
                    "No service registered for: " + type.getName()
                            + ". Check that the providing module is enabled before the consuming module.");
        }
        return (T) service;
    }

    /**
     * Returns true if a service is registered for the given type.
     *
     * @param type the service type to check
     * @return true if registered
     */
    public boolean has(Class<?> type) {
        return services.containsKey(type);
    }

    /**
     * Removes a service registration. Called from module disable() for cleanup.
     *
     * @param type the service type to remove
     */
    public void unregister(Class<?> type) {
        services.remove(type);
    }

    /**
     * Clears all registered services. Called on full plugin reload.
     */
    public void clear() {
        services.clear();
    }
}
