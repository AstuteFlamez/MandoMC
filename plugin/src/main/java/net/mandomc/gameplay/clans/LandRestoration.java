package net.mandomc.gameplay.clans;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Predicate;
import java.util.zip.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;

/** Pristine chunk terrain, captured before the first player edit or claim, never seed-regenerated.
 * Snapshot compression and atomic persistence use the shared IO worker. Only loaded chunks animate.
 * No inventories are copied: restoration cannot replenish looted containers.
 */
public final class LandRestoration implements AutoCloseable {
    private static final class Land {
        final ClanState.Chunk chunk;
        final int minY, maxY;
        String[] blocks;
        boolean loading;
        final BitSet staff = new BitSet();
        long due;
        int cursor;
        boolean ready;
        boolean forced;
        Land(ClanState.Chunk chunk, int minY, int maxY, String[] blocks, long due) {
            this.chunk=chunk; this.minY=minY; this.maxY=maxY; this.blocks=blocks; this.due=due;
        }
    }
    private final JavaPlugin plugin;
    private final ExecutorService io;
    private final Path folder;
    private final long delay;
    private final int budget;
    private final Predicate<ClanState.Chunk> claimed;
    private final Map<ClanState.Chunk,Land> lands = new LinkedHashMap<>();
    private final BukkitTask task;
    private boolean failed;
    private volatile boolean closed;


    public LandRestoration(JavaPlugin plugin, ExecutorService io, long delay, int budget,
                           Predicate<ClanState.Chunk> claimed) throws Exception {
        this.plugin=plugin; this.io=io; this.delay=delay; this.budget=budget; this.claimed=claimed;
        folder=plugin.getDataFolder().toPath().resolve("data/land-baselines");
        if (Files.exists(folder)) try (var paths=Files.list(folder)) {
            for (Path path:paths.filter(p->p.toString().endsWith(".gz")).toList()) {
                Land land=read(path,false);
                Path metadata=path.resolveSibling(path.getFileName()+".meta");
                if(Files.exists(metadata)) {
                    var lines=Files.readString(metadata).split("\\n",-1);
                    land.due=Long.parseLong(lines[0]);land.cursor=Integer.parseInt(lines[1]);
                    if(land.cursor<0||land.cursor>=(land.maxY-land.minY)*256)throw new IOException("Invalid restore cursor");
                    land.staff.or(BitSet.valueOf(Base64.getDecoder().decode(lines[2])));
                    land.forced=lines.length>3&&Boolean.parseBoolean(lines[3]);
                }
                land.ready=true; lands.put(land.chunk,land);
            }
        }
        task=Bukkit.getScheduler().runTaskTimer(plugin,()->{
            try{tick();}catch(RuntimeException error){failed=true;failure(error);}
        },5,5);
    }
    public static ClanState.Chunk chunk(Block block) {
        return new ClanState.Chunk(block.getWorld().getKey().toString(),block.getX()>>4,block.getZ()>>4);
    }
    public static ClanState.Chunk chunk(Location l) {
        return new ClanState.Chunk(l.getWorld().getKey().toString(),l.getBlockX()>>4,l.getBlockZ()>>4);
    }
    private Path path(ClanState.Chunk c) {
        return folder.resolve(c.world().replace(':','_').replace('/','_')+"_"+c.x()+"_"+c.z()+".gz");
    }
    /** First attempt waits for the durable baseline. Retry after the preparation message. */
    public boolean prepare(World world, int x, int z) { return prepare(world,x,z,Map.of()); }
    public boolean prepare(World world, int x, int z, Map<Integer,String> originals) {
        var key=new ClanState.Chunk(world.getKey().toString(),x,z);
        var old=lands.get(key);
        if (failed) return false;
        if (old!=null) return old.ready && old.cursor==0 && !old.forced;
        var snapshot=world.getChunkAt(x,z).getChunkSnapshot(true,false,false);
        int min=world.getMinHeight(), max=world.getMaxHeight();
        // ChunkSnapshot is safe to read on the worker; no live world access occurs there.
        var land=new Land(key,min,max,new String[(max-min)*256],0);
        lands.put(key,land);
        io.execute(()->{
            try {
                var palette=new HashMap<String,String>();
                for(int y=min;y<max;y++) for(int z1=0;z1<16;z1++) for(int x1=0;x1<16;x1++) {
                    String data=originals.getOrDefault((y-min)*256+z1*16+x1,snapshot.getBlockData(x1,y,z1).getAsString());
                    land.blocks[(y-min)*256+z1*16+x1]=palette.computeIfAbsent(data,k->k);
                }
                write(land,0,0,new BitSet());
                if(!closed&&plugin.isEnabled())Bukkit.getScheduler().runTask(plugin,()->{land.blocks=null;land.ready=true;});
            } catch(Exception error) { failure(error); }
        });
        return false;
    }
    public boolean ready(ClanState.Chunk chunk) {
        var land=lands.get(chunk); return !failed && land!=null && land.ready && land.cursor==0 && !land.forced;
    }
    public boolean restoring(ClanState.Chunk chunk) {
        var land=lands.get(chunk); return land!=null && (land.cursor>0||land.forced);
    }
    public boolean hasBaseline(ClanState.Chunk chunk) { return lands.containsKey(chunk); }
    public void touch(ClanState.Chunk chunk) {
        var land=lands.get(chunk);
        if (land==null || !land.ready || land.cursor>0 || land.forced) return;
        land.due=System.currentTimeMillis()+delay; checkpoint(land);
    }
    public void inherit(ClanState.Chunk from,ClanState.Chunk to) {
        var source=lands.get(from);var target=lands.get(to);
        if(source!=null&&target!=null&&source.due>target.due){target.due=source.due;checkpoint(target);}
    }
    public void release(ClanState.Chunk chunk, boolean immediate) {
        var land=lands.get(chunk);
        if (land==null) throw new IllegalStateException("Missing land baseline for "+chunk);
        land.forced=immediate;
        land.due=immediate?System.currentTimeMillis():System.currentTimeMillis()+delay;
        checkpoint(land);
    }
    public void staffEdit(Block block) {
        var land=lands.get(chunk(block)); if(land==null)return;
        land.staff.set((block.getY()-land.minY)*256+(block.getZ()&15)*16+(block.getX()&15));
        checkpoint(land);
    }
    private void checkpoint(Land land) {
        String metadata=land.due+"\n"+land.cursor+"\n"+Base64.getEncoder().encodeToString(land.staff.toByteArray())+"\n"+land.forced+"\n";
        Path target=path(land.chunk).resolveSibling(path(land.chunk).getFileName()+".meta");
        io.execute(()->{try {net.mandomc.config.AtomicData.write(target,metadata);}catch(Exception e){failure(e);}});
    }
    private void failure(Exception error) {
        plugin.getLogger().log(java.util.logging.Level.SEVERE,"Cannot persist land restoration; edits fail closed",error);
        if (plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin,()->failed=true);
    }
    private void tick() {
        if(failed)return;
        int remaining=budget;
        int scanBudget=4096;
        long now=System.currentTimeMillis();
        for(var land:List.copyOf(lands.values())) {
            if(remaining<=0||scanBudget<=0)break;
            if(!land.ready||land.due==0||land.due>now||claimed.test(land.chunk))continue;
            World world=Bukkit.getWorld(NamespacedKey.fromString(land.chunk.world()));
            if(world==null||!world.isChunkLoaded(land.chunk.x(),land.chunk.z()))continue;
            if(land.blocks==null) {
                if(!land.loading) {
                    land.loading=true;
                    io.execute(()->{try {
                        var loaded=read(path(land.chunk),true);
                        if(!closed&&plugin.isEnabled())Bukkit.getScheduler().runTask(plugin,()->{land.blocks=loaded.blocks;land.loading=false;});
                    }catch(Exception error){failure(error);}});
                }
                continue;
            }
            int scanned=0; boolean changed=false;
            // Bounded scans keep even large air/unchanged runs from monopolizing a tick.
            while(land.cursor<land.blocks.length&&remaining>0&&scanBudget>0) {
                scanBudget--;scanned++;
                int i=land.cursor, y=land.minY+i/256, x=land.chunk.x()*16+(i&15), z=land.chunk.z()*16+(i%256)/16;
                Block block=world.getBlockAt(x,y,z);
                if(!land.staff.get(i) && !block.getBlockData().getAsString().equals(land.blocks[i])) {
                    var data=Bukkit.createBlockData(land.blocks[i]);
                    var box=new BoundingBox(x,y,z,x+1,y+1,z+1);
                    if(data.getMaterial().isSolid()&&!world.getNearbyEntities(box,
                            e->e instanceof LivingEntity && e.getBoundingBox().overlaps(box)).isEmpty())break;
                    block.setBlockData(data,false);
                    if(scanned%3==0)world.spawnParticle(Particle.CLOUD,x+.5,y+.5,z+.5,2,.2,.2,.2,.01);
                    remaining--; changed=true;
                }
                land.cursor++;
            }
            if(land.cursor==land.blocks.length) {
                // Keep the same immutable terrain baseline for future edits; staff exclusions survive.
                land.cursor=0; land.due=0; land.forced=false; land.blocks=null; checkpoint(land);
            } else if(changed||scanned>1) checkpoint(land);
        }
    }
    private void write(Land land,long due,int cursor,BitSet staff) throws Exception {
        Files.createDirectories(folder);
        Path target=path(land.chunk), temporary=target.resolveSibling(target.getFileName()+".tmp");
        try(var out=new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(temporary)))) {
            out.writeInt(1); out.writeUTF(land.chunk.world()); out.writeInt(land.chunk.x()); out.writeInt(land.chunk.z());
            out.writeInt(land.minY); out.writeInt(land.maxY); out.writeLong(due); out.writeInt(cursor);
            var palette=new LinkedHashMap<String,Integer>();
            for(String data:land.blocks)palette.computeIfAbsent(data,k->palette.size());
            out.writeInt(palette.size()); for(String data:palette.keySet())out.writeUTF(data);
            for(String data:land.blocks)out.writeInt(palette.get(data));
            byte[] bits=staff.toByteArray();out.writeInt(bits.length);out.write(bits);
        }
        try(var channel=java.nio.channels.FileChannel.open(temporary,StandardOpenOption.WRITE)){channel.force(true);}
        Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
    }
    private Land read(Path path,boolean includeBlocks) throws Exception {
        try(var in=new DataInputStream(new GZIPInputStream(Files.newInputStream(path)))) {
            if(in.readInt()!=1)throw new IOException("Invalid baseline schema: "+path);
            var chunk=new ClanState.Chunk(in.readUTF(),in.readInt(),in.readInt());
            int min=in.readInt(),max=in.readInt();long due=in.readLong();int cursor=in.readInt();
            if(max<=min||max-min>4096)throw new IOException("Invalid baseline heights");
            if(!includeBlocks)return new Land(chunk,min,max,null,due);
            int count=in.readInt();if(count<1||count>65536)throw new IOException("Invalid baseline palette");
            var palette=new String[count];for(int i=0;i<count;i++){palette[i]=in.readUTF();}
            var blocks=new String[(max-min)*256];
            for(int i=0;i<blocks.length;i++){int index=in.readInt();if(index<0||index>=count)throw new IOException("Invalid palette index");blocks[i]=palette[index];}
            if(cursor<0||cursor>=blocks.length)throw new IOException("Invalid restoration cursor");
            var land=new Land(chunk,min,max,blocks,due);land.cursor=cursor;
            int bytes=in.readInt();if(bytes<0||bytes>(blocks.length+7)/8)throw new IOException("Invalid staff mask");
            land.staff.or(BitSet.valueOf(in.readNBytes(bytes)));return land;
        }
    }
    @Override public void close(){closed=true;task.cancel();}
}
