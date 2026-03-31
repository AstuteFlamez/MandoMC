package net.mandomc.core.storage;

import java.util.Collection;
import java.util.Optional;

/**
 * Generic CRUD interface for persistent storage of entities of type {@code T}
 * identified by keys of type {@code ID}.
 *
 * Implementations must be thread-safe.
 *
 * @param <T>  the entity type
 * @param <ID> the key type
 */
public interface Repository<T, ID> {

    /**
     * Returns the entity with the given ID, or empty if not found.
     *
     * @param id the entity identifier
     * @return an Optional wrapping the entity, or empty
     */
    Optional<T> findById(ID id);

    /**
     * Returns a snapshot of all stored entities.
     *
     * @return unmodifiable collection of all entities
     */
    Collection<T> findAll();

    /**
     * Inserts or replaces the entity in the in-memory cache.
     * Call {@link #flush()} to persist changes to disk / database.
     *
     * @param entity the entity to store
     */
    void save(T entity);

    /**
     * Removes the entity with the given ID from the in-memory cache.
     * Call {@link #flush()} to persist changes to disk / database.
     *
     * @param id the identifier of the entity to remove
     */
    void delete(ID id);

    /**
     * Loads all entities from the backing store into the in-memory cache.
     * Replaces any existing cache state.
     */
    void load();

    /**
     * Persists the current in-memory cache to the backing store.
     */
    void flush();
}
