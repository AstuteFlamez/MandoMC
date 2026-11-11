package net.mandomc.gameplay.items;
import net.mandomc.gameplay.ilum.IlumTimeFormat;
import org.junit.Test;
import static org.junit.Assert.*;
public class IlumTimeFormatTest {
    @Test public void oneDecimalWithCorrectMinuteCarryAndStableLocale() {
        assertEquals("0:00.0", IlumTimeFormat.format(0)); assertEquals("0:01.2", IlumTimeFormat.format(1234));
        assertEquals("1:00.0", IlumTimeFormat.format(59999)); assertEquals("1:01.1", IlumTimeFormat.format(61050));
        var old=java.util.Locale.getDefault(); try {java.util.Locale.setDefault(java.util.Locale.GERMANY);assertEquals("0:01.2",IlumTimeFormat.format(1234));}finally{java.util.Locale.setDefault(old);}
    }
}
