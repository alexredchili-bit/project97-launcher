package plz.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import zombie.characters.BodyDamage.BodyDamage;
import zombie.characters.BodyDamage.BodyPartType;
import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoPlayer;
import zombie.core.textures.Texture;
import zombie.network.GameClient;
import zombie.ui.UIElement;
import zombie.ui.UI_BodyPart;

@Mixin(UI_BodyPart.class)
abstract class UI_BodyPartMixin extends UIElement {
    @Unique
    private static final float PLZ_BANDAGE_ALPHA = 0.72F;

    @Shadow
    public BodyPartType bodyPartType;

    @Shadow
    IsoGameCharacter chr;

    @Shadow
    Texture scratchTex;

    @Shadow
    Texture bandageTex;

    @Shadow
    Texture dirtyBandageTex;

    @Shadow
    Texture infectionTex;

    @Shadow
    Texture deepWoundTex;

    @Shadow
    Texture stitchTex;

    @Shadow
    Texture biteTex;

    @Shadow
    Texture glassTex;

    @Shadow
    Texture boneTex;

    @Shadow
    Texture splintTex;

    @Shadow
    Texture burnTex;

    @Shadow
    Texture bulletTex;

    /**
     * @author PLZ
     * @reason Wounds draw under a translucent bandage instead of being hidden by it, which reorders the whole layer stack.
     */
    @Overwrite
    @Override
    public void render() {
        BodyDamage bodyDamage = this.chr.getBodyDamage();
        if (GameClient.client && this.chr instanceof IsoPlayer isoPlayer && !isoPlayer.isLocalPlayer()) {
            bodyDamage = this.chr.getBodyDamageRemote();
        }

        if (this.infectionTex != null && bodyDamage.getBodyPart(this.bodyPartType).getWoundInfectionLevel() > 0.0F) {
            this.DrawTexture(this.infectionTex, 0.0, 0.0, bodyDamage.getBodyPart(this.bodyPartType).getWoundInfectionLevel() / 10.0F);
        }

        this.plz$drawWound(bodyDamage);

        if (this.bandageTex != null && bodyDamage.IsBandaged(this.bodyPartType) && bodyDamage.getBodyPart(this.bodyPartType).getBandageLife() > 0.0F) {
            this.DrawTexture(this.bandageTex, 0.0, 0.0, (double)PLZ_BANDAGE_ALPHA);
        } else if (this.dirtyBandageTex != null
            && bodyDamage.IsBandaged(this.bodyPartType)
            && bodyDamage.getBodyPart(this.bodyPartType).getBandageLife() <= 0.0F) {
            this.DrawTexture(this.dirtyBandageTex, 0.0, 0.0, (double)PLZ_BANDAGE_ALPHA);
        }

        if (this.boneTex != null
            && bodyDamage.getBodyPart(this.bodyPartType).getFractureTime() > 0.0F
            && bodyDamage.getBodyPart(this.bodyPartType).getSplintFactor() == 0.0F) {
            this.DrawTexture(this.boneTex, 0.0, 0.0, 1.0);
        } else if (this.splintTex != null && bodyDamage.getBodyPart(this.bodyPartType).getSplintFactor() > 0.0F) {
            this.DrawTexture(this.splintTex, 0.0, 0.0, 1.0);
        }

        if (this.glassTex != null && bodyDamage.getBodyPart(this.bodyPartType).haveGlass()) {
            this.DrawTexture(this.glassTex, 0.0, 0.0, 1.0);
        }

        if (this.bulletTex != null && bodyDamage.getBodyPart(this.bodyPartType).haveBullet()) {
            this.DrawTexture(this.bulletTex, 0.0, 0.0, 1.0);
        }

        if (this.burnTex != null && bodyDamage.getBodyPart(this.bodyPartType).getBurnTime() > 0.0F) {
            this.DrawTexture(this.burnTex, 0.0, 0.0, bodyDamage.getBodyPart(this.bodyPartType).getBurnTime() / 100.0F);
        }

        super.render();
    }

    @Unique
    private void plz$drawWound(BodyDamage bodyDamage) {
        if (this.scratchTex != null && bodyDamage.IsScratched(this.bodyPartType)) {
            this.DrawTexture(this.scratchTex, 0.0, 0.0, bodyDamage.getBodyPart(this.bodyPartType).getScratchTime() / 20.0F);
        } else if (this.scratchTex != null && bodyDamage.IsCut(this.bodyPartType)) {
            this.DrawTexture(this.scratchTex, 0.0, 0.0, bodyDamage.getBodyPart(this.bodyPartType).getCutTime() / 20.0F);
        } else if (this.biteTex != null
            && bodyDamage.IsBitten(this.bodyPartType)
            && bodyDamage.getBodyPart(this.bodyPartType).getBiteTime() >= 0.0F) {
            this.DrawTexture(this.biteTex, 0.0, 0.0, 1.0);
        } else if (this.deepWoundTex != null && bodyDamage.IsDeepWounded(this.bodyPartType)) {
            this.DrawTexture(this.deepWoundTex, 0.0, 0.0, bodyDamage.getBodyPart(this.bodyPartType).getDeepWoundTime() / 15.0F);
        } else if (this.stitchTex != null && bodyDamage.IsStitched(this.bodyPartType)) {
            this.DrawTexture(this.stitchTex, 0.0, 0.0, 1.0);
        }
    }
}
