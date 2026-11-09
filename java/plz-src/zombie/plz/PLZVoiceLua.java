package zombie.plz;

import java.util.ArrayList;
import java.util.List;
import se.krka.kahlua.vm.JavaFunction;
import se.krka.kahlua.vm.KahluaTable;
import se.krka.kahlua.vm.LuaCallFrame;
import zombie.characters.IsoPlayer;
import zombie.core.raknet.VoiceManagerData;
import zombie.network.GameClient;
import zombie.plz.access.PLZVoiceManagerAccess;
import zombie.radio.devices.DeviceData;

public final class PLZVoiceLua {
    private PLZVoiceLua() {
    }

    public static void register(KahluaTable table, PLZVoiceManagerAccess access) {
        table.rawset("setPrivateCall", new JavaFunction() {
            @Override
            public int call(LuaCallFrame callFrame, int nArguments) {
                Object arg1 = callFrame.get(0);
                PLZVoiceState.privateCall = arg1 instanceof Boolean && (Boolean)arg1;
                PLZVoiceChannels.republish();
                return 1;
            }
        });
        table.rawset("getPrivateCall", new JavaFunction() {
            @Override
            public int call(LuaCallFrame callFrame, int nArguments) {
                callFrame.push(PLZVoiceState.privateCall);
                return 1;
            }
        });
        table.rawset("isTransmitting", new JavaFunction() {
            @Override
            public int call(LuaCallFrame callFrame, int nArguments) {
                callFrame.push(access.plzServerVOIPEnable() && System.currentTimeMillis() - access.plzIndicatorIsVoice() <= 300L);
                return 1;
            }
        });
        table.rawset("setRadioPttBinding", new JavaFunction() {
            @Override
            public int call(LuaCallFrame callFrame, int nArguments) {
                // get(0), NOT get(1): LuaCallFrame is zero-based, and get(1) left the binding null forever.
                Object arg1 = callFrame.get(0);
                PLZVoiceState.radioPttBinding = arg1 instanceof String s && !s.isEmpty() ? s : null;
                return 1;
            }
        });
        table.rawset("republishChannels", new JavaFunction() {
            @Override
            public int call(LuaCallFrame callFrame, int nArguments) {
                PLZVoiceChannels.republish();
                return 1;
            }
        });
        // Whether this client would actually play that peer's radio audio; `loaded` is what getPeerRadio cannot tell.
        table.rawset("getRadioAudio", new JavaFunction() {
            @Override
            public int call(LuaCallFrame callFrame, int nArguments) {
                // get(0). Zero-based, like every other read on this table.
                KahluaTable info = callFrame.getPlatform().newTable();
                Object arg1 = callFrame.get(0);
                if (!(arg1 instanceof Double onlineId)) {
                    info.rawset("known", false);
                    callFrame.push(info);
                    return 1;
                }

                short id = (short)onlineId.intValue();
                boolean loaded = false;
                if (GameClient.client && GameClient.instance != null) {
                    ArrayList<IsoPlayer> players = GameClient.instance.getPlayers();
                    for (int i = 0; i < players.size(); i++) {
                        if (players.get(i).onlineId == id) {
                            loaded = true;
                            break;
                        }
                    }
                }

                boolean serviced;
                synchronized (PLZVoiceState.RADIO_ONLY_PEERS) {
                    serviced = PLZVoiceState.RADIO_ONLY_PEERS.contains(id);
                }

                int frames;
                synchronized (PLZVoiceState.FRAME_COUNTS) {
                    Integer seen = PLZVoiceState.FRAME_COUNTS.get(id);
                    frames = seen == null ? 0 : seen;
                }

                info.rawset("known", true);
                info.rawset("loaded", loaded);
                info.rawset("serviced", serviced);
                // Without VOIP the receive pass never runs, so `serviced` reads false for a non-radio reason.
                info.rawset("voip", access.plzServerVOIPEnable());
                // A hear-all listener gets the proximity entry first, so `linked` is structurally false for them.
                boolean hearAll = false;
                IsoPlayer me = IsoPlayer.getInstance();
                if (me != null && me.onlineId != -1) {
                    hearAll = VoiceManagerData.get(me.onlineId).isCanHearAll || me.canHearAll();
                }
                info.rawset("hearAll", hearAll);
                info.rawset("linked", access.plzRadioLink(VoiceManagerData.get(id)) != null);
                info.rawset("frames", (double)frames);
                callFrame.push(info);
                return 1;
            }
        });
        table.rawset("setVoiceConfig", new JavaFunction() {
            @Override
            public int call(LuaCallFrame callFrame, int nArguments) {
                Object arg1 = callFrame.get(0);
                if (arg1 instanceof KahluaTable cfg) {
                    PLZVoice.setConfig(
                        PLZVoiceState.configBool(cfg, "floors", true),
                        PLZVoiceState.configBool(cfg, "modes", true),
                        PLZVoiceState.configFloat(cfg, "whisper", PLZVoice.getWhisperFraction()),
                        PLZVoiceState.configFloat(cfg, "normal", PLZVoice.getNormalFraction()),
                        PLZVoiceState.configFloat(cfg, "shout", PLZVoice.getShoutFraction()),
                        PLZVoiceState.configFloat(cfg, "megaphone", PLZVoice.getMegaphoneFraction()),
                        PLZVoiceState.configFloat(cfg, "falloff", PLZVoice.getFalloffExponent()),
                        PLZVoiceState.configFloat(cfg, "gain", PLZVoice.getGain())
                    );
                    PLZVoiceChannels.republish();
                }

                return 1;
            }
        });
        table.rawset("getVoiceInfo", new JavaFunction() {
            @Override
            public int call(LuaCallFrame callFrame, int nArguments) {
                KahluaTable info = callFrame.getPlatform().newTable();
                IsoPlayer me = IsoPlayer.getInstance();
                int index = me == null ? 0 : me.getIndex();
                int mode = PLZVoice.getMode(index);
                info.rawset("mode", (double)mode);
                info.rawset("floors", PLZVoice.isFloorsEnabled());
                info.rawset("modes", PLZVoice.isModesEnabled());
                info.rawset("maxDistance", (double)access.plzMaxDistance());
                info.rawset("range", (double)PLZVoice.rangeForMode(mode, access.plzMaxDistance()));
                                info.rawset("channel", (double)(me == null ? PLZVoice.CHANNEL_NONE : PLZVoice.entryChannel(me.getZi())));
                info.rawset("minDistance", (double)access.plzMinDistance());

                info.rawset("volumeAt2", (double)PLZVoice.volumeFor(mode, 2.0F, access.plzMinDistance(), access.plzMaxDistance()));
                info.rawset("volumeAt20", (double)PLZVoice.volumeFor(mode, 20.0F, access.plzMinDistance(), access.plzMaxDistance()));
                info.rawset("gain", (double)PLZVoice.getGain());

                info.rawset("isClient", GameClient.client);
                info.rawset("hasConnection", GameClient.connection != null);
                info.rawset("numPlayers", (double)IsoPlayer.numPlayers);
                info.rawset("player0", IsoPlayer.numPlayers > 0 && IsoPlayer.players[0] != null);
                info.rawset("onlineId", (double)(me == null ? -1 : me.onlineId));
                if (me != null && me.onlineId != -1) {
                    VoiceManagerData mineData = VoiceManagerData.get(me.onlineId);
                    synchronized (mineData.radioData) {
                        info.rawset("myEntries", (double)mineData.radioData.size());
                    }
                }
                callFrame.push(info);
                return 1;
            }
        });
        table.rawset("getPeerVoice", new JavaFunction() {
            @Override
            public int call(LuaCallFrame callFrame, int nArguments) {
                KahluaTable info = callFrame.getPlatform().newTable();
                Object arg1 = callFrame.get(0);
                IsoPlayer me = IsoPlayer.getInstance();
                if (!(arg1 instanceof Double onlineId) || me == null || me.onlineId == -1) {
                    info.rawset("known", false);
                    callFrame.push(info);
                    return 1;
                }

                VoiceManagerData theirs = VoiceManagerData.get((short)onlineId.intValue());
                VoiceManagerData mine = VoiceManagerData.get(me.onlineId);
                synchronized (theirs.radioData) {
                    synchronized (mine.radioData) {
                        if (theirs.radioData.isEmpty() || mine.radioData.isEmpty()) {
                            info.rawset("known", false);
                            callFrame.push(info);
                            return 1;
                        }

                        VoiceManagerData.RadioData them = theirs.radioData.get(0);
                        VoiceManagerData.RadioData us = mine.radioData.get(0);
                        float allowed = PLZVoice.audibleRange(us.freq, them.freq, them.distance, access.plzMaxDistance());
                        float gate = PLZVoice.gateRange(us.freq, them.freq, them.distance, access.plzMaxDistance());
                        float dx = us.x - them.x;
                        float dy = us.y - them.y;
                        float apart = (float)Math.sqrt(dx * dx + dy * dy);

                        info.rawset("known", true);
                        info.rawset("channel", (double)them.freq);
                        info.rawset("distance", (double)them.distance);
                        info.rawset("entries", (double)theirs.radioData.size());
                        info.rawset("myChannel", (double)us.freq);
                        info.rawset("apart", (double)apart);
                        info.rawset("allowed", (double)allowed);
                        info.rawset("wouldHear", apart < allowed);

                        // allowed/wouldHear are the audible range; gate/wouldRoute are the wider routing gate checkForNearbyRadios uses.
                        info.rawset("gate", (double)gate);
                        info.rawset("wouldRoute", apart < gate);

                        int floor = PLZVoice.floorForChannel(them.freq);
                        info.rawset("hasFloor", floor != PLZVoice.NO_FLOOR);
                        if (floor != PLZVoice.NO_FLOOR) {
                            info.rawset("floor", (double)floor);
                        }
                    }
                }

                callFrame.push(info);
                return 1;
            }
        });
        table.rawset("getPeerRadio", new JavaFunction() {
            @Override
            public int call(LuaCallFrame callFrame, int nArguments) {
                KahluaTable info = callFrame.getPlatform().newTable();
                // get(0): zero-based; rejecting the id here reads exactly like routing never crossing the wire.
                Object arg1 = callFrame.get(0);
                IsoPlayer me = IsoPlayer.getInstance();
                if (!(arg1 instanceof Double onlineId) || me == null || me.onlineId == -1) {
                    info.rawset("known", false);
                    callFrame.push(info);
                    return 1;
                }

                VoiceManagerData theirs = VoiceManagerData.get((short)onlineId.intValue());
                VoiceManagerData mine = VoiceManagerData.get(me.onlineId);
                synchronized (theirs.radioData) {
                    synchronized (mine.radioData) {
                        info.rawset("known", true);
                        info.rawset("myEntries", (double)mine.radioData.size());
                        info.rawset("theirEntries", (double)theirs.radioData.size());

                        boolean matched = false;
                        for (int i = 1; i < mine.radioData.size() && !matched; i++) {
                            VoiceManagerData.RadioData us = mine.radioData.get(i);
                            if (PLZVoice.floorForChannel(us.freq) != PLZVoice.NO_FLOOR) {
                                continue;
                            }
                            for (int j = 1; j < theirs.radioData.size(); j++) {
                                VoiceManagerData.RadioData them = theirs.radioData.get(j);
                                if (us.freq != them.freq) {
                                    continue;
                                }
                                float dx = us.x - them.x;
                                float dy = us.y - them.y;
                                float apart = (float)Math.sqrt(dx * dx + dy * dy);
                                info.rawset("channel", (double)them.freq);
                                info.rawset("theirRange", (double)them.distance);
                                info.rawset("apart", (double)apart);
                                if (apart < them.distance) {
                                    info.rawset("wouldHear", true);
                                    matched = true;
                                    break;
                                }
                                info.rawset("wouldHear", false);
                            }
                        }
                        info.rawset("matched", matched);
                    }
                }

                callFrame.push(info);
                return 1;
            }
        });
        // Voice changer: Java answers who is allowed, so the management row and the shifter never disagree.
        table.rawset("setVoiceChanger", new JavaFunction() {
            @Override
            public int call(LuaCallFrame callFrame, int nArguments) {
                Object arg1 = callFrame.get(0);
                Object arg2 = callFrame.get(1);
                boolean on = arg1 instanceof Boolean && (Boolean)arg1;

                // The preset lands before the enable, so switching presets while armed never plays the old one for a frame.
                boolean took = true;
                if (arg2 instanceof Double preset) {
                    took = PLZVoiceChanger.setPreset((int)Math.round(preset));
                }
                took = PLZVoiceChanger.setEnabled(on) && took;

                callFrame.push(took);
                return 1;
            }
        });
        table.rawset("getVoiceChanger", new JavaFunction() {
            @Override
            public int call(LuaCallFrame callFrame, int nArguments) {
                KahluaTable info = callFrame.getPlatform().newTable();
                info.rawset("allowed", PLZVoiceChanger.localIsAllowed());
                info.rawset("enabled", PLZVoiceChanger.isEnabled());
                info.rawset("preset", (double)PLZVoiceChanger.getPreset());
                info.rawset("presets", (double)PLZVoiceChanger.PRESET_COUNT);
                info.rawset("ratio", (double)PLZVoiceChanger.activeRatio());
                info.rawset("window", (double)PLZVoiceChanger.getWindow());
                callFrame.push(info);
                return 1;
            }
        });
        table.rawset("stationSetFrequencies", new JavaFunction() {
            @Override
            public int call(LuaCallFrame callFrame, int nArguments) {
                Object csv = callFrame.get(0);
                PLZStation.setFrequencies(csv instanceof String s ? s : "");
                return 0;
            }
        });
        table.rawset("stationSetOnAir", new JavaFunction() {
            @Override
            public int call(LuaCallFrame callFrame, int nArguments) {
                Object freq = callFrame.get(0);
                Object csv = nArguments > 1 ? callFrame.get(1) : null;
                if (freq instanceof Double d) {
                    PLZStation.setOnAir(d.intValue(), csv instanceof String s ? s : "");
                }
                return 0;
            }
        });
        table.rawset("stationSetTransmit", new JavaFunction() {
            @Override
            public int call(LuaCallFrame callFrame, int nArguments) {
                Object freq = callFrame.get(0);
                int next = freq instanceof Double d ? d.intValue() : 0;
                if (next != PLZStation.transmitFreq()) {
                    PLZStation.setTransmit(next);
                    PLZVoiceChannels.republish();
                }
                return 0;
            }
        });
        table.rawset("stationScan", new JavaFunction() {
            @Override
            public int call(LuaCallFrame callFrame, int nArguments) {
                Object range = callFrame.get(0);
                KahluaTable out = callFrame.getPlatform().newTable();
                List<Object[]> rows = PLZStation.scan(
                    IsoPlayer.getInstance(), range instanceof Double d ? d.floatValue() : 30.0F
                );
                for (int i = 0; i < rows.size(); i++) {
                    Object[] r = rows.get(i);
                    DeviceData dd = (DeviceData)r[0];
                    KahluaTable row = callFrame.getPlatform().newTable();
                    row.rawset("device", dd);
                    row.rawset("freq", (double)dd.getChannel());
                    row.rawset("volume", (double)dd.getDeviceVolume());
                    row.rawset("kind", (double)(Integer)r[1]);
                    row.rawset("x", (double)(Float)r[2]);
                    row.rawset("y", (double)(Float)r[3]);
                    row.rawset("z", (double)(Float)r[4]);
                    row.rawset("dist", (double)(Float)r[5]);
                    row.rawset("flat", r[6]);
                    out.rawset(i + 1, row);
                }
                callFrame.push(out);
                return 1;
            }
        });
        table.rawset("stationSetReceivers", new JavaFunction() {
            @Override
            public int call(LuaCallFrame callFrame, int nArguments) {
                Object arg = callFrame.get(0);
                ArrayList<PLZStation.Receiver> list = new ArrayList<>();
                if (arg instanceof KahluaTable t) {
                    for (int i = 1; i <= t.len(); i++) {
                        if (t.rawget(i) instanceof KahluaTable row && row.rawget("device") instanceof DeviceData dd) {
                            PLZStation.Receiver r = new PLZStation.Receiver();
                            r.device = dd;
                            r.freq = row.rawget("freq") instanceof Double d ? d.intValue() : dd.getChannel();
                            r.x = row.rawget("x") instanceof Double d ? d.floatValue() : 0.0F;
                            r.y = row.rawget("y") instanceof Double d ? d.floatValue() : 0.0F;
                            r.z = row.rawget("z") instanceof Double d ? d.floatValue() : 0.0F;
                            r.gain = row.rawget("gain") instanceof Double d ? d.floatValue() : 1.0F;
                            r.range = row.rawget("range") instanceof Double d ? d.floatValue() : 30.0F;
                            r.level = row.rawget("level") instanceof Double d ? d.intValue() : PLZStation.LEVEL_FULL;
                            r.flat = Boolean.TRUE.equals(row.rawget("flat"));
                            list.add(r);
                        }
                    }
                }
                if (PLZStation.setReceivers(list)) {
                    PLZVoiceState.stationReceiversDirty = true;
                }
                long now = System.currentTimeMillis();
                if (PLZVoiceState.stationReceiversDirty && now - PLZVoiceState.stationRepublishMs >= 1000L) {
                    PLZVoiceState.stationReceiversDirty = false;
                    PLZVoiceState.stationRepublishMs = now;
                    PLZVoiceChannels.republish();
                }
                return 0;
            }
        });
        table.rawset("stationVoiceAgeMs", new JavaFunction() {
            @Override
            public int call(LuaCallFrame callFrame, int nArguments) {
                Object freq = callFrame.get(0);
                callFrame.push(freq instanceof Double d ? PLZStation.voiceAgeMs(d.intValue()) : -1.0);
                return 1;
            }
        });
    }
}
