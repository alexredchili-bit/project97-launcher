package zombie.plz;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.HashMap;
import javax.imageio.ImageIO;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryUtil;
import zombie.ZomboidFileSystem;
import zombie.core.opengl.RenderThread;
import zombie.core.skinnedmodel.model.VehicleSubModelInstance;
import zombie.core.textures.Texture;
import zombie.vehicles.BaseVehicle;

public final class PLZVehiclePlate {
    public static final String MODEL_ID = "PLZ_VehiclePlate";
    private static final String MODDATA_KEY = "PLZ_licensePlate";
    private static final int TEX = 256;
    private static final HashMap<String, Texture> plateTextures = new HashMap<>();
    private static final String ART_PATH = "media/textures/PLZ/plz_plate_ky_dealer.png";

    // Pixel grid of that exact cut: re-cutting the cell invalidates every number below.
    private static final int ART_W = 84;
    private static final int ART_H = 33;
    private static final int BAND_LEFT = 5;
    private static final int BAND_RIGHT = 79;
    private static final int BAND_TOP = 7;
    private static final int BAND_BOTTOM = 26;

    // One pressed character height for every plate, so a short override like "PLZ" never runs into the header.
    private static final int NUMBER_H = 10;
    private static final String NUMBER_REF = "HX08";

    private static BufferedImage art;
    private static boolean artLoaded;

    private PLZVehiclePlate() {
    }

    public static Texture texture(VehicleSubModelInstance sub) {
        if (sub.modelInfo == null || sub.modelInfo.scriptModel == null) {
            return null;
        }

        String modelId = sub.modelInfo.scriptModel.id;
        if (modelId == null || !modelId.startsWith(MODEL_ID)) {
            return null;
        }

        if (!(sub.object instanceof BaseVehicle)) {
            return null;
        }

        BaseVehicle vehicle = (BaseVehicle)sub.object;
        if (!vehicle.hasModData()) {
            return null;
        }

        Object raw = vehicle.getModData().rawget(MODDATA_KEY);
        if (!(raw instanceof String)) {
            return null;
        }

        String plate = ((String)raw).trim();
        return plate.isEmpty() ? null : getPlateTexture(plate);
    }

    private static Texture getPlateTexture(String plate) {
        Texture cached = plateTextures.get(plate);
        if (cached != null) {
            return cached;
        }

        final Texture tex = new Texture(TEX, TEX, "plz_plate_" + plate, 0);
        plateTextures.put(plate, tex);
        final int[] argb = renderPlate(plate);
        RenderThread.invokeOnRenderContext(
            () -> {
                GL11.glBindTexture(3553, Texture.lastTextureID = tex.getID());
                GL11.glTexParameteri(3553, 10241, 9729);
                GL11.glTexParameteri(3553, 10240, 9729);
                ByteBuffer pixels = MemoryUtil.memAlloc(argb.length * 4);

                for (int i = 0; i < argb.length; i++) {
                    int p = argb[i];
                    pixels.put((byte)(p >> 16 & 0xFF));
                    pixels.put((byte)(p >> 8 & 0xFF));
                    pixels.put((byte)(p & 0xFF));
                    pixels.put((byte)(p >>> 24 & 0xFF));
                }

                pixels.flip();
                GL11.glTexImage2D(3553, 0, 6408, TEX, TEX, 0, 6408, 5121, pixels);
                MemoryUtil.memFree(pixels);
            }
        );
        return tex;
    }

    // Loaded once: a missing file fails once, not once per plate.
    private static synchronized BufferedImage plateArt() {
        if (artLoaded) {
            return art;
        }

        artLoaded = true;
        try {
            String path = ZomboidFileSystem.instance.getString(ART_PATH);
            File file = new File(path);
            if (file.isFile()) {
                art = ImageIO.read(file);
            }
        } catch (Exception e) {
            art = null;
        }

        return art;
    }

    private static int[] renderPlate(String plate) {
        BufferedImage img = new BufferedImage(TEX, TEX, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        // The 0.5 x 0.25 quad squashes this square texture back to 2:1, so text is laid out in 512x256.
        BufferedImage plateArt = plateArt();
        double bandX;
        double bandY;
        double bandW;
        double bandH;
        double numberH;

        if (plateArt != null) {
            g.drawImage(plateArt, 0, 0, TEX, TEX, null);
            g.scale(0.5, 1.0);

            double sx = 512.0 / ART_W;
            double sy = 256.0 / ART_H;
            bandX = BAND_LEFT * sx;
            bandW = (BAND_RIGHT - BAND_LEFT) * sx;
            bandY = BAND_TOP * sy;
            bandH = (BAND_BOTTOM - BAND_TOP) * sy;
            numberH = NUMBER_H * sy;
        } else {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, TEX, TEX);
            g.scale(0.5, 1.0);
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, 512, 16);
            g.fillRect(0, 240, 512, 16);
            g.fillRect(0, 0, 16, 256);
            g.fillRect(496, 0, 16, 256);

            bandX = 41.0;
            bandW = 430.0;
            bandY = 16.0;
            bandH = 224.0;
            numberH = 100.0;
        }

        // Ink boxes, not line boxes: a line box reserves descender room no plate uses and rides high.
        FontRenderContext frc = g.getFontRenderContext();
        Font font = new Font(Font.SANS_SERIF, Font.BOLD, 100);
        Rectangle2D ref = font.createGlyphVector(frc, NUMBER_REF).getVisualBounds();
        Rectangle2D ink = font.createGlyphVector(frc, plate).getVisualBounds();
        if (ink.getWidth() <= 0.0 || ref.getHeight() <= 0.0) {
            g.dispose();
            return pixels(img);
        }

        double fit = Math.min(bandW / ink.getWidth(), numberH / ref.getHeight());
        font = font.deriveFont((float)(100.0 * fit * 0.98));
        ref = font.createGlyphVector(frc, NUMBER_REF).getVisualBounds();
        ink = font.createGlyphVector(frc, plate).getVisualBounds();

        g.setFont(font);
        g.setColor(Color.BLACK);
        g.drawString(plate,
            (float)(bandX + (bandW - ink.getWidth()) / 2.0 - ink.getX()),
            (float)(bandY + (bandH - ref.getHeight()) / 2.0 - ref.getY()));
        g.dispose();

        return pixels(img);
    }

    private static int[] pixels(BufferedImage img) {
        int[] out = new int[TEX * TEX];
        img.getRGB(0, 0, TEX, TEX, out, 0, TEX);
        return out;
    }
}
