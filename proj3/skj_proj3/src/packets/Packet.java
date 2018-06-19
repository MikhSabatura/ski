package packets;

import javax.xml.bind.DatatypeConverter;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

public abstract class Packet {

    // packet types
    public final static byte START = 0;
    public final static byte OK = 1;
    public final static byte FILE = 3;
    public final static byte SPEED = 7;
    public final static byte END = 9;

    protected byte type;

    protected byte[] data;
    protected byte[] hash;

    protected static MessageDigest md;

    private InetAddress inetAddr;
    private int sourcePort;

    static {
        try {
            md = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            System.err.println("CHECKSUM ALGORITHM NOT RECOGNIZED");
        }
    }

    public Packet(DatagramPacket datagramPacket) {
        byte[] rawBytes = Arrays.copyOfRange(datagramPacket.getData(), 0, datagramPacket.getLength());

        this.inetAddr = datagramPacket.getAddress();
        this.sourcePort = datagramPacket.getPort();
        this.type = rawBytes[0];
        this.data = extractData(rawBytes);
        this.hash = extractCheckSum(rawBytes);
    }

    public Packet(byte type, byte[] data) {
        this.type = type;
        this.data = data;
        this.hash = md.digest(inputForCheckSum());
        md.reset();
    }

    public Packet(byte type) {
        this(type, new byte[0]);
    }

    public abstract DatagramPacket pack();

    public boolean isNotCorrupt() {
        return checkIntegrity(this);
    }

    public boolean isCorrupt() {
        return !checkIntegrity(this);
    }

    public static boolean checkIntegrity(Packet packet) {
        boolean res = Arrays.equals(md.digest(packet.inputForCheckSum()), packet.hash);
        md.reset();
        return res;
    }

    abstract byte[] extractData(byte[] rawBytes);

    abstract byte[] inputForCheckSum();

    public byte[] extractCheckSum(byte[] rawBytes) {
        return Arrays.copyOfRange(rawBytes, rawBytes.length - 16, rawBytes.length);
    }

    public byte getType() {
        return type;
    }

    public byte[] getData() {
        return data;
    }

    public byte[] getHash() {
        return hash;
    }

    public InetAddress getSourceInetAddress() {
        return inetAddr;
    }

    public int getSourcePort() {
        return sourcePort;
    }

    abstract int headerSize();

}
