package net.mandomc.commands;
import org.junit.Test;
import static org.junit.Assert.*;
public class ItemQuantityTest {
    @Test public void quantitiesAreBoundedWholeItems() {
        assertEquals(1,ItemCommand.quantity("1"));assertEquals(2304,ItemCommand.quantity("2304"));
        for(String input:new String[]{"0","-1","2305","999999999999999","NaN","1.5","1e2","+1"})
            assertThrows(IllegalArgumentException.class,()->ItemCommand.quantity(input));
    }
}
