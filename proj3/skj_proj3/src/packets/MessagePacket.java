package packets;

import java.net.DatagramPacket;
import java.util.Arrays;

public class MessagePacket extends Packet {

    public MessagePacket(DatagramPacket dataPacket) {
        super(dataPacket);
    }

    public MessagePacket(byte type, byte[] data) {
        super(type, data);
    }

    public MessagePacket(byte type) {
        super(type);
    }

    public MessagePacket(byte type, int val) {
        super(type, Converter.intToBytes(val));
    }

    @Override
    public DatagramPacket pack() {
        byte[] result = new byte[headerSize() + data.length + hash.length];
        result[0] = type;
        System.arraycopy(data, 0, result, headerSize(), data.length);
        System.arraycopy(hash, 0, result, headerSize() + data.length, hash.length);
        return new DatagramPacket(result, result.length);
    }

    @Override
    byte[] extractData(byte[] rawBytes) {
        return Arrays.copyOfRange(rawBytes, headerSize(), rawBytes.length - 16);
    }

    @Override
    byte[] inputForCheckSum() {
        byte[] res = new byte[data.length + headerSize()];
        res[0] = type;
        System.arraycopy(data, 0, res, headerSize(), data.length);
        return res;
    }

    @Override
    int headerSize() {
        return 1;
    }
}
