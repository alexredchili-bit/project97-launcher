package zombie.plz;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;

/** Track length straight from the file, for a dedicated server that has no FMOD to ask. */
public final class PLZOggLength {
    static final int TAIL_BYTES = 65536;
    private static final int HEAD_BYTES = 4096;

    private PLZOggLength() {
    }

    /** Length in milliseconds, or -1 when the file is missing or not Ogg Vorbis, Ogg Opus or PCM WAV. */
    public static long lengthMs(File file) {
        if (file == null || !file.isFile()) {
            return -1L;
        }

        try (RandomAccessFile in = new RandomAccessFile(file, "r")) {
            long size = in.length();
            byte[] head = new byte[(int)Math.min(HEAD_BYTES, size)];
            in.readFully(head);
            if (startsWith(head, 0, "RIFF")) {
                return wavMs(head);
            }

            if (!startsWith(head, 0, "OggS")) {
                return -1L;
            }

            int tailLength = (int)Math.min(TAIL_BYTES, size);
            byte[] tail = new byte[tailLength];
            in.seek(size - tailLength);
            in.readFully(tail);
            return oggMs(head, tail);
        } catch (IOException e) {
            return -1L;
        }
    }

    static long oggMs(byte[] head, byte[] tail) {
        int body = 27 + (head.length > 26 ? head[26] & 0xFF : 0);
        long rate;
        long preSkip = 0L;
        if (head.length > body + 16 && head[body] == 1 && startsWith(head, body + 1, "vorbis")) {
            rate = le32(head, body + 12);
        } else if (head.length > body + 12 && startsWith(head, body, "OpusHead")) {
            rate = 48000L;
            preSkip = le16(head, body + 10);
        } else {
            return -1L;
        }

        if (rate <= 0L) {
            return -1L;
        }

        long granule = lastGranule(tail);
        if (granule < 0L) {
            return -1L;
        }

        long samples = Math.max(0L, granule - preSkip);
        return samples * 1000L / rate;
    }

    static long lastGranule(byte[] tail) {
        for (int i = tail.length - 27; i >= 0; i--) {
            if (tail[i] == 'O' && tail[i + 1] == 'g' && tail[i + 2] == 'g' && tail[i + 3] == 'S' && tail[i + 4] == 0) {
                long granule = le64(tail, i + 6);
                if (granule >= 0L) {
                    return granule;
                }
            }
        }

        return -1L;
    }

    static long wavMs(byte[] head) {
        if (!startsWith(head, 8, "WAVE")) {
            return -1L;
        }

        long byteRate = -1L;
        int p = 12;
        while (p + 8 <= head.length) {
            long chunkSize = le32(head, p + 4);
            if (startsWith(head, p, "fmt ") && p + 16 <= head.length) {
                byteRate = le32(head, p + 16);
            } else if (startsWith(head, p, "data")) {
                return byteRate > 0L ? chunkSize * 1000L / byteRate : -1L;
            }

            p += 8 + (int)chunkSize + (int)(chunkSize & 1L);
        }

        return -1L;
    }

    private static boolean startsWith(byte[] data, int offset, String text) {
        byte[] want = text.getBytes(StandardCharsets.US_ASCII);
        if (offset < 0 || offset + want.length > data.length) {
            return false;
        }

        for (int i = 0; i < want.length; i++) {
            if (data[offset + i] != want[i]) {
                return false;
            }
        }

        return true;
    }

    private static long le16(byte[] d, int o) {
        return (d[o] & 0xFFL) | (d[o + 1] & 0xFFL) << 8;
    }

    private static long le32(byte[] d, int o) {
        return (d[o] & 0xFFL) | (d[o + 1] & 0xFFL) << 8 | (d[o + 2] & 0xFFL) << 16 | (d[o + 3] & 0xFFL) << 24;
    }

    private static long le64(byte[] d, int o) {
        return le32(d, o) | le32(d, o + 4) << 32;
    }
}
