package packets;

import java.net.DatagramPacket;
import java.util.Arrays;

public class FilePacket extends Packet implements Comparable<FilePacket>{

    private int seqNum;

    public FilePacket(DatagramPacket dataPacket) {
        super(dataPacket);
        this.seqNum = Converter.bytesToInt(Arrays.copyOfRange(dataPacket.getData(), 1, headerSize()));
    }

    public FilePacket(byte[] data, int seqNum) {
        super(Packet.FILE, data);
        this.seqNum = seqNum;
        this.hash = md.digest(inputForCheckSum());
        md.reset();
    }

    public FilePacket(byte type) {
        super(type);
        this.seqNum = -1;
        md.digest(inputForCheckSum());
    }

    public FilePacket(byte type, int seqNum) {
        super(type);
        this.seqNum = seqNum;
        this.hash = md.digest(inputForCheckSum());
    }

    public int getSeqNum() {
        return seqNum;
    }

    @Override
    public DatagramPacket pack() {
        byte[] result = new byte[headerSize() + data.length + hash.length];
        result[0] = type;
        System.arraycopy(Converter.intToBytes(seqNum), 0, result, 1, 4);
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
        System.arraycopy(Converter.intToBytes(seqNum), 0, res, 1, 4);
        System.arraycopy(data, 0, res, headerSize(), data.length);

        return res;
    }

    @Override
    int headerSize() {
        return 5;
    }


    @Override
    public int compareTo(FilePacket o) {
        return getSeqNum() - o.getSeqNum();
    }
}
