package zombie.plz;

import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import zombie.core.Core;
import zombie.core.SpriteRenderer;
import zombie.core.math.PZMath;
import zombie.core.opengl.GLStateRenderThread;
import zombie.core.opengl.VBORenderer;
import zombie.core.sprite.SpriteRenderState;
import zombie.core.textures.Texture;
import zombie.core.textures.TextureDraw;
import zombie.core.textures.VideoTexture;
import zombie.iso.IsoDepthHelper;
import zombie.iso.PlayerCamera;

final class PLZVideoTextureScreenDrawer extends TextureDraw.GenericDrawer {
    final float[] quads = new float[14];
    VideoTexture tex;
    int level;
    float frame;

    void setQuad(int q, float lx, float ly, float rx, float ry, float bottom, float top, float bias) {
        int o = q * 7;
        this.quads[o] = lx;
        this.quads[o + 1] = ly;
        this.quads[o + 2] = rx;
        this.quads[o + 3] = ry;
        this.quads[o + 4] = bottom;
        this.quads[o + 5] = top;
        this.quads[o + 6] = bias;
    }

    @Override
    public void render() {
        SpriteRenderState renderState = SpriteRenderer.instance.getRenderingState();
        PlayerCamera camera = renderState.playerCamera[renderState.playerIndex];
        float rcx = camera.rightClickX;
        float rcy = camera.rightClickY;
        float tox = camera.getTOffX();
        float toy = camera.getTOffY();
        float playerX = Core.getInstance().floatParamMap.get(0);
        float playerY = Core.getInstance().floatParamMap.get(1);
        float playerZ = Core.getInstance().floatParamMap.get(2);
        float cx = playerX - camera.XToIso(-tox - rcx, -toy - rcy, 0.0F) + camera.deferedX;
        float cy = playerY - camera.YToIso(-tox - rcx, -toy - rcy, 0.0F) + camera.deferedY;
        float screenWidth = camera.offscreenWidth / 1920.0F;
        float screenHeight = camera.offscreenHeight / 1920.0F;
        Matrix4f projection = Core.getInstance().projectionMatrixStack.alloc();
        projection.setOrtho(-screenWidth / 2.0F, screenWidth / 2.0F, -screenHeight / 2.0F, screenHeight / 2.0F, -10.0F, 10.0F);
        Core.getInstance().projectionMatrixStack.push(projection);
        GL11.glEnable(2929);
        GL11.glDepthFunc(515);
        GL11.glDepthMask(true);
        GL11.glBlendFunc(770, 771);
        this.renderQuad(0, null, this.frame, playerX, playerY, playerZ, cx, cy);
        if (this.tex != null) {
            this.renderQuad(1, this.tex, 1.0F, playerX, playerY, playerZ, cx, cy);
        }
        VBORenderer.getInstance().flush();
        Core.getInstance().projectionMatrixStack.pop();
        GLStateRenderThread.restore();
    }

    private void renderQuad(int q, VideoTexture texture, float bright, float playerX, float playerY, float playerZ, float cx, float cy) {
        int o = q * 7;
        float lx = this.quads[o];
        float ly = this.quads[o + 1];
        float rx = this.quads[o + 2];
        float ry = this.quads[o + 3];
        float bottom = this.quads[o + 4];
        float top = this.quads[o + 5];
        float bias = this.quads[o + 6];
        int px = PZMath.fastfloor(playerX);
        int py = PZMath.fastfloor(playerY);
        float dLT = IsoDepthHelper.getSquareDepthData(px, py, lx, ly, top).depthStart + bias;
        float dRT = IsoDepthHelper.getSquareDepthData(px, py, rx, ry, top).depthStart + bias;
        float dRB = IsoDepthHelper.getSquareDepthData(px, py, rx, ry, bottom).depthStart + bias;
        float dLB = IsoDepthHelper.getSquareDepthData(px, py, lx, ly, bottom).depthStart + bias;
        float ox = (lx + rx) / 2.0F;
        float oy = (ly + ry) / 2.0F;
        float yTop = (top - this.level) * PLZCinema.LEVEL_UNITS;
        float yBottom = (bottom - this.level) * PLZCinema.LEVEL_UNITS;
        Matrix4f modelView = Core.getInstance().modelViewMatrixStack.alloc();
        modelView.scaling(Core.scale);
        modelView.scale(Core.tileScale / 2.0F);
        modelView.rotate((float)(Math.PI / 6), 1.0F, 0.0F, 0.0F);
        modelView.rotate((float)(Math.PI * 3.0 / 4.0), 0.0F, 1.0F, 0.0F);
        modelView.translate(-(ox - cx), (this.level - playerZ) * PLZCinema.LEVEL_UNITS, -(oy - cy));
        modelView.scale(-1.0F, 1.0F, -1.0F);
        modelView.translate(0.0F, -0.71999997F, 0.0F);
        VBORenderer vbor = VBORenderer.getInstance();
        vbor.cmdPushAndLoadMatrix(5888, modelView);
        vbor.startRun(vbor.formatPositionColorUvDepth);
        vbor.setMode(7);
        vbor.setDepthTest(true);
        if (texture != null) {
            vbor.setTextureID(texture.getTextureId());
            vbor.setMinMagFilters(9729, 9729);
        } else {
            vbor.setTextureID(Texture.getWhite().getTextureId());
        }

        vbor.addQuadDepth(
            lx - ox, yTop, ly - oy, 0.0F, 0.0F, dLT,
            rx - ox, yTop, ry - oy, 1.0F, 0.0F, dRT,
            rx - ox, yBottom, ry - oy, 1.0F, 1.0F, dRB,
            lx - ox, yBottom, ly - oy, 0.0F, 1.0F, dLB,
            bright, bright, bright, 1.0F
        );
        vbor.endRun();
        vbor.cmdPopMatrix(5888);
    }

    @Override
    public void postRender() {
        this.tex = null;
        PLZCinema.screenDrawerPool.offer(this);
    }
}
