package qonduit.utils;

public class ByteArrayUtils {

    public static byte[] copy(byte[] in) {
        byte[] out = new byte[in.length];
        System.arraycopy(in, 0, out, 0, in.length);
        return out;
    }

}
