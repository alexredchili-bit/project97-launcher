package zombie.plz;

import java.nio.ByteBuffer;
import se.krka.kahlua.integration.LuaReturn;
import se.krka.kahlua.vm.KahluaTable;
import zombie.Lua.LuaManager;
import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoPlayer;
import zombie.debug.DebugLog;
import zombie.erosion.ErosionData;
import zombie.erosion.ErosionMain;
import zombie.erosion.ErosionRegions;
import zombie.erosion.categories.ErosionCategory;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoWorld;
import zombie.iso.objects.IsoTree;
import zombie.network.GameClient;
import zombie.network.GameServer;

public final class PLZTreeGate {
    private PLZTreeGate() {
    }

    // Logging zones: Lua decides, and an error lets the swing through as vanilla would.
    public static boolean refusesFell(IsoTree tree, IsoGameCharacter owner) {
        IsoGridSquare square = tree.square;
        if (!GameServer.server || !(owner instanceof IsoPlayer) || square == null || !PLZFixes.on(PLZFixes.TREE_FELL_GATE)) {
            return false;
        }

        try {
            Object gate = LuaManager.env == null ? null : LuaManager.env.rawget("ForestryServer");
            Object function = gate instanceof KahluaTable table ? table.rawget("mayHit") : null;
            if (function == null) {
                return false;
            }

            LuaReturn result = LuaManager.caller.protectedCall(
                LuaManager.thread, function, owner, (double) square.getX(), (double) square.getY(), (double) square.getZ()
            );
            if (!result.isSuccess() || !Boolean.FALSE.equals(result.getFirst())) {
                return false;
            }

            PLZFixes.hit(PLZFixes.TREE_FELL_GATE);
            return true;
        } catch (Throwable failure) {
            DebugLog.log("IsoTree: logging gate let a swing through after " + failure);
            return false;
        }
    }

    // A planted tree becomes erosion's own, so it keeps its species' seasons and growth.
    public static boolean adoptIntoErosion(IsoTree tree, int species, int stage, int maxStage) {
        IsoGridSquare square = tree.square;
        if (GameClient.client || square == null || square.getZ() != 0 || square.getChunk() == null) {
            return false;
        }

        ErosionData.Square erosion = square.getErosionData();
        if (erosion == null || !erosion.init || species < 0 || species > 10) {
            return false;
        }

        int top = Math.max(0, Math.min(7, maxStage));
        int at = Math.max(0, Math.min(top, stage));
        try {
            erosion.doNothing = false;
            erosion.regions.removeIf(data -> data.regionId == 0 && data.categoryId == 0);
            int eTick = ErosionMain.getInstance().getEtick();
            int spawnTime = eTick - (int) Math.ceil(at * (60.0F / (top + 1.0F)));
            int flags = 2;
            if (at == 1) {
                flags |= 8;
            } else if (at == 2) {
                flags |= 16;
            } else if (at == 3) {
                flags |= 32;
            } else if (at == 4) {
                flags |= 64;
            } else if (at > 4) {
                flags |= 128;
            }

            ByteBuffer bb = ByteBuffer.allocate(16);
            bb.put((byte) 0).put((byte) 0).put((byte) -1).put((byte) flags);
            if (at > 4) {
                bb.put((byte) at);
            }

            bb.put((byte) species).put((byte) top).putShort((short) spawnTime);
            bb.flip();
            ErosionCategory.Data data = ErosionCategory.loadCategoryData(bb, IsoWorld.getWorldVersion());
            erosion.regions.add(data);
            ErosionRegions.getCategory(0, 0).update(square, erosion, data, square.getChunk().getErosionData(), eTick);
            return erosion.regions.contains(data);
        } catch (Throwable failure) {
            DebugLog.log("IsoTree: could not hand a planted tree to erosion: " + failure);
            return false;
        }
    }
}
