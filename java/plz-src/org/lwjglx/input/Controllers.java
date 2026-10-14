package org.lwjglx.input;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.ByteBuffer;
import java.util.function.Consumer;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryUtil;
import zombie.ZomboidFileSystem;
import zombie.core.Core;
import zombie.core.logger.ExceptionLogger;

public class Controllers {
   public static final int MAX_AXES = 6;
   public static final int MAX_BUTTONS = 15;
   public static final int MAX_CONTROLLERS = 16;
   private static final Controller[] controllers = new Controller[16];
   private static boolean isCreated;
   private static Consumer<Integer> controllerConnectedCallback;
   private static Consumer<Integer> controllerDisconnectedCallback;
   private static int debugToggleControllerPluggedIn = -1;

   public static void create() {
      readGameControllerDB();
      GLFW.glfwSetJoystickCallback(Controllers::updateControllersCount);

      for (int i = 0; i < 16; i++) {
         if (GLFW.glfwJoystickPresent(i)) {
            controllers[i] = new Controller(i);
         }
      }

      isCreated = true;
   }

   private static void readGameControllerDB() {
      File file = new File("./media/gamecontrollerdb.txt").getAbsoluteFile();
      if (file.exists()) {
         readGameControllerDB(file);
      }

      applyPLZMappings();
      String path = ZomboidFileSystem.instance.getCacheDirSub("joypads" + File.separator + "gamecontrollerdb.txt");
      file = new File(path);
      if (file.exists()) {
         readGameControllerDB(file);
      }
   }

   // GLFW maps no XInput guitar subtype (06 Rock Band, 07 Guitar Hero, 0b bass), and PZ drops what is not a gamepad.
   private static final String PLZ_GUITAR_LAYOUT = ",a:b0,b:b1,x:b2,y:b3,leftshoulder:b4,rightshoulder:b5,back:b6,start:b7,leftstick:b8,rightstick:b9,leftx:a0,lefty:a1,rightx:a2,righty:a3,lefttrigger:a4,righttrigger:a5,dpup:h0.1,dpright:h0.2,dpdown:h0.4,dpleft:h0.8,platform:Windows,";
   private static final String[][] PLZ_GUITARS = new String[][]{
      {"78696e70757406000000000000000000", "XInput Guitar"},
      {"78696e70757407000000000000000000", "XInput Guitar Alternate"},
      {"78696e7075740b000000000000000000", "XInput Guitar Bass"}
   };

   private static void applyPLZMappings() {
      StringBuilder mappings = new StringBuilder();

      for (String[] guitar : PLZ_GUITARS) {
         mappings.append(guitar[0]).append(',').append(guitar[1]).append(PLZ_GUITAR_LAYOUT).append('\n');
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

   private static void readGameControllerDB(File file) {
      try (
         FileReader fileReader = new FileReader(file);
         BufferedReader bufferedReader = new BufferedReader(fileReader);
      ) {
         StringBuilder stringBuilder = new StringBuilder();

         String line;
         while ((line = bufferedReader.readLine()) != null) {
            if (!line.startsWith("#")) {
               stringBuilder.append(line);
               stringBuilder.append(System.lineSeparator());
            }
         }

         ByteBuffer pathBuf = MemoryUtil.memUTF8(stringBuilder.toString());
         if (GLFW.glfwUpdateGamepadMappings(pathBuf)) {
         }

         MemoryUtil.memFree(pathBuf);
      } catch (Exception ex) {
         ExceptionLogger.logException(ex);
      }
   }

   public static void setControllerConnectedCallback(Consumer<Integer> controllerConnectedCallback) {
      Controllers.controllerConnectedCallback = controllerConnectedCallback;
   }

   public static void setControllerDisconnectedCallback(Consumer<Integer> controllerDisconnectedCallback) {
      Controllers.controllerDisconnectedCallback = controllerDisconnectedCallback;
   }

   public static int getControllerCount() {
      if (!isCreated()) {
         throw new RuntimeException("Before calling 'getJoypadCount()' you should call 'create()' method");
      } else {
         return controllers.length;
      }
   }

   public static Controller getController(int i) {
      if (!isCreated()) {
         throw new RuntimeException("Before calling 'getJoypad(int)' you should call 'create()' method");
      } else {
         return controllers[i];
      }
   }

   public static boolean isCreated() {
      return isCreated;
   }

   public static void poll(GamepadState[] gamepadStates) {
      if (!isCreated()) {
         throw new RuntimeException("Before calling 'poll()' you should call 'create()' method");
      }

      if (Core.debug && debugToggleControllerPluggedIn >= 0 && debugToggleControllerPluggedIn < 16) {
         int index = debugToggleControllerPluggedIn;
         debugToggleControllerPluggedIn = -1;
         if (controllers[index] != null) {
            updateControllersCount(index, 262146);
         } else if (GLFW.glfwJoystickIsGamepad(index)) {
            updateControllersCount(index, 262145);
         }
      }

      for (int i = 0; i < controllers.length; i++) {
         Controller controller = controllers[i];
         if (controller != null) {
            controller.poll(gamepadStates[i]);
         }
      }
   }

   private static void updateControllersCount(int joystickID, int event) {
      if (event == 262145) {
         Controller pluggedController = new Controller(joystickID);
         controllers[joystickID] = pluggedController;
         if (controllerConnectedCallback != null) {
            controllerConnectedCallback.accept(joystickID);
         }
      } else if (event == 262146) {
         controllers[joystickID] = null;
         if (controllerDisconnectedCallback != null) {
            controllerDisconnectedCallback.accept(joystickID);
         }
      }
   }

   public static void setDebugToggleControllerPluggedIn(int index) {
      debugToggleControllerPluggedIn = index;
   }
}
