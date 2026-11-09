package plz.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import fmod.FMODRecordPosition;
import fmod.FMODSoundData;
import fmod.javafmod;
import fmod.javafmodJNI;
import java.util.ArrayList;
import java.util.concurrent.Semaphore;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import se.krka.kahlua.vm.KahluaTable;
import se.krka.kahlua.vm.Platform;
import zombie.characters.IsoPlayer;
import zombie.core.raknet.RakVoice;
import zombie.core.raknet.VoiceManager;
import zombie.core.raknet.VoiceManagerData;
import zombie.input.GameKeyboard;
import zombie.input.KeybindId;
import zombie.iso.IsoUtils;
import zombie.network.FakeClientManager;
import zombie.network.GameClient;
import zombie.plz.PLZChannelProbe;
import zombie.plz.PLZFixes;
import zombie.plz.PLZLiveVideo;
import zombie.plz.PLZStation;
import zombie.plz.PLZVoice;
import zombie.plz.PLZVoiceChanger;
import zombie.plz.PLZVoiceLua;
import zombie.plz.PLZVoiceState;
import zombie.plz.access.PLZVoiceManagerAccess;
import zombie.radio.devices.DeviceData;

@Mixin(VoiceManager.class)
abstract class VoiceManagerMixin implements PLZVoiceManagerAccess {
    @Shadow
    private static boolean serverVOIPEnable;
    @Shadow
    private static int sampleRate;
    @Shadow
    private static int period;
    @Shadow
    private static float minDistance;
    @Shadow
    private static float maxDistance;
    @Shadow
    private static boolean is3D;
    @Shadow
    private static byte[] fmodReceiveBuffer;
    @Shadow
    private boolean isModeVad;
    @Shadow
    private boolean isModePpt;
    @Shadow
    private int volumePlayers;
    @Shadow
    @Final
    private FMODSoundData fmodSoundData;
    @Shadow
    @Final
    private FMODRecordPosition fmodRecordPosition;
    @Shadow
    private int fmodSoundDataError;
    @Shadow
    private int fmodVoiceRecordDriverId;
    @Shadow
    private long fmodRecordSound;
    @Shadow
    private Semaphore recDevSemaphore;
    @Shadow
    private boolean initialiseRecDev;
    @Shadow
    private long indicatorIsVoice;
    @Shadow
    private boolean quit;
    @Shadow
    private long timeLast;
    @Shadow
    byte[] buf;
    @Shadow
    @Final
    private Object notifier;
    @Shadow
    private boolean isClient;
    @Shadow
    private boolean testingMicrophone;

    @Shadow
    private long getUserPlaySound(short onlineId) {
        throw new AssertionError();
    }

    @Shadow
    private float getCanHearAllVolume(float range) {
        throw new AssertionError();
    }

    @Shadow
    private static void logFrame(IsoPlayer me, IsoPlayer player, float distance) {
        throw new AssertionError();
    }

    @Override
    public long plzIndicatorIsVoice() {
        return this.indicatorIsVoice;
    }

    @Override
    public boolean plzServerVOIPEnable() {
        return serverVOIPEnable;
    }

    @Override
    public float plzMinDistance() {
        return minDistance;
    }

    @Override
    public float plzMaxDistance() {
        return maxDistance;
    }

    // Only a radio match carries a deviceData; hear-all and proximity entries never reach an unloaded speaker.
    @Override
    public VoiceManagerData.RadioData plzRadioLink(VoiceManagerData theirs) {
        IsoPlayer me = IsoPlayer.getInstance();
        if (me == null || me.onlineId == -1 || theirs.index == me.onlineId) {
            return null;
        }

        VoiceManagerData.RadioData link = this.checkForNearbyRadios(theirs);
        return link != null && ((VoiceManagerData_RadioDataMixin) link).plz$getDeviceData() != null ? link : null;
    }

    // The grain length is a fraction of a second, so the shifter needs the settled rate and a fresh ring here.
    @Inject(
        method = "ResetRecSound",
        at = @At(value = "FIELD", target = "Lzombie/core/raknet/VoiceManager;fmodReceiveBuffer:[B", opcode = org.objectweb.asm.Opcodes.PUTSTATIC, shift = At.Shift.AFTER)
    )
    private void plz$resetVoiceChanger(CallbackInfo ci) {
        PLZVoiceChanger.setSampleRate(sampleRate);
        PLZVoiceChanger.reset();
    }

    @Inject(method = "LuaRegister", at = @At("TAIL"))
    private void plz$registerLua(Platform platform, KahluaTable environment, CallbackInfo ci, @Local(name = "table") KahluaTable table) {
        PLZVoiceLua.register(table, this);
    }

    // The ceiling is the gain, not 1.0: that is what lets the loudness setting reach anything; 1.0 is byte-identical to vanilla.
    @ModifyArg(
        method = "setUserPlaySound",
        at = @At(value = "INVOKE", target = "Lzombie/iso/IsoUtils;clamp(FFF)F"),
        index = 2
    )
    private float plz$playbackCeiling(float vanillaMax) {
        return PLZVoice.playbackCeiling();
    }

    @Unique
    private float plz$setUserPlaySound(long userPlayChannel, float volume) {
        volume = IsoUtils.clamp(volume * IsoUtils.lerp(this.volumePlayers, 0.0F, 12.0F), 0.0F, PLZVoice.playbackCeiling());
        javafmod.FMOD_Channel_SetVolume(userPlayChannel, volume);
        return volume;
    }

    // Releases the RAW sound too: getUserPlaySound drops the old handle when it reopens, so a bare stop leaks it.
    @Unique
    private void plz$closeVoiceChannel(VoiceManagerData d) throws InterruptedException {
        this.recDevSemaphore.acquire();
        try {
            javafmod.FMOD_Channel_Stop(d.userplaychannel);
            d.userplaychannel = 0L;
            if (d.userplaysound != 0L) {
                javafmod.FMOD_RAWPlaySound_Release(d.userplaysound);
                d.userplaysound = 0L;
            }
        } finally {
            this.recDevSemaphore.release();
        }
    }

    @WrapOperation(
        method = "UpdateChannelsRoaming",
        at = @At(value = "NEW", target = "(FFF)Lzombie/core/raknet/VoiceManagerData$RadioData;")
    )
    private VoiceManagerData.RadioData plz$routingEntry(
        float distance, float x, float y, Operation<VoiceManagerData.RadioData> original, @Local(name = "player") IsoPlayer player
    ) {
        int mode = PLZVoice.getMode(player.getIndex());
        return new VoiceManagerData.RadioData(
            PLZVoice.entryChannel(player.getZi()), PLZVoiceState.privateCall ? 0.0F : PLZVoice.entryRange(mode, distance), x, y
        );
    }

    // The loop test that fails is the last instruction inside synchronized(radioData), where station entries belong.
    @ModifyExpressionValue(
        method = "UpdateChannelsRoaming",
        at = @At(value = "FIELD", target = "Lzombie/characters/IsoPlayer;numPlayers:I", opcode = org.objectweb.asm.Opcodes.GETSTATIC)
    )
    private int plz$appendStationsAtLoopEnd(
        int numPlayers, @Local(name = "i") int i, @Local(name = "me") IsoPlayer me, @Local(name = "myRadioData") VoiceManagerData myRadioData
    ) {
        if (i >= numPlayers) {
            plz$appendStationEntries(me, myRadioData);
        }

        return numPlayers;
    }

    // Station receivers past the vanilla 4-tile scan listen at range 0; the on-air host adds the transmitter.
    @Unique
    private static void plz$appendStationEntries(IsoPlayer me, VoiceManagerData mine) {
        for (PLZStation.Receiver r : PLZStation.receivers()) {
            DeviceData dd = r.device;
            if (dd == null || !dd.getIsTurnedOn() || dd.getChannel() != r.freq) {
                continue;
            }

            boolean present = false;
            for (int k = 1; k < mine.radioData.size(); k++) {
                if (((VoiceManagerData_RadioDataMixin) mine.radioData.get(k)).plz$getDeviceData() == dd) {
                    present = true;
                    break;
                }
            }

            if (!present) {
                VoiceManagerData.RadioData entry = new VoiceManagerData.RadioData(dd, r.x, r.y);
                entry.distance = 0.0F;
                mine.radioData.add(entry);
            }
        }

        int freq = PLZStation.transmitFreq();
        if (freq != 0) {
            mine.radioData.add(new VoiceManagerData.RadioData(freq, PLZStation.TRANSMIT_RANGE, me.getX(), me.getY()));
        }
    }

    @ModifyConstant(method = "InitVMServer", constant = @Constant(intValue = 8000))
    private int plz$bufferingBytes(int vanilla) {
        return PLZVoice.bufferingBytes();
    }

    /**
     * @author PLZ
     * @reason Voice changer, radio PTT, live cast, idle channel close, station receivers, floors and megaphone touch most of the loop.
     */
    @Overwrite
    synchronized void UpdateVMClient() throws InterruptedException {
        while (!this.quit && !this.isClient && !this.testingMicrophone) {
            synchronized (this.notifier) {
                try {
                    this.notifier.wait();
                } catch (InterruptedException var11) {
                }
            }
        }

        if (serverVOIPEnable) {
            if (IsoPlayer.getInstance() != null) {
                IsoPlayer.getInstance().isSpeek = System.currentTimeMillis() - this.indicatorIsVoice <= 300L;
            }

            if (this.initialiseRecDev) {
                this.recDevSemaphore.acquire();
                javafmod.FMOD_System_GetRecordPosition(this.fmodVoiceRecordDriverId, this.fmodRecordPosition);
                if (fmodReceiveBuffer != null) {
                    while ((this.fmodSoundDataError = javafmod.FMOD_Sound_GetData(this.fmodRecordSound, fmodReceiveBuffer, this.fmodSoundData)) == 0) {
                        // Voice changer before the encoder and above the send gate, so its ring never gets a seam at word boundaries.
                        PLZVoiceChanger.process(fmodReceiveBuffer, (int)this.fmodSoundData.size);

                        if ((IsoPlayer.getInstance() != null && GameClient.connection != null || FakeClientManager.isVOIPEnabled())
                            && (!is3D || !IsoPlayer.getInstance().isDead())) {
                            boolean plzSent = false;
                            if (this.isModePpt) {
                                if (GameKeyboard.isKeyDown(KeybindId.ENABLE_VOICE_TRANSMIT) || PLZVoiceState.isRadioPttDown()) {
                                    RakVoice.SendFrame(
                                        GameClient.connection.getConnectedGUID(),
                                        IsoPlayer.getInstance().getOnlineID(),
                                        fmodReceiveBuffer,
                                        this.fmodSoundData.size
                                    );
                                    this.indicatorIsVoice = System.currentTimeMillis();
                                    plzSent = true;
                                } else if (FakeClientManager.isVOIPEnabled()) {
                                    RakVoice.SendFrame(
                                        FakeClientManager.getConnectedGUID(), FakeClientManager.getOnlineID(), fmodReceiveBuffer, this.fmodSoundData.size
                                    );
                                    this.indicatorIsVoice = System.currentTimeMillis();
                                }
                            }

                            if (this.isModeVad && this.fmodSoundData.vad != 0L) {
                                RakVoice.SendFrame(
                                    GameClient.connection.getConnectedGUID(), IsoPlayer.getInstance().getOnlineID(), fmodReceiveBuffer, this.fmodSoundData.size
                                );
                                this.indicatorIsVoice = System.currentTimeMillis();
                                plzSent = true;
                            }

                            if (plzSent) {
                                PLZLiveVideo.castVoice(fmodReceiveBuffer, (int)this.fmodSoundData.size, sampleRate);
                            }
                        }
                    }
                }

                this.recDevSemaphore.release();
            }

            ArrayList<IsoPlayer> players = GameClient.instance.getPlayers();

            for (IsoPlayer player : players) {
                if (player != null && player != IsoPlayer.getInstance() && player.getOnlineID() != -1) {
                    VoiceManagerData.get(player.getOnlineID());
                }
            }

            ArrayList<VoiceManagerData> data = VoiceManagerData.data;
            synchronized (PLZVoiceState.RADIO_ONLY_PEERS) {
                PLZVoiceState.RADIO_ONLY_PEERS.clear();
            }

            boolean plzCensus = PLZFixes.on(PLZFixes.CHANNEL_PROBE) && PLZChannelProbe.censusBegin();
            for (int i = 0; i < data.size(); i++) {
                VoiceManagerData d = data.get(i);
                d.online = false;
                d.player = null;

                for (IsoPlayer player : players) {
                    if (player != null && player.onlineId == d.index) {
                        d.online = true;
                        d.player = player;
                        break;
                    }
                }

                if (!d.online) {
                    VoiceManagerData.RadioData rdata = this.checkForNearbyRadios(d);
                    d.online = rdata != null && ((VoiceManagerData_RadioDataMixin) rdata).plz$getDeviceData() != null;
                }

                if (d.online && d.player == null) {
                    synchronized (PLZVoiceState.RADIO_ONLY_PEERS) {
                        PLZVoiceState.RADIO_ONLY_PEERS.add(d.index);
                    }
                }

                if (false & d.index == 0) {
                    break;
                }

                if (d.userplaychannel != 0L & !d.online) {
                    this.plz$closeVoiceChannel(d);
                } else if (d.userplaychannel != 0L && PLZVoiceState.voiceIdle(d.index) && PLZFixes.on(PLZFixes.VOICE_IDLE_CHANNEL)) {
                    this.plz$closeVoiceChannel(d);
                    PLZFixes.hit(PLZFixes.VOICE_IDLE_CHANNEL);
                }

                if (plzCensus) {
                    PLZChannelProbe.census(d.userplaychannel, d.index);
                }
            }

            if (plzCensus) {
                PLZChannelProbe.censusEnd();
            }

            long currentTime = System.currentTimeMillis() - this.timeLast;
            if (currentTime >= period) {
                this.timeLast += currentTime;
                if (IsoPlayer.getInstance() == null) {
                    return;
                }

                for (int i = 0; i < data.size(); i++) {
                    VoiceManagerData d = data.get(i);
                    IsoPlayer me = IsoPlayer.getInstance();
                    IsoPlayer player = d.player;
                    if (d.online && player != me) {
                        while (RakVoice.ReceiveFrame(d.index, this.buf)) {
                            if (player != null) {
                                d.voicetimeout = 10L;
                            }

                            PLZVoiceState.countFrame(d.index);
                            if (!d.userplaymute) {
                                // Open the channel before it is positioned, or the first frame after an idle close plays unplaced.
                                this.getUserPlaySound(d.index);
                                float plzVolume = 0.0F;
                                float range = player != null ? IsoUtils.DistanceTo(me.getX(), me.getY(), player.getX(), player.getY()) : 0.0F;
                                if (me.canHearAll()) {
                                    javafmodJNI.FMOD_Channel_Set3DLevel(d.userplaychannel, 0.0F);
                                    javafmod.FMOD_Channel_Set3DAttributes(d.userplaychannel, me.getX(), me.getY(), me.getZ(), 0.0F, 0.0F, 0.0F);
                                    plzVolume = this.plz$setUserPlaySound(d.userplaychannel, this.getCanHearAllVolume(range));
                                } else {
                                    boolean plzSameVehicle = PLZVoiceState.sharesVehicle(me, player);
                                    VoiceManagerData.RadioData rdata = this.checkForNearbyRadios(d);
                                    if (rdata != null && ((VoiceManagerData_RadioDataMixin) rdata).plz$getDeviceData() != null && !plzSameVehicle) {
                                        javafmodJNI.FMOD_Channel_Set3DLevel(d.userplaychannel, 0.0F);
                                        javafmod.FMOD_Channel_Set3DAttributes(d.userplaychannel, me.getX(), me.getY(), me.getZ(), 0.0F, 0.0F, 0.0F);
                                        float plzRadioVolume = ((VoiceManagerData_RadioDataMixin) rdata).plz$getDeviceData().getDeviceVolume();
                                        PLZStation.Receiver plzReceiver = PLZStation.isStation(rdata.freq) ? PLZStation.receiverFor(((VoiceManagerData_RadioDataMixin) rdata).plz$getDeviceData()) : null;
                                        if (plzReceiver != null) {
                                            plzRadioVolume *= plzReceiver.gain * PLZStation.voiceFalloff(plzReceiver, me.getX(), me.getY());
                                            PLZStation.noteVoice(rdata.freq);
                                        }

                                        plzVolume = this.plz$setUserPlaySound(d.userplaychannel, plzRadioVolume);
                                        ((VoiceManagerData_RadioDataMixin) rdata).plz$getDeviceData().doReceiveMPSignal(((VoiceManagerData_RadioDataMixin) rdata).plz$getLastReceiveDistance());
                                    } else if (player != null) {
                                        if (rdata == null && !plzSameVehicle) {
                                            // The one silent mute in the path, so name which test dropped the frame.
                                            PLZVoiceState.logMute(me, player, d, range);
                                            javafmodJNI.FMOD_Channel_Set3DLevel(d.userplaychannel, 0.0F);
                                            javafmod.FMOD_Channel_Set3DAttributes(d.userplaychannel, me.getX(), me.getY(), me.getZ(), 0.0F, 0.0F, 0.0F);
                                            javafmod.FMOD_Channel_SetVolume(d.userplaychannel, 0.0F);
                                        } else {
                                            if (is3D) {
                                                javafmodJNI.FMOD_Channel_Set3DLevel(d.userplaychannel, IsoUtils.lerp(range, 0.0F, minDistance));
                                                javafmod.FMOD_Channel_Set3DAttributes(
                                                    d.userplaychannel, player.getX(), player.getY(), player.getZ(), 0.0F, 0.0F, 0.0F
                                                );
                                            } else {
                                                javafmodJNI.FMOD_Channel_Set3DLevel(d.userplaychannel, 0.0F);
                                                javafmod.FMOD_Channel_Set3DAttributes(d.userplaychannel, me.getX(), me.getY(), me.getZ(), 0.0F, 0.0F, 0.0F);
                                            }

                                            int speakerMode = PLZVoiceState.speakerMode(d, maxDistance);
                                            // range, not lastReceiveDistance: the routing entry holds whole tiles every 3 s, so loudness would staircase.
                                            plzVolume = this.plz$setUserPlaySound(
                                                d.userplaychannel,
                                                PLZVoice.volumeFor(
                                                    speakerMode, range, minDistance, maxDistance
                                                )
                                            );

                                            // The hiss rides the proximity path only; a radio voice already carries the device static.
                                            if (PLZVoice.isMegaphone(speakerMode)) {
                                                PLZVoiceState.startMegaphoneStatic(player);
                                            } else {
                                                PLZVoiceState.stopMegaphoneStatic(player);
                                            }
                                        }

                                        if (range > maxDistance) {
                                            logFrame(me, player, range);
                                        }
                                    }
                                }

                                if (PLZFixes.on(PLZFixes.CHANNEL_PROBE)) {
                                    PLZChannelProbe.observe(d.userplaychannel, d.index, plzVolume > 0.0F);
                                }

                                javafmod.FMOD_System_RAWPlayData(this.getUserPlaySound(d.index), this.buf, this.buf.length);
                            }
                        }

                        if (player != null) {
                            if (d.voicetimeout == 0L) {
                                player.isSpeek = false;
                                // The one place a hiss reliably ends: the speaker stopped, left range or put the megaphone away.
                                PLZVoiceState.stopMegaphoneStatic(player);
                            } else {
                                d.voicetimeout--;
                                player.isSpeek = true;
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * @author PLZ
     * @reason Voice and station channels are skipped mid-loop and the proximity test uses the drift-tolerant gate range.
     */
    @Overwrite
    private VoiceManagerData.RadioData checkForNearbyRadios(VoiceManagerData radioData) {
        IsoPlayer me = IsoPlayer.getInstance();
        if (me != null && me.onlineId != -1) {
            VoiceManagerData myRadioData = VoiceManagerData.get(me.onlineId);
            if (myRadioData == null) {
                return null;
            }

            if (myRadioData.isCanHearAll) {
                ((VoiceManagerData_RadioDataMixin) myRadioData.radioData.get(0)).plz$setLastReceiveDistance(0.0F);
                return myRadioData.radioData.get(0);
            }

            synchronized (myRadioData.radioData) {
                for (int i = 1; i < myRadioData.radioData.size(); i++) {
                    if (PLZVoice.isVoiceChannel(myRadioData.radioData.get(i).freq)) {
                        continue;
                    }

                    int plzFreq = myRadioData.radioData.get(i).freq;
                    if (PLZStation.isStation(plzFreq)
                        && (PLZStation.receiverFor(((VoiceManagerData_RadioDataMixin) myRadioData.radioData.get(i)).plz$getDeviceData()) == null || !PLZStation.isOnAir(plzFreq, radioData.index))) {
                        continue;
                    }

                    synchronized (radioData.radioData) {
                        for (int j = 1; j < radioData.radioData.size(); j++) {
                            if (PLZVoice.isVoiceChannel(radioData.radioData.get(j).freq)) {
                                continue;
                            }

                            if (myRadioData.radioData.get(i).freq == radioData.radioData.get(j).freq) {
                                float dx = myRadioData.radioData.get(i).x - radioData.radioData.get(j).x;
                                float dy = myRadioData.radioData.get(i).y - radioData.radioData.get(j).y;
                                ((VoiceManagerData_RadioDataMixin) myRadioData.radioData.get(i)).plz$setLastReceiveDistance((float)Math.sqrt(dx * dx + dy * dy));
                                if (((VoiceManagerData_RadioDataMixin) myRadioData.radioData.get(i)).plz$getLastReceiveDistance() < radioData.radioData.get(j).distance) {
                                    return myRadioData.radioData.get(i);
                                }
                            }
                        }
                    }
                }
            }

            synchronized (myRadioData.radioData) {
                synchronized (radioData.radioData) {
                    if (!radioData.radioData.isEmpty() && !myRadioData.radioData.isEmpty()) {
                        float dx = myRadioData.radioData.get(0).x - radioData.radioData.get(0).x;
                        float dy = myRadioData.radioData.get(0).y - radioData.radioData.get(0).y;
                        ((VoiceManagerData_RadioDataMixin) myRadioData.radioData.get(0)).plz$setLastReceiveDistance((float)Math.sqrt(dx * dx + dy * dy));
                        // gateRange, not audibleRange: entries are whole tiles 3 s apart; PLZVoice.volumeFor enforces the real range.
                        float plzAudible = PLZVoice.gateRange(
                            myRadioData.radioData.get(0).freq,
                            radioData.radioData.get(0).freq,
                            radioData.radioData.get(0).distance,
                            maxDistance
                        );
                        if (((VoiceManagerData_RadioDataMixin) myRadioData.radioData.get(0)).plz$getLastReceiveDistance() < plzAudible) {
                            return myRadioData.radioData.get(0);
                        }
                    }
                }

                return null;
            }
        } else {
            return null;
        }
    }
}
