package net.mandomc.redstone;

import java.lang.instrument.Instrumentation;

/** Keeps installed launcher and agent entry points compatible while Paper runs native redstone. */
public final class RedstoneAgent {
    private RedstoneAgent() { }

    public static void agentmain(String arguments, Instrumentation instrumentation) {
        premain(arguments, instrumentation);
    }

    public static void premain(String arguments, Instrumentation instrumentation) {
        System.setProperty("mandomc.redstone.agent", "native");
    }
}
