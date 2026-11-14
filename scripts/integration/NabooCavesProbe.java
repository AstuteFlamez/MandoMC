package net.mandomc.verification;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.lang.reflect.Method;
import net.mandomc.gameplay.arrival.ArrivalDestinations;
import org.bukkit.*;
import org.bukkit.plugin.java.JavaPlugin;

/** Bounded native terrain readback; no terrain or player mutation. */
public final class NabooCavesProbe extends JavaPlugin {
    private World world; private DataInputStream input; private String[] states;
    private int remaining,checks,chunkIndex,delay;private boolean done;
    private final List<int[]> chunks=new ArrayList<>();
    private final Set<org.bukkit.Chunk> forced=new HashSet<>();
    private void check(boolean value,String reason){if(!value)throw new IllegalStateException(reason);checks++;}
    @Override public void onEnable(){
        try {
            world=Bukkit.getWorld(NamespacedKey.minecraft("naboo"));
            check(world!=null,"Naboo loaded");
            input=new DataInputStream(new BufferedInputStream(Files.newInputStream(getDataFolder().toPath().resolve("expected.bin")),1024*1024));
            remaining=input.readInt();states=new String[input.readInt()];for(int i=0;i<states.length;i++)states[i]=input.readUTF();
            for(String row:Files.readAllLines(getDataFolder().toPath().resolve("chunks.txt")))chunks.add(coords(row));
            Bukkit.getScheduler().runTaskTimer(this,this::tick,100,1);
        }catch(Throwable error){fail(error);}
    }
    private int[] coords(String row){return Arrays.stream(row.split(" ")).mapToInt(Integer::parseInt).toArray();}
    private void tick(){
        if(done)return;
        try {
            if(chunkIndex<chunks.size()) {
                for(int i=0;i<3&&chunkIndex<chunks.size();i++) {
                    var p=chunks.get(chunkIndex++);var c=world.getChunkAt(p[0],p[1]);
                    if(!c.isForceLoaded()){c.setForceLoaded(true);forced.add(c);}
                }
                return;
            }
            if(delay++<40)return;
            int budget=16000;
            while(remaining>0&&budget-->0) {
                int x=input.readInt(),y=input.readInt(),z=input.readInt(),index=input.readInt();
                String actual=world.getBlockAt(x,y,z).getBlockData().getAsString();
                check(actual.equals(states[index]),"Terrain mismatch at "+x+","+y+","+z+": "+actual+" expected "+states[index]);remaining--;
            }
            if(remaining>0)return;
            check(input.read()==-1,"No trailing expected terrain data");input.close();input=null;
            verifyArchitecture();
            for(String row:Files.readAllLines(getDataFolder().toPath().resolve("rooms.txt"))) {
                int[] p=coords(row);try { ArrivalDestinations.safeSpawn(new Location(world,p[0]+.5,p[1],p[2]+.5)); }
                catch (RuntimeException error) { throw new IllegalStateException("Cave anchor " + row, error); }
                checks++;
            }
            for(String row:Files.readAllLines(getDataFolder().toPath().resolve("lights.txt"))) {
                int[] p=coords(row);var b=world.getBlockAt(p[0],p[1],p[2]);
                check(b.getType()==Material.GLOW_LICHEN,"Supported cave lichen retained");
                check(b.getLightFromBlocks()>0,"Cave relighting complete");
            }
            Path decorations = getDataFolder().toPath().resolve("decorations.txt");
            if (Files.exists(decorations)) {
                for (String row : Files.readAllLines(decorations)) {
                    int[] p = coords(row);
                    var block = world.getBlockAt(p[0], p[1], p[2]);
                    check(block.getBlockData().isSupported(block), "Cave decoration physically supported: " + row);
                }
            }
            Path pools = getDataFolder().toPath().resolve("pools.txt");
            if (Files.exists(pools)) {
                for (String row : Files.readAllLines(pools)) {
                    int[] p = coords(row);
                    var block = world.getBlockAt(p[0], p[1], p[2]);
                    check(block.getType() == Material.WATER, "Pool water retained: " + row);
                    check(block.getRelative(0, -1, 0).getType().isOccluding(), "Pool has a solid floor: " + row);
                    for (int[] face : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                        Material side = block.getRelative(face[0], 0, face[1]).getType();
                        check(side == Material.WATER || side.isOccluding(), "Pool waterline enclosed: " + row);
                    }
                }
            }
            done=true;release();Files.writeString(getDataFolder().toPath().resolve("PASSED.txt"),"NABOO TERRAIN PASSED: "+checks);getLogger().info("NABOO TERRAIN PASSED: "+checks);
        }catch(Throwable error){fail(error);}
    }
    private void verifyArchitecture()throws Exception {
        Class<?> getter=Class.forName("net.minecraft.world.level.BlockGetter");
        Class<?> pos=Class.forName("net.minecraft.core.BlockPos");
        Object empty=Class.forName("net.minecraft.world.level.EmptyBlockGetter").getField("INSTANCE").get(null);
        Object zero=pos.getField("ZERO").get(null);
        Class<?> shape=Class.forName("net.minecraft.world.phys.shapes.VoxelShape");
        Class<?> op=Class.forName("net.minecraft.world.phys.shapes.BooleanOp");
        Method unequal=Class.forName("net.minecraft.world.phys.shapes.Shapes").getMethod("joinIsNotEmpty",shape,shape,op);
        Object xor=op.getField("NOT_SAME").get(null);
        for(String row:Files.readAllLines(getDataFolder().toPath().resolve("recolors.tsv"))) {
            String[] pair=row.split("\t");var before=Bukkit.createBlockData(pair[0]);var after=Bukkit.createBlockData(pair[1]);
            String a=before.getAsString(),b=after.getAsString();
            check(a.substring(a.contains("[")?a.indexOf('['):a.length()).equals(b.substring(b.contains("[")?b.indexOf('['):b.length())),"All orientation/state properties preserved");
            Object first=before.getClass().getMethod("getState").invoke(before),second=after.getClass().getMethod("getState").invoke(after);
            Object firstShape=first.getClass().getMethod("getCollisionShape",getter,pos).invoke(first,empty,zero);
            Object secondShape=second.getClass().getMethod("getCollisionShape",getter,pos).invoke(second,empty,zero);
            check(!(boolean)unequal.invoke(null,firstShape,secondShape,xor),"Recolor collision geometry unchanged: "+row);
        }
    }
    private void release(){for(var c:forced)c.setForceLoaded(false);forced.clear();}
    private void fail(Throwable error){
        done=true;release();getLogger().log(java.util.logging.Level.SEVERE,"NABOO TERRAIN FAILED",error);
        try{getDataFolder().mkdirs();Files.writeString(getDataFolder().toPath().resolve("FAILED.txt"),error.toString());}catch(Exception ignored){}
    }
    @Override public void onDisable(){release();try{if(input!=null)input.close();}catch(IOException ignored){}}
}
