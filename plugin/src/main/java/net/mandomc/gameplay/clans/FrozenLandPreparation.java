package net.mandomc.gameplay.clans;

import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import net.mandomc.config.AtomicData;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/** Explicit, finite, restart-resumable capture. The scope file is a user-requested frozen version. */
public final class FrozenLandPreparation implements AutoCloseable {
    private final JavaPlugin plugin;
    private final ClanService clans;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final List<ClanState.Chunk> scope = new ArrayList<>();
    private final Set<ClanState.Chunk> completed = new HashSet<>();
    private final Path journal;
    private final BukkitTask task;
    private ClanState.Chunk current;
    private boolean preserved, loading, writing, failed, closed;
    private int cursor;
    private long nextSnapshot;

    public FrozenLandPreparation(JavaPlugin plugin, ClanService clans) throws Exception {
        this.plugin=plugin;this.clans=clans;
        Path file=plugin.getDataFolder().toPath().resolve("clans/prepared-land.yml");
        if (!Files.exists(file)) {journal=null;task=null;return;}
        var config=new YamlConfiguration();config.load(file.toFile());
        if(config.getInt("schema-version")!=1)throw new IllegalArgumentException("prepared-land.yml: schema-version 1 required");
        String id=config.getString("capture-id","");
        if(!id.matches("[a-z0-9_-]{1,64}"))throw new IllegalArgumentException("prepared-land.yml: invalid capture-id");
        var unique=new LinkedHashSet<ClanState.Chunk>();
        for(String row:config.getStringList("chunks")) {
            String[] parts=row.split(" ");
            if(parts.length!=3 || !parts[0].matches("[a-z0-9_-]+:[a-z0-9_/-]+"))
                throw new IllegalArgumentException("prepared-land.yml: invalid chunk "+row);
            var chunk=new ClanState.Chunk(parts[0],Integer.parseInt(parts[1]),Integer.parseInt(parts[2]));
            if(Math.abs((long)chunk.x())>1875000 || Math.abs((long)chunk.z())>1875000 || !unique.add(chunk))
                throw new IllegalArgumentException("prepared-land.yml: duplicate/out-of-range chunk");
        }
        if(unique.isEmpty()||unique.size()>8192)throw new IllegalArgumentException("prepared-land.yml: expected 1..8192 finite chunks");
        scope.addAll(unique);
        Path folder=plugin.getDataFolder().toPath().resolve("data/frozen-land");Files.createDirectories(folder);
        journal=folder.resolve(id+".journal");
        String scopeHash=hex(Files.readAllBytes(file));Path hashFile=folder.resolve(id+".scope-sha256");
        if(Files.exists(hashFile) && !Files.readString(hashFile).trim().equals(scopeHash))
            throw new IllegalStateException("Frozen scope changed for "+id+"; use a new explicitly requested capture-id");
        if(!Files.exists(hashFile))AtomicData.write(hashFile,scopeHash+"\n");
        // Hash validation is off-thread too; completed entries never overwrite their originals.
        loading=true;
        io.execute(()->{
            try {
                var verified=new HashSet<ClanState.Chunk>();
                if(Files.exists(journal)) {
                    byte[] bytes=Files.readAllBytes(journal);
                    if(bytes.length>0&&bytes[bytes.length-1]!='\n') {
                        int last=bytes.length-1;while(last>=0&&bytes[last]!='\n')last--;
                        try(var channel=FileChannel.open(journal,StandardOpenOption.WRITE)){channel.truncate(last+1);channel.force(true);}
                    }
                }
                if(Files.exists(journal))for(String line:Files.readAllLines(journal)) {
                    if(line.isBlank())continue;String[] bits=line.split(" ");
                    if(bits.length!=5)throw new IllegalStateException("Incomplete/corrupt frozen journal: "+journal);
                    var chunk=new ClanState.Chunk(bits[0],Integer.parseInt(bits[1]),Integer.parseInt(bits[2]));
                    if(!unique.contains(chunk)||!hex(Files.readAllBytes(clans.land().baselinePath(chunk))).equals(bits[3]))
                        throw new IllegalStateException("Frozen baseline checksum mismatch: "+chunk);
                    verified.add(chunk);
                }
                main(()->{completed.addAll(verified);clans.land().preparedScope(unique);loading=false;});
            }catch(Exception e){failure(e);}
        });
        task=Bukkit.getScheduler().runTaskTimer(plugin,this::tick,5,1);
    }
    private static String hex(byte[] data)throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    }
    private void main(Runnable action){if(!closed&&plugin.isEnabled())Bukkit.getScheduler().runTask(plugin,action);}
    private void failure(Exception error){
        plugin.getLogger().log(java.util.logging.Level.SEVERE,"Frozen land preparation stopped; originals retained",error);
        if(!closed)io.execute(()->{try{AtomicData.write(journal.resolveSibling(journal.getFileName()+".failed"),error+"\n");}
            catch(Exception nested){plugin.getLogger().log(java.util.logging.Level.SEVERE,"Cannot write frozen capture failure receipt",nested);}});
        main(()->{clans.land().preparedScope(completed);failed=true;loading=false;writing=false;});
    }
    private void tick() {
        if(closed||failed||loading||writing)return;
        if(clans.land().failed()){failure(new IllegalStateException("Land snapshot storage failed"));return;}
        if(current==null) {
            if(System.nanoTime()<nextSnapshot)return;
            while(cursor<scope.size()&&completed.contains(scope.get(cursor)))cursor++;
            if(cursor==scope.size()) {
                writing=true;
                io.execute(()->{try{
                    String summary="completed="+completed.size()+"\nexpected="+scope.size()+"\njournal-sha256="+hex(Files.readAllBytes(journal))+"\n";
                    AtomicData.write(journal.resolveSibling(journal.getFileName()+".completed"),summary);
                    main(()->{if(task!=null)task.cancel();plugin.getLogger().info("Frozen wilderness backup complete: "+completed.size()+"/"+scope.size()+" chunks; journal "+journal.getFileName());});
                }catch(Exception e){failure(e);}});
                return;
            }
            current=scope.get(cursor);
            var world=Bukkit.getWorld(NamespacedKey.fromString(current.world()));
            if(world==null||!clans.wilderness(current)){failure(new IllegalStateException("Scope is unavailable or no longer claimable wilderness: "+current));return;}
            preserved=clans.land().hasBaseline(current);
            if(preserved){record();return;}
            loading=true;var requested=current;
            world.getChunkAtAsync(current.x(),current.z(),false).whenComplete((chunk,error)->main(()->{
                loading=false;
                if(error!=null||chunk==null){failure(new IllegalStateException("Existing chunk unavailable; generation forbidden: "+requested,error));return;}
                if(!clans.wilderness(requested)){failure(new IllegalStateException("Territory changed before capture: "+requested));return;}
                nextSnapshot=System.nanoTime()+250_000_000L;
                clans.land().capture(world,requested.x(),requested.z());
                if(!clans.land().hasBaseline(requested))failure(new IllegalStateException("Chunk unloaded before snapshot; resume after fixing: "+requested));
            }));
        } else if(clans.land().hasBaseline(current)&&clans.land().ready(current)) record();
    }
    private void record() {
        // A retained baseline can be restoring. It is still an original and must never be replaced.
        if(!preserved&&!clans.land().ready(current))return;
        writing=true;var chunk=current;boolean kept=preserved;
        io.execute(()->{
            try {
                String hash=hex(Files.readAllBytes(clans.land().baselinePath(chunk)));
                String line=chunk.world()+" "+chunk.x()+" "+chunk.z()+" "+hash+" "+(kept?"preserved":"captured")+"\n";
                try(var channel=FileChannel.open(journal,StandardOpenOption.CREATE,StandardOpenOption.WRITE,StandardOpenOption.APPEND)) {
                    var bytes=StandardCharsets.UTF_8.encode(line);while(bytes.hasRemaining())channel.write(bytes);channel.force(true);
                }
                main(()->{completed.add(chunk);current=null;cursor++;writing=false;
                    if(completed.size()%100==0)plugin.getLogger().info("Frozen wilderness backup: "+completed.size()+"/"+scope.size());});
            }catch(Exception e){failure(e);}
        });
    }
    public int completed(){return completed.size();}
    public int total(){return scope.size();}
    public boolean failed(){return failed;}
    @Override public void close(){closed=true;if(task!=null)task.cancel();io.shutdown();}
}
