package net.mandomc.core.services;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ServiceRegistry}.
 */
class ServiceRegistryTest {

    private ServiceRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ServiceRegistry();
    }

    @Test
    void register_and_get_returnsService() {
        String service = "hello";
        registry.register(String.class, service);
        assertSame(service, registry.get(String.class));
    }

    @Test
    void get_whenNotRegistered_throwsServiceNotFoundException() {
        assertThrows(ServiceNotFoundException.class, () -> registry.get(Integer.class));
    }

    @Test
    void register_duplicate_throwsIllegalArgumentException() {
        registry.register(String.class, "first");
        assertThrows(IllegalArgumentException.class, () -> registry.register(String.class, "second"));
    }

    @Test
    void has_returnsTrueWhenRegistered() {
        registry.register(String.class, "value");
        assertTrue(registry.has(String.class));
    }

    @Test
    void has_returnsFalseWhenNotRegistered() {
        assertFalse(registry.has(Integer.class));
    }

    @Test
    void unregister_removesService() {
        registry.register(String.class, "value");
        registry.unregister(String.class);
        assertFalse(registry.has(String.class));
    }

    @Test
    void clear_removesAllServices() {
        registry.register(String.class, "a");
        registry.register(Integer.class, 1);
        registry.clear();
        assertFalse(registry.has(String.class));
        assertFalse(registry.has(Integer.class));
    }
}
