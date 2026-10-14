package zombie.plz;

import java.util.ArrayList;
import org.joml.Vector3f;
import zombie.Lua.LuaEventManager;
import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoPlayer;
import zombie.core.math.PZMath;
import zombie.inventory.types.HandWeapon;
import zombie.iso.IsoChunk;
import zombie.network.GameServer;
import zombie.network.ServerMap;
import zombie.scripting.objects.VehicleScript;
import zombie.vehicles.BaseVehicle;
import zombie.vehicles.VehiclePart;

/** Tells Lua when a weapon or another car damages a car. See .claude/PLZ-VEHICLE-PROTECTION.md section 10. */
public final class PLZVehicleDamageWatch {
    /** Fired with (username, vehicleId, partId, weapon, ranged, before, after). */
    public static final String EVENT_HIT = "PLZOnVehicleHit";

    /** Fired with (vehicleId, otherVehicleId, force), only when another car is in contact. */
    public static final String EVENT_CRASH = "PLZOnVehicleCrash";

    /** Bullet has already pushed the two bodies apart by the time a crash is counted. */
    static final float CONTACT_MARGIN = 0.8F;

    private static final float REACH_TILES = 12.0F;

    private static final Vector3f world = new Vector3f();
    private static final Vector3f local = new Vector3f();

    private PLZVehicleDamageWatch() {
    }

    public static void onPartHit(BaseVehicle vehicle, IsoGameCharacter attacker, HandWeapon weapon, VehiclePart part, int before, int after) {
        if (!GameServer.server || vehicle == null || part == null || after >= before) {
            return;
        }

        if (!(attacker instanceof IsoPlayer player) || player.isAnimal()) {
            return;
        }

        // Nothing here may throw: this runs inside packet processing, and an escaping error drops the hit.
        try {
            LuaEventManager.triggerEvent(
                EVENT_HIT,
                player.getUsername(),
                Double.valueOf(vehicle.getId()),
                part.getId(),
                weapon == null ? "?" : weapon.getFullType(),
                Boolean.valueOf(weapon != null && weapon.isRanged()),
                Double.valueOf(before),
                Double.valueOf(after)
            );
        } catch (Throwable ignored) {
        }
    }

    public static void onCrash(BaseVehicle vehicle, float force) {
        if (!GameServer.server || vehicle == null) {
            return;
        }

        try {
            BaseVehicle other = touching(vehicle);
            if (other == null) {
                return;
            }

            LuaEventManager.triggerEvent(
                EVENT_CRASH,
                Double.valueOf(vehicle.getId()),
                Double.valueOf(other.getId()),
                Double.valueOf(Math.abs(force))
            );
        } catch (Throwable ignored) {
        }
    }

    private static BaseVehicle touching(BaseVehicle vehicle) {
        int minX = PZMath.fastfloor((vehicle.getX() - REACH_TILES) / 8.0F);
        int minY = PZMath.fastfloor((vehicle.getY() - REACH_TILES) / 8.0F);
        int maxX = PZMath.fastfloor((vehicle.getX() + REACH_TILES) / 8.0F);
        int maxY = PZMath.fastfloor((vehicle.getY() + REACH_TILES) / 8.0F);
        BaseVehicle best = null;
        float bestDistance = Float.MAX_VALUE;

        for (int cy = minY; cy <= maxY; cy++) {
            for (int cx = minX; cx <= maxX; cx++) {
                IsoChunk chunk = ServerMap.instance.getChunk(cx, cy);
                if (chunk == null) {
                    continue;
                }

                ArrayList<BaseVehicle> vehicles = chunk.vehicles;
                for (int i = 0; i < vehicles.size(); i++) {
                    BaseVehicle other = vehicles.get(i);
                    if (other == vehicle || other == vehicle.getVehicleTowing() || other == vehicle.getVehicleTowedBy()) {
                        continue;
                    }

                    if (Math.abs(other.getZ() - vehicle.getZ()) >= 1.0F) {
                        continue;
                    }

                    float distance = other.DistToSquared(vehicle);
                    if (distance >= bestDistance) {
                        continue;
                    }

                    if (cornersWithin(vehicle, other) || cornersWithin(other, vehicle)) {
                        best = other;
                        bestDistance = distance;
                    }
                }
            }
        }

        return best;
    }

    private static boolean cornersWithin(BaseVehicle box, BaseVehicle other) {
        VehicleScript boxScript = box.getScript();
        VehicleScript otherScript = other.getScript();
        if (boxScript == null || otherScript == null) {
            return false;
        }

        Vector3f boxSize = boxScript.getExtents();
        Vector3f boxCenter = boxScript.getCenterOfMassOffset();
        Vector3f size = otherScript.getExtents();
        Vector3f center = otherScript.getCenterOfMassOffset();

        for (int corner = 0; corner < 4; corner++) {
            float sideX = corner < 2 ? 0.5F : -0.5F;
            float sideZ = corner % 2 == 0 ? 0.5F : -0.5F;
            other.getWorldPos(center.x + size.x * sideX, 0.0F, center.z + size.z * sideZ, world);
            box.getLocalPos(world.x, world.y, world.z, local);
            if (insideBox(local.x, local.z, boxCenter.x, boxCenter.z, boxSize.x, boxSize.z, CONTACT_MARGIN)) {
                return true;
            }
        }

        return false;
    }

    static boolean insideBox(float x, float z, float centerX, float centerZ, float sizeX, float sizeZ, float margin) {
        return Math.abs(x - centerX) <= sizeX * 0.5F + margin && Math.abs(z - centerZ) <= sizeZ * 0.5F + margin;
    }
}
