package net.mandomc.core.services;

/**
 * Thrown when a required service is not found in the {@link ServiceRegistry}.
 *
 * This typically indicates an incorrect module load order: the consuming
 * module's enable() runs before the providing module has registered the service.
 */
public final class ServiceNotFoundException extends RuntimeException {

    public ServiceNotFoundException(String message) {
        super(message);
    }
}
