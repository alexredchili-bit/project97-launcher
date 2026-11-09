package zombie.plz.access;

public interface PLZEventAccess {
    long[] plzNanos();

    long[] plzCalls();

    void plzResetCounters();
}
