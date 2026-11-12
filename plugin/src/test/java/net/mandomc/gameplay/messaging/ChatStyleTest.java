package net.mandomc.gameplay.messaging;

import net.kyori.adventure.text.*;
import net.kyori.adventure.text.event.*;
import net.kyori.adventure.text.format.*;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.junit.Test;
import static org.junit.Assert.*;

public class ChatStyleTest {
    private final ChatStyle style=new ChatStyle();
    @Test public void adminIsEntirelyRedItalicButErrorsAreNot(){
        var input=MiniMessage.miniMessage().deserialize("<green>Changed <white>AstuteFlamez</white>.");
        effective(style.format(input,ChatCategory.ADMIN),null,TextDecoration.State.NOT_SET,true);
        effective(style.format(Component.text("No permission.",NamedTextColor.RED),ChatCategory.ERROR),null,TextDecoration.State.NOT_SET,false);
    }
    private void effective(Component c,TextColor inherited,TextDecoration.State italics,boolean admin){
        TextColor color=c.color()==null?inherited:c.color();
        var italic=c.decoration(TextDecoration.ITALIC)==TextDecoration.State.NOT_SET?italics:c.decoration(TextDecoration.ITALIC);
        if(c instanceof TextComponent t&&!t.content().isEmpty()){
            if(t.content().equals(" » ")){assertEquals(NamedTextColor.DARK_GRAY,color);assertEquals(TextDecoration.State.FALSE,italic);}
            else if(admin){assertEquals(NamedTextColor.RED,color);assertEquals(TextDecoration.State.TRUE,italic);}
            else {assertEquals(TextDecoration.State.FALSE,italic);assertEquals(NamedTextColor.RED,color);}
        }
        for(var child:c.children())effective(child,color,italic,admin);
    }
    @Test public void stripsSplitLegacyPrefixOnceAndKeepsEvents(){
        var click=ClickEvent.runCommand("/recipes");var hover=HoverEvent.showText(Component.text("Show recipes"));
        var input=Component.empty().append(Component.text("ᴍᴀɴᴅᴏᴍᴄ",NamedTextColor.RED)).append(Component.text(" » ",NamedTextColor.DARK_GRAY))
                .append(Component.text("Open recipes",NamedTextColor.GREEN).clickEvent(click).hoverEvent(hover));
        var result=style.format(input,ChatCategory.INFO);
        assertEquals("ᴍᴀɴᴅᴏᴍᴄ » Open recipes",ChatStyle.plain(result));
        assertTrue(hasEvents(result,click,hover));assertSame(result,style.format(result,ChatCategory.INFO));
    }
    private boolean hasEvents(Component c,ClickEvent click,HoverEvent<?> hover){
        return click.equals(c.clickEvent())&&hover.equals(c.hoverEvent())||c.children().stream().anyMatch(n->hasEvents(n,click,hover));
    }
    @Test public void literalPlayerMarkupIsNeverParsed(){
        var result=style.format(Component.text("Invited <red><click:run_command:/op me>name",NamedTextColor.GREEN),ChatCategory.SOCIAL);
        assertEquals("ᴍᴀɴᴅᴏᴍᴄ » Invited <red><click:run_command:/op me>name",ChatStyle.plain(result));
        assertNull(result.clickEvent());
    }
    @Test public void translationCauseAndArgumentsRemainNative(){
        var death=Component.translatable("death.attack.player",Component.text("Victim"),Component.text("Killer"));
        var result=style.format(death,ChatCategory.COMBAT);
        assertTrue(result.children().get(1) instanceof TranslatableComponent);
        assertEquals("death.attack.player",((TranslatableComponent)result.children().get(1)).key());
        assertEquals(2,((TranslatableComponent)result.children().get(1)).arguments().size());
    }
    @Test public void matchesScoreboardBrandAndKeepsSeparatorIndependent(){
        var result=style.format(Component.text("Ready",NamedTextColor.GREEN),ChatCategory.SUCCESS);
        var brand=result.children().getFirst();var separator=brand.children().getFirst();
        assertEquals("ᴍᴀɴᴅᴏᴍᴄ » Ready",ChatStyle.plain(result));
        assertEquals(net.kyori.adventure.key.Key.key("minecraft:default"),brand.font());
        assertEquals(TextDecoration.State.TRUE,brand.decoration(TextDecoration.BOLD));
        assertEquals(NamedTextColor.GREEN,brand.color());assertEquals(NamedTextColor.DARK_GRAY,separator.color());
        assertEquals(TextDecoration.State.FALSE,separator.decoration(TextDecoration.BOLD));
        for(String legacy:new String[]{"MandoMC » Ready","ᴍᴀɴᴅᴏᴍᴄ » Ready","[MandoMC] >> Ready"})
            assertEquals("ᴍᴀɴᴅᴏᴍᴄ » Ready",ChatStyle.plain(style.format(Component.text(legacy),ChatCategory.SUCCESS)));
        assertSame(result,style.format(result,ChatCategory.SUCCESS));
    }
    @Test public void templatesAreAnchoredAndRegexLiteralsAreQuoted(){
        var pattern=MessageCatalog.templatePattern("<gray>Paid $<amount> to <player> (saved).");
        assertNotNull(pattern);assertTrue(pattern.matcher("Paid $12.00 to Player (saved).").matches());
        assertFalse(pattern.matcher("someone said Paid $12.00 to Player (saved).").matches());
        assertFalse(pattern.matcher("Paid $12.00 to Player savedX").matches());
    }
}
