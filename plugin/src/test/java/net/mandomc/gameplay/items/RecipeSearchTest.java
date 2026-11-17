package net.mandomc.gameplay.items;
import org.junit.Test;
import static org.junit.Assert.*;
public class RecipeSearchTest {
    @Test public void namesAndIdsMatchPartialWordsWithoutCaseOrPunctuation(){
        assertTrue(RecipeSearch.matches("ee 3","ee3","EE-3"));
        assertTrue(RecipeSearch.matches("BESKAR chest","beskar_chestplate","Beskar Chestplate"));
        assertTrue(RecipeSearch.matches("x wing","xwing","X-Wing"));
        assertFalse(RecipeSearch.matches("beskar helmet","beskar_boots","Beskar Boots"));
        assertTrue(RecipeSearch.matches("","ee3","EE-3"));
    }
}
