package zombie.plz;

import java.nio.ByteBuffer;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryUtil;
import zombie.core.logger.ExceptionLogger;

public final class PLZControllerMappings {
    // GLFW maps no XInput guitar subtype (06 Rock Band, 07 Guitar Hero, 0b bass), and PZ drops what is not a gamepad.
    private static final String GUITAR_LAYOUT = ",a:b0,b:b1,x:b2,y:b3,leftshoulder:b4,rightshoulder:b5,back:b6,start:b7,leftstick:b8,rightstick:b9,leftx:a0,lefty:a1,rightx:a2,righty:a3,lefttrigger:a4,righttrigger:a5,dpup:h0.1,dpright:h0.2,dpdown:h0.4,dpleft:h0.8,platform:Windows,";
    private static final String[][] GUITARS = new String[][]{
        {"78696e70757406000000000000000000", "XInput Guitar"},
        {"78696e70757407000000000000000000", "XInput Guitar Alternate"},
        {"78696e7075740b000000000000000000", "XInput Guitar Bass"}
    };

    private PLZControllerMappings() {
    }

    public static void apply() {
        StringBuilder mappings = new StringBuilder();

        for (String[] guitar : GUITARS) {
            mappings.append(guitar[0]).append(',').append(guitar[1]).append(GUITAR_LAYOUT).append('\n');
        }

        ByteBuffer buffer = MemoryUtil.memUTF8(mappings.toString());

        try {
            GLFW.glfwUpdateGamepadMappings(buffer);
        } catch (Exception ex) {
            ExceptionLogger.logException(ex);
        } finally {
            MemoryUtil.memFree(buffer);
        }
    }
}
