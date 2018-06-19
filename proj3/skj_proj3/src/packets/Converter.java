package packets;

import java.nio.ByteBuffer;

public class Converter {

    public static int bytesToInt(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return buffer.getInt();
    }

    public static byte[] intToBytes(int i) {
        ByteBuffer buffer = ByteBuffer.allocate(4);
        buffer.putInt(i);
        return buffer.array();
    }



}
