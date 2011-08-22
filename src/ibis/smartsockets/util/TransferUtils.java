package ibis.smartsockets.util;

public class TransferUtils {

    public static void storeShort(short v, byte [] target, int off) {
        target[off++] = (byte)(0xff & (v >> 8));
        target[off]   = (byte)(0xff & v);
    }

    public static short readShort(byte [] source, int off) {
        return (short) (((source[off] & 0xff) << 8)  | (source[off+1] & 0xff));
    }

    public static void storeInt(int v, byte [] target, int off) {
        target[off++] = (byte)(0xff & (v >> 24));
        target[off++] = (byte)(0xff & (v >> 16));
        target[off++] = (byte)(0xff & (v >> 8));
        target[off]   = (byte)(0xff & v);
    }


    public static int readInt(byte [] source, int off) {
        return (((source[off]   & 0xff) << 24) |
                ((source[off+1] & 0xff) << 16) |
                ((source[off+2] & 0xff) << 8)  |
                 (source[off+3] & 0xff));
    }
}
