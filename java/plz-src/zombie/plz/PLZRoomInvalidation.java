package zombie.plz;

// Lets FBORenderCell see room invalidations made inside the forEach lambdas of checkBlackedOutRooms.
public final class PLZRoomInvalidation {
    public static boolean armed;
    public static boolean fired;

    private PLZRoomInvalidation() {
    }

    public static void arm() {
        armed = true;
        fired = false;
    }

    public static boolean disarm() {
        armed = false;
        return fired;
    }

    public static void invalidated() {
        if (armed) {
            fired = true;
        }
    }
}
