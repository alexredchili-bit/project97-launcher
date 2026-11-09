package zombie.plz;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import zombie.iso.weather.fx.IsoWeatherFX;

public final class PLZWeatherFX {
    // Both fields are package-private, and the signed 42.22.1 jar refuses our classes inside zombie.iso.weather.fx.
    private static final VarHandle INSTANCE = handle("instance", IsoWeatherFX.class);
    private static final VarHandle DEBUG_BOUNDS = handle("debugBounds", boolean.class);

    private PLZWeatherFX() {
    }

    public static IsoWeatherFX instance() {
        return (IsoWeatherFX)INSTANCE.get();
    }

    public static boolean debugBounds() {
        return (boolean)DEBUG_BOUNDS.get();
    }

    private static VarHandle handle(String name, Class<?> type) {
        try {
            return MethodHandles.privateLookupIn(IsoWeatherFX.class, MethodHandles.lookup()).findStaticVarHandle(IsoWeatherFX.class, name, type);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
