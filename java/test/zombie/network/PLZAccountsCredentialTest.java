package zombie.network;

import java.util.Objects;

public class PLZAccountsCredentialTest {
    static int fails = 0;

    static void check(String what, Object got, Object want) {
        boolean ok = Objects.equals(String.valueOf(got), String.valueOf(want));
        System.out.println((ok ? "  PASS  " : "  FAIL  ") + what + "  got=" + got + " want=" + want);
        if (!ok) fails++;
    }

    static void checkTrue(String what, boolean got) {
        check(what, got, true);
    }

    static long ms(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    public static void main(String[] a) throws Exception {
        String first = "76561198000000001";
        String second = "76561198000000002";
        String third = "76561198000000003";

        System.out.println("[1] the cached credential is byte-identical to the uncached hash");
        String direct = PLZAccounts.computeCredential(first);
        long t0 = System.nanoTime();
        String viaCache = PLZAccounts.credentialFor(first);
        long coldMs = ms(t0);
        check("cold credentialFor == computeCredential", viaCache, direct);
        checkTrue("credential is a BCrypt cost-12 hash", viaCache.startsWith("$2a$12$"));
        check("different SteamIDs differ", PLZAccounts.computeCredential(second).equals(direct), false);

        System.out.println("[2] a repeat login for the same SteamID costs no hash");
        t0 = System.nanoTime();
        String warm = PLZAccounts.credentialFor(first);
        long warmMs = ms(t0);
        check("warm == cold", warm, viaCache);
        checkTrue("warm lookup under 5 ms (was " + warmMs + " ms, cold " + coldMs + " ms)", warmMs < 5);

        System.out.println("[3] prefetch hashes off the calling thread, then login is free");
        t0 = System.nanoTime();
        PLZAccounts.prefetchCredential(second);
        long prefetchCallMs = ms(t0);
        checkTrue("prefetch returns immediately (" + prefetchCallMs + " ms)", prefetchCallMs < 20);
        long deadline = System.currentTimeMillis() + 5000L;
        while (!PLZAccounts.isCached(second) && System.currentTimeMillis() < deadline) {
            Thread.sleep(10L);
        }
        checkTrue("background hash finished", PLZAccounts.isCached(second));
        t0 = System.nanoTime();
        String prefetched = PLZAccounts.credentialFor(second);
        long afterPrefetchMs = ms(t0);
        check("prefetched == uncached", prefetched, PLZAccounts.computeCredential(second));
        checkTrue("login after prefetch under 5 ms (" + afterPrefetchMs + " ms)", afterPrefetchMs < 5);

        System.out.println("[4] login racing an unfinished prefetch still gets the right answer");
        PLZAccounts.prefetchCredential(third);
        String raced = PLZAccounts.credentialFor(third);
        check("raced == uncached", raced, PLZAccounts.computeCredential(third));

        System.out.println("[5] junk input never throws");
        PLZAccounts.prefetchCredential(null);
        PLZAccounts.prefetchCredential("");
        checkTrue("null/empty prefetch ignored", !PLZAccounts.isCached(""));

        System.out.println(fails == 0 ? "ALL PASS" : fails + " FAILED");
        System.exit(fails == 0 ? 0 : 1);
    }
}
