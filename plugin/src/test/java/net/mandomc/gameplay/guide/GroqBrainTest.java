package net.mandomc.gameplay.guide;
import org.junit.Test;
import static org.junit.Assert.*;
public class GroqBrainTest {
    @Test public void providerResetIntervalsRespectSecondsAndMixedUnits(){
        assertEquals(86400,GroqBrain.resetDelay("1m26.4s"));
        assertEquals(3390,GroqBrain.resetDelay("3.39s"));
        assertEquals(60000,GroqBrain.resetDelay("unknown"));
    }
}
