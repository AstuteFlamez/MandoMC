package net.mandomc.core.storage;

import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * Abstract JSON-backed {@link Repository} implementation.
 *
 * Maintains an in-memory {@code LinkedHashMap} protected by a
 * {@link ReentrantReadWriteLock}. Subclasses implement JSON
 * serialisation/deserialisation via {@link #populate(String, Map)} and
 * {@link #serialize(Map)}, and provide an entity ID extractor via
 * {@link #idOf(Object)}.
 *
 * @param <T>  the entity type
 * @param <ID> the key type
 */
public abstract class JsonRepository<T, ID> implements Repository<T, ID> {

    private final Map<ID, T> cache = new LinkedHashMap<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    private final File file;
    private final Logger logger;
    private final Plugin plugin;
    private volatile boolean dirty;
    private BukkitTask scheduledFlushTask;

    /**
     * Creates the repository and ensures the backing file's parent directories exist.
     *
     * @param plugin       the plugin owning the data folder
     * @param relativePath path relative to the plugin data folder (e.g. {@code "bounties/bounties.json"})
     */
    protected JsonRepository(Plugin plugin, String relativePath) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), relativePath);
        this.logger = plugin.getLogger();
        this.file.getParentFile().mkdirs();
    }

    // -----------------------------------------------------------------------
    // Template methods — subclasses provide JSON logic
    // -----------------------------------------------------------------------

    /**
     * Parses {@code json} and populates {@code target} with loaded entities.
     * Called while the write lock is held.
     *
     * @param json   raw JSON string read from file
     * @param target the mutable cache map to populate
     */
    protected abstract void populate(String json, Map<ID, T> target);

    /**
     * Converts the current cache snapshot to a JSON string.
     * Called while the read lock is held.
     *
     * @param data unmodifiable view of the cache
     * @return JSON string to write to file
     */
    protected abstract String serialize(Map<ID, T> data);

    /**
     * Extracts the unique ID from an entity.
     *
     * @param entity the entity
     * @return the entity's ID
     */
    protected abstract ID idOf(T entity);

    // -----------------------------------------------------------------------
    // Repository implementation
    // -----------------------------------------------------------------------

    @Override
    public final void load() {
        lock.writeLock().lock();
        try {
            if (!file.exists()) {
                file.createNewFile();
                return;
            }
            String json = Files.readString(file.toPath());
            if (json.isBlank()) return;
            cache.clear();
            populate(json, cache);
            dirty = false;
        } catch (Exception e) {
            logger.severe("[" + getClass().getSimpleName() + "] Failed to load data: " + e.getMessage());
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public final void flush() {
        cancelScheduledFlush();
        boolean wrote = false;
        lock.readLock().lock();
        try {
            String json = serialize(Collections.unmodifiableMap(cache));
            Files.writeString(file.toPath(), json);
            wrote = true;
        } catch (IOException e) {
            logger.severe("[" + getClass().getSimpleName() + "] Failed to save data: " + e.getMessage());
        } finally {
            lock.readLock().unlock();
        }
        if (wrote) {
            dirty = false;
        }
    }

    /**
     * Schedules a debounced flush to reduce repeated disk writes in hot paths.
     *
     * If a flush task is already queued, this call is a no-op.
     * On scheduler errors (e.g. tests without Bukkit), falls back to immediate flush.
     *
     * @param delayTicks delay before flush
     */
    public final void flushSoon(long delayTicks) {
        if (!dirty) {
            return;
        }
        if (delayTicks <= 0) {
            flush();
            return;
        }
        synchronized (this) {
            if (scheduledFlushTask != null && !scheduledFlushTask.isCancelled()) {
                return;
            }
            try {
                scheduledFlushTask = org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    synchronized (JsonRepository.this) {
                        scheduledFlushTask = null;
                    }
                    flushIfDirty();
                }, delayTicks);
            } catch (Throwable ignored) {
                flush();
            }
        }
    }

    /**
     * Flushes only if local state is marked dirty.
     */
    public final void flushIfDirty() {
        if (dirty) {
            flush();
        }
    }

    /**
     * Marks the in-memory state as dirty when mutable entities are changed in place.
     */
    public final void touch() {
        dirty = true;
    }

    @Override
    public Optional<T> findById(ID id) {
        lock.readLock().lock();
        try {
            return Optional.ofNullable(cache.get(id));
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public Collection<T> findAll() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(cache.values());
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void save(T entity) {
        lock.writeLock().lock();
        try {
            cache.put(idOf(entity), entity);
            dirty = true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void delete(ID id) {
        lock.writeLock().lock();
        try {
            if (cache.remove(id) != null) {
                dirty = true;
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    // -----------------------------------------------------------------------
    // Protected helpers for subclasses
    // -----------------------------------------------------------------------

    /**
     * Returns the entity for {@code id}, or creates and caches a new one if absent.
     * Acquires the write lock.
     *
     * @param id      the entity identifier
     * @param factory creates the default entity when none exists
     * @return the existing or newly created entity
     */
    protected T getOrCreate(ID id, Function<ID, T> factory) {
        lock.writeLock().lock();
        try {
            return cache.computeIfAbsent(id, factory);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Returns true if an entity with the given ID exists in the cache.
     *
     * @param id the entity identifier
     * @return true if cached, false otherwise
     */
    protected boolean containsKey(ID id) {
        lock.readLock().lock();
        try {
            return cache.containsKey(id);
        } finally {
            lock.readLock().unlock();
        }
    }

    private void cancelScheduledFlush() {
        synchronized (this) {
            if (scheduledFlushTask != null && !scheduledFlushTask.isCancelled()) {
                scheduledFlushTask.cancel();
            }
            scheduledFlushTask = null;
        }
    }
}
