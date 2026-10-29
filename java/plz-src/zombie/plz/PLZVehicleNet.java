package zombie.plz;

import gnu.trove.iterator.TIntLongIterator;
import gnu.trove.iterator.TShortLongIterator;
import gnu.trove.map.hash.TIntLongHashMap;
import gnu.trove.map.hash.TShortLongHashMap;
import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;
import zombie.characters.IsoPlayer;
import zombie.core.network.ByteBufferWriter;
import zombie.core.raknet.UdpConnection;
import zombie.debug.DebugType;
import zombie.debug.LogSeverity;
import zombie.inventory.ItemContainer;
import zombie.network.GameServer;
import zombie.network.PacketTypes;
import zombie.network.packets.INetworkPacket;
import zombie.vehicles.BaseVehicle;
import zombie.vehicles.VehicleManager;
import zombie.vehicles.VehiclePart;

/** Server-side vehicle traffic shaping per connection. See java-patch/README.md, "PLZVehicleNet". */
public final class PLZVehicleNet {
    static final long HOLD_MS = 2500L;
    static final long KNOWN_MS = 3000L;
    static final long FORGET_MS = 30000L;
    static final long SERVE_GAP_MS = 2000L;
    static final float NEAR_TILES = 40.0F;
    static final float LEAN_TILES = 20.0F;
    public static final int FULLS_PER_TICK = 3;
    private static final long PUSH_EVERY_MS = 500L;
    private static final long PRUNE_EVERY_MS = 10000L;
    private static final int IMMEDIATE_PRIORITY = 0;

    private static final Map<UdpConnection, Ledger> ledgers = new WeakHashMap<>();
    private static volatile Thread leanThread;
    private static final ArrayList<BaseVehicle> sortBuffer = new ArrayList<>();

    private PLZVehicleNet() {
    }

    static final class Ledger {
        private final TShortLongHashMap granted = new TShortLongHashMap();
        private final TShortLongHashMap known = new TShortLongHashMap();
        private final TShortLongHashMap owed = new TShortLongHashMap();
        private final TIntLongHashMap served = new TIntLongHashMap();
        private long pushedAt;
        private long prunedAt;

        synchronized void noteKnown(short id, long now) {
            this.known.put(id, now);
        }

        synchronized boolean grantFull(short id, long now) {
            long at = this.granted.get(id);
            if (at != 0L && now - at < HOLD_MS && this.known.get(id) < at) {
                return false;
            }

            this.granted.put(id, now);
            return true;
        }

        synchronized void fullSent(short id, boolean lean, long now) {
            if (lean) {
                this.owed.put(id, now);
            } else {
                this.owed.remove(id);
            }
        }

        synchronized boolean isOwed(short id) {
            return this.owed.containsKey(id);
        }

        synchronized boolean knowsSinceLean(short id, long now) {
            long seen = this.known.get(id);
            return seen != 0L && now - seen < KNOWN_MS && seen >= this.owed.get(id);
        }

        synchronized void settle(short id) {
            this.owed.remove(id);
        }

        synchronized boolean mayServe(int key, long now) {
            long at = this.served.get(key);
            if (at != 0L && now - at < SERVE_GAP_MS) {
                return false;
            }

            this.served.put(key, now);
            return true;
        }

        synchronized boolean pushDue(long now) {
            if (now - this.pushedAt < PUSH_EVERY_MS) {
                return false;
            }

            this.pushedAt = now;
            return !this.owed.isEmpty();
        }

        synchronized short[] owedIds() {
            return this.owed.keys();
        }

        synchronized void pruneIfDue(long now) {
            if (now - this.prunedAt < PRUNE_EVERY_MS) {
                return;
            }

            this.prunedAt = now;
            drop(this.granted, now, HOLD_MS);
            TIntLongIterator served = this.served.iterator();
            while (served.hasNext()) {
                served.advance();
                if (now - served.value() >= SERVE_GAP_MS) {
                    served.remove();
                }
            }

            TShortLongIterator it = this.owed.iterator();
            while (it.hasNext()) {
                it.advance();
                long seen = this.known.get(it.key());
                if (now - it.value() >= FORGET_MS && (seen == 0L || now - seen >= FORGET_MS)) {
                    it.remove();
                }
            }

            drop(this.known, now, FORGET_MS);
        }

        private static void drop(TShortLongHashMap map, long now, long ageMs) {
            TShortLongIterator it = map.iterator();
            while (it.hasNext()) {
                it.advance();
                if (now - it.value() >= ageMs) {
                    it.remove();
                }
            }
        }
    }

    private static Ledger ledger(UdpConnection connection) {
        synchronized (ledgers) {
            return ledgers.computeIfAbsent(connection, c -> new Ledger());
        }
    }

    public static void noteKnown(UdpConnection connection, short vehicleId) {
        ledger(connection).noteKnown(vehicleId, System.currentTimeMillis());
    }

    /** False while a full save for this car is already on its way to this client. */
    public static boolean grantFull(UdpConnection connection, short vehicleId) {
        return ledger(connection).grantFull(vehicleId, System.currentTimeMillis());
    }

    public static void beginLean() {
        leanThread = Thread.currentThread();
    }

    public static void endLean() {
        leanThread = null;
    }

    /** Thread-confined so a world save running elsewhere never writes an emptied container. */
    public static boolean leanWriting() {
        return leanThread == Thread.currentThread();
    }

    public static boolean shouldLean(UdpConnection connection, BaseVehicle vehicle) {
        if (isOccupant(connection, vehicle) || nearestSq(connection, vehicle.getX(), vehicle.getY()) <= LEAN_TILES * LEAN_TILES) {
            return false;
        }

        return hasContents(vehicle);
    }

    public static void fullSent(UdpConnection connection, short vehicleId, boolean lean) {
        ledger(connection).fullSent(vehicleId, lean, System.currentTimeMillis());
    }

    public static void pushOwed(UdpConnection connection) {
        Ledger ledger = ledger(connection);
        long now = System.currentTimeMillis();
        ledger.pruneIfDue(now);
        if (!ledger.pushDue(now)) {
            return;
        }

        for (short id : ledger.owedIds()) {
            BaseVehicle vehicle = VehicleManager.instance.getVehicleByID(id);
            if (vehicle == null) {
                ledger.settle(id);
            } else if (ledger.knowsSinceLean(id, now)
                && (isOccupant(connection, vehicle) || nearestSq(connection, vehicle.getX(), vehicle.getY()) <= LEAN_TILES * LEAN_TILES)) {
                ledger.settle(id);
                sendContents(connection, vehicle);
                PLZFixes.hit(PLZFixes.VEHICLE_LEAN_FULL);
            }
        }
    }

    /** Answers a client opening a container it only has the lean copy of. */
    public static boolean serveRequested(UdpConnection connection, BaseVehicle vehicle, ItemContainer container) {
        if (container.getItems().isEmpty() || !connection.isRelevantTo(vehicle.getX(), vehicle.getY())) {
            return false;
        }

        int part = container.vehiclePart == null ? 255 : container.vehiclePart.getIndex() & 255;
        if (!ledger(connection).mayServe(vehicle.vehicleId << 8 | part, System.currentTimeMillis())) {
            return false;
        }

        INetworkPacket.send(connection, PacketTypes.PacketType.AddInventoryItemToContainer, container, container.getItems());
        return true;
    }

    public static void sortNearestFirst(UdpConnection connection, ArrayList<BaseVehicle> vehicles) {
        if (vehicles.size() < 2) {
            return;
        }

        sortBuffer.clear();
        sortBuffer.addAll(vehicles);
        float[] dist = new float[sortBuffer.size()];
        for (int i = 0; i < dist.length; i++) {
            BaseVehicle v = sortBuffer.get(i);
            dist[i] = isOccupant(connection, v) ? -1.0F : nearestSq(connection, v.getX(), v.getY());
        }

        Integer[] order = new Integer[dist.length];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }

        java.util.Arrays.sort(order, (a, b) -> Float.compare(dist[a], dist[b]));
        vehicles.clear();
        for (Integer i : order) {
            vehicles.add(sortBuffer.get(i));
        }

        sortBuffer.clear();
    }

    /** Relays driver physics, jumping the queue for the car's own riders and anyone near it. */
    public static void relayPhysics(INetworkPacket packet, PacketTypes.PacketType type, UdpConnection from, BaseVehicle vehicle, float x, float y) {
        boolean counted = false;
        for (UdpConnection connection : GameServer.udpEngine.connections) {
            if ((from == null || connection.getConnectedGUID() != from.getConnectedGUID())
                && connection.isFullyConnected()
                && connection.isRelevantTo(x, y)) {
                boolean fast = isOccupant(connection, vehicle) || nearestSq(connection, x, y) <= NEAR_TILES * NEAR_TILES;
                sendWithPriority(packet, type, connection, fast ? IMMEDIATE_PRIORITY : type.packetPriority);
                if (fast && !counted) {
                    PLZFixes.hit(PLZFixes.VEHICLE_PHYSICS_FAST_LANE);
                    counted = true;
                }
            }
        }
    }

    private static void sendWithPriority(INetworkPacket packet, PacketTypes.PacketType type, UdpConnection connection, int priority) {
        ByteBufferWriter b = connection.startPacket();
        try {
            type.doPacket(b);
            packet.write(b);
            connection.endPacket(priority, type.packetReliability, type.orderingChannel);
        } catch (Exception e) {
            connection.cancelPacket();
            DebugType.Multiplayer.printException(e, "PLZVehicleNet relay failed", LogSeverity.Error);
        }
    }

    private static void sendContents(UdpConnection connection, BaseVehicle vehicle) {
        for (int i = 0; i < vehicle.getPartCount(); i++) {
            VehiclePart part = vehicle.getPartByIndex(i);
            ItemContainer container = part == null ? null : part.getItemContainer();
            if (container != null && !container.getItems().isEmpty()) {
                INetworkPacket.send(connection, PacketTypes.PacketType.AddInventoryItemToContainer, container, container.getItems());
            }
        }
    }

    private static boolean hasContents(BaseVehicle vehicle) {
        for (int i = 0; i < vehicle.getPartCount(); i++) {
            VehiclePart part = vehicle.getPartByIndex(i);
            ItemContainer container = part == null ? null : part.getItemContainer();
            if (container != null && !container.getItems().isEmpty()) {
                return true;
            }
        }

        return false;
    }

    private static boolean isOccupant(UdpConnection connection, BaseVehicle vehicle) {
        for (IsoPlayer player : connection.players) {
            if (player != null && player.getVehicle() == vehicle) {
                return true;
            }
        }

        return false;
    }

    private static float nearestSq(UdpConnection connection, float x, float y) {
        float best = Float.MAX_VALUE;
        for (IsoPlayer player : connection.players) {
            if (player != null) {
                float dx = player.getX() - x;
                float dy = player.getY() - y;
                best = Math.min(best, dx * dx + dy * dy);
            }
        }

        return best;
    }
}
