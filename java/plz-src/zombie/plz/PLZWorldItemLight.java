package zombie.plz;

import org.joml.Vector3f;

public final class PLZWorldItemLight {
    private static final boolean FOLLOW_ROTATION = !"false".equals(System.getProperty("plz.worldItemLightFollowsRotation"));

    public static final float DEFAULT_GAIN = 1.0F;
    public static final float MIN_GAIN = 0.25F;
    public static final float MAX_GAIN = 4.0F;

    private static volatile float gain = clamp(readProperty());

    private PLZWorldItemLight() {
    }

    public static float getGain() {
        return gain;
    }

    public static void setGain(float value) {
        gain = clamp(value);
    }

    public static void orientLight(Vector3f pos, float degX, float degY, float degZ) {
        if (!FOLLOW_ROTATION) {
            pos.rotateY((float)Math.toRadians(degY));
            return;
        }

        pos.rotateX((float)Math.toRadians(-degX)).rotateY((float)Math.toRadians(-degY)).rotateZ((float)Math.toRadians(-degZ));
        pos.x = -pos.x;
    }

    private static float readProperty() {
        String raw = System.getProperty("plz.worldItemLight");
        if (raw == null) {
            return DEFAULT_GAIN;
        }

        try {
            return Float.parseFloat(raw.trim());
        } catch (NumberFormatException ignored) {
            return DEFAULT_GAIN;
        }
    }

    private static float clamp(float value) {
        if (Float.isNaN(value)) {
            return DEFAULT_GAIN;
        }

        return Math.max(MIN_GAIN, Math.min(MAX_GAIN, value));
    }
}
