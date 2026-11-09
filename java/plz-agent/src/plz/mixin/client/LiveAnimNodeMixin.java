package plz.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import zombie.core.skinnedmodel.advancedanimation.LiveAnimNode;

@Mixin(LiveAnimNode.class)
public interface LiveAnimNodeMixin {
    @Accessor("nodeAnimTime")
    void plz$setNodeAnimTime(float nodeAnimTime);
}
