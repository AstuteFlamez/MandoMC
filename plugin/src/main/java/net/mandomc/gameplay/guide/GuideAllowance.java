package net.mandomc.gameplay.guide;

import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/** Used only on the guide worker. Reservations are durable before HTTP and cap shared token use conservatively. */
public final class GuideAllowance {
    public enum Denial { NONE, DAILY, COOLDOWN, SHARED }
    private final Path file;private final int personal,tokenBudget,reservation,cooldown,requestBudget;private final Properties values=new Properties();
    public GuideAllowance(Path file,int personal,int tokenBudget,int reservation,int cooldown) throws IOException{
        this(file,personal,tokenBudget,reservation,cooldown,800);
    }
    public GuideAllowance(Path file,int personal,int tokenBudget,int reservation,int cooldown,int requestBudget) throws IOException{
        this.file=file;this.personal=personal;this.tokenBudget=tokenBudget;this.reservation=reservation;this.cooldown=cooldown;this.requestBudget=requestBudget;
        if(personal<1 || tokenBudget<reservation || reservation<1 || cooldown<1 || requestBudget<1)throw new IllegalArgumentException("Invalid droid allowance");
        if(Files.exists(file))try(var in=Files.newInputStream(file)){values.load(in);}
    }
    private long number(String key){return Long.parseLong(values.getProperty(key,"0"));}
    private void set(String key,long value){values.setProperty(key,""+value);}
    private void day(Instant now){String today=now.atZone(ZoneId.of("America/Chicago")).toLocalDate().toString();if(!today.equals(values.getProperty("day"))){values.clear();values.setProperty("day",today);}}
    public Denial reserve(UUID player,Instant now) throws IOException{
        day(now);String id=player.toString();if(number("count."+id)>=personal)return Denial.DAILY;
        if(now.getEpochSecond()-number("last."+id)<cooldown)return Denial.COOLDOWN;
        if(number("requests")>=requestBudget || number("tokens")+reservation>tokenBudget)return Denial.SHARED;
        set("requests",number("requests")+1);set("count."+id,number("count."+id)+1);set("last."+id,now.getEpochSecond());set("tokens",number("tokens")+reservation);save();return Denial.NONE;
    }
    public void finish(UUID player,Instant reservedAt,int actualTokens,boolean success) throws IOException{
        // A response crossing midnight must not alter the new day's counters.
        String original=reservedAt.atZone(ZoneId.of("America/Chicago")).toLocalDate().toString();if(!original.equals(values.getProperty("day")))return;
        if(success)set("tokens",Math.max(0,number("tokens")-reservation+Math.max(0,actualTokens)));
        else {set("count."+player,Math.max(0,number("count."+player)-1));values.remove("last."+player);}
        save();
    }
    public void save() throws IOException{
        Files.createDirectories(file.getParent());var tmp=Files.createTempFile(file.getParent(),"guide-allowance-",".tmp");
        try{try(var out=Files.newOutputStream(tmp)){values.store(out,"M4-ND allowance; no prompts, replies or credentials");}Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}finally{Files.deleteIfExists(tmp);}
    }
}
