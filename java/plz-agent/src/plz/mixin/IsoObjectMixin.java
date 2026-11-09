package plz.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import se.krka.kahlua.vm.KahluaTable;
import zombie.core.network.ByteBufferReader;
import zombie.core.network.ByteBufferWriter;
import zombie.core.raknet.UdpConnection;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoObject;
import zombie.iso.SpriteDetails.IsoFlagType;
import zombie.iso.sprite.IsoSprite;
import zombie.iso.sprite.IsoSpriteManager;
import zombie.network.GameClient;
import zombie.network.GameServer;
import zombie.network.PacketTypes;
import zombie.plz.PLZFixes;
import zombie.plz.PLZSyncWatch;
import zombie.plz.PLZUtilities;

@Mixin(IsoObject.class)
abstract class IsoObjectMixin {
    @Shadow
    public IsoSprite sprite;

    @Shadow
    public IsoGridSquare square;

    @Shadow
    public String spriteName;

    @Shadow
    protected boolean usesExternalWaterSource;

    @Shadow
    public abstract int getObjectIndex();

    @Shadow
    public abstract void syncIsoObjectSend(ByteBufferWriter bb);

    @Shadow
    public abstract void syncIsoObjectReceive(ByteBufferReader bb, UdpConnection source);

    @Shadow
    public abstract void flagForHotSave();

    @Shadow
    public abstract boolean hasModData();

    @Shadow
    public abstract KahluaTable getModData();

    /**
     * @author PLZ
     * @reason Both server broadcast loops skip irrelevant connections and count sends for PLZSyncWatch.
     */
    @Overwrite
    public void syncIsoObject(boolean bRemote, byte val, UdpConnection source, ByteBufferReader bb) {
        if (this.square == null) {
            System.out.println("ERROR: " + this.getClass().getSimpleName() + " square is null");
        } else if (this.getObjectIndex() == -1) {
            System.out
                .println(
                    "ERROR: "
                        + this.getClass().getSimpleName()
                        + " not found on square "
                        + this.square.getX()
                        + ","
                        + this.square.getY()
                        + ","
                        + this.square.getZ()
                );
        } else {
            if (GameClient.client && !bRemote) {
                ByteBufferWriter b = GameClient.connection.startPacket();
                PacketTypes.PacketType.SyncIsoObject.doPacket(b);
                this.syncIsoObjectSend(b);
                PacketTypes.PacketType.SyncIsoObject.send(GameClient.connection);
            } else if (GameServer.server && !bRemote) {
                int plzSent = 0;
                int plzSkipped = 0;

                for (UdpConnection connection : GameServer.udpEngine.connections) {
                    if (PLZSyncWatch.relevantOnly && !connection.isRelevantTo(this.square.x, this.square.y)) {
                        plzSkipped++;
                        continue;
                    }

                    ByteBufferWriter b = connection.startPacket();
                    PacketTypes.PacketType.SyncIsoObject.doPacket(b);
                    this.syncIsoObjectSend(b);
                    PacketTypes.PacketType.SyncIsoObject.send(connection);
                    plzSent++;
                }

                if (PLZSyncWatch.enabled) {
                    PLZSyncWatch.record(
                        this.getClass().getSimpleName() + " (server)",
                        this.square.getX(),
                        this.square.getY(),
                        this.square.getZ(),
                        plzSent,
                        plzSkipped,
                        null
                    );
                }
            } else if (bRemote) {
                this.syncIsoObjectReceive(bb, source);
                if (GameServer.server) {
                    int plzSent = 0;
                    int plzSkipped = 0;

                    for (UdpConnection connection : GameServer.udpEngine.connections) {
                        if (source != null && connection.getConnectedGUID() != source.getConnectedGUID()) {
                            if (PLZSyncWatch.relevantOnly && !connection.isRelevantTo(this.square.x, this.square.y)) {
                                plzSkipped++;
                                continue;
                            }

                            ByteBufferWriter b = connection.startPacket();
                            PacketTypes.PacketType.SyncIsoObject.doPacket(b);
                            this.syncIsoObjectSend(b);
                            PacketTypes.PacketType.SyncIsoObject.send(connection);
                            plzSent++;
                        }
                    }

                    if (PLZSyncWatch.enabled) {
                        PLZSyncWatch.record(
                            this.getClass().getSimpleName(),
                            this.square.getX(),
                            this.square.getY(),
                            this.square.getZ(),
                            plzSent,
                            plzSkipped,
                            source
                        );
                    }
                }
            }

            this.flagForHotSave();
        }
    }

    // PLZ: a player-placed fixture (PLZ's plzPlumbed or vanilla's canBeWaterPiped) not already fed by a rain barrel.
    @Unique
    private boolean plz$isPlacedWaterFixture() {
        if (this.sprite == null || !this.sprite.getProperties().has(IsoFlagType.waterPiped)) {
            return false;
        }

        if (this.usesExternalWaterSource || !this.hasModData()) {
            return false;
        }

        return this.getModData().rawget("plzPlumbed") instanceof Boolean plzPlumbed && plzPlumbed
            || this.getModData().rawget("canBeWaterPiped") instanceof Boolean canBePiped && canBePiped;
    }

    @Unique
    private boolean plz$isMainsEverywhere() {
        return this.sprite.getProperties().has(IsoFlagType.waterPiped) && PLZUtilities.waterEverywhere();
    }

    // PLZ: vanilla's room, derelict-zone and shutoff gates have no Lua equivalent and rule out most build-anywhere placements.
    @Inject(
        method = "isWaterInfinite",
        at = @At(value = "FIELD", target = "Lzombie/iso/IsoObject;square:Lzombie/iso/IsoGridSquare;", opcode = Opcodes.GETFIELD, ordinal = 0),
        cancellable = true
    )
    private void plz$placedFixtureOnMains(CallbackInfoReturnable<Boolean> cir) {
        if (this.plz$isPlacedWaterFixture() || this.plz$isMainsEverywhere()) {
            cir.setReturnValue(true);
        }
    }

    // PLZ: canBeWaterPiped is exactly what makes the unmoved test say no, so a placed sink would otherwise run TAINTED.
    @WrapOperation(
        method = "getInfiniteWaterType",
        at = @At(value = "INVOKE", target = "Lzombie/iso/IsoObject;isUnmovedPipedWaterSource()Z")
    )
    private boolean plz$placedFixtureIsTap(IsoObject self, Operation<Boolean> original) {
        return original.call(self) || this.plz$isPlacedWaterFixture() || this.plz$isMainsEverywhere();
    }

    // PLZ: clients assign getSprite(id) unconditionally, so an unresolvable sprite nulls theirs and throws every render.
    @Inject(method = "transmitUpdatedSpriteToClients(Lzombie/core/raknet/UdpConnection;)V", at = @At("HEAD"), cancellable = true)
    private void plz$spriteTransmitGuard(UdpConnection connection, CallbackInfo ci) {
        if (GameServer.server && PLZFixes.on(PLZFixes.SPRITE_TRANSMIT_GUARD) && !this.plz$spriteIsResolvableByClients()) {
            PLZFixes.hit(PLZFixes.SPRITE_TRANSMIT_GUARD);
            ci.cancel();
        }
    }

    @Unique
    private boolean plz$spriteIsResolvableByClients() {
        IsoSprite sprite = this.sprite;
        if (sprite == null) {
            return false;
        }

        if (IsoSpriteManager.instance != null && IsoSpriteManager.instance.getSprite(sprite.id) != null) {
            return true;
        }

        return this.spriteName != null && !this.spriteName.isEmpty();
    }
}
