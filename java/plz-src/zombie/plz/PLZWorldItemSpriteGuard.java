package zombie.plz;

import zombie.debug.DebugLog;
import zombie.iso.objects.IsoWorldInventoryObject;
import zombie.iso.sprite.IsoSprite;
import zombie.iso.sprite.IsoSpriteManager;

public final class PLZWorldItemSpriteGuard {
    private PLZWorldItemSpriteGuard() {
    }

    // An UpdateItemSprite for a runtime-created sprite leaves it null on the client, and render() then throws every frame.
    public static void restoreSprite(IsoWorldInventoryObject object) {
        try {
            object.sprite = IsoSprite.CreateSprite(IsoSpriteManager.instance);
            if (object.getItem() != null) {
                object.updateSprite();
            }

            PLZFixes.hit(PLZFixes.WORLD_ITEM_SPRITE_GUARD);
        } catch (Throwable failure) {
            DebugLog.log("PLZFixes: null-sprite repair failed, vanilla render resumes: " + failure);
        }
    }
}
