import org.junit.Assert;
import org.junit.Test;
import packets.FilePacket;
import packets.MessagePacket;
import packets.Packet;
import transmitters.FileTransmitter;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

public class PacketTest {

    @Test
    public void testMd() throws NoSuchAlgorithmException {
        String message = "test";
        MessageDigest md = MessageDigest.getInstance("MD5");

        MessagePacket packet = new MessagePacket(Packet.OK, message.getBytes());
        byte[] c = new byte[1 + packet.getData().length];
        c[0] = packet.getType();
        System.arraycopy(packet.getData(), 0, c, 1, packet.getData().length);

        Assert.assertTrue(Arrays.equals(packet.getHash(), md.digest(c)));
    }

    @Test
    public void testPacketConstructor() throws Exception{
        String message = "test";
        MessageDigest md = MessageDigest.getInstance("MD5");

        MessagePacket expected = new MessagePacket(Packet.OK, message.getBytes());

        DatagramPacket datagramPacket = expected.pack();
        MessagePacket actual = new MessagePacket(datagramPacket);

        Assert.assertArrayEquals(expected.getData(), actual.getData());
        Assert.assertArrayEquals(expected.getHash(), actual.getHash());
        Assert.assertEquals(expected.getType(), actual.getType());
    }

    @Test
    public void testFilePacketConstr() throws Exception{
        String message = "test";
        MessageDigest md = MessageDigest.getInstance("MD5");

        FilePacket expected = new FilePacket(message.getBytes(), 0);
        DatagramPacket datagramPacket = expected.pack();
        FilePacket actual = new FilePacket(datagramPacket);

        Assert.assertArrayEquals(expected.getData(), actual.getData());
        Assert.assertArrayEquals(expected.getHash(), actual.getHash());
        Assert.assertEquals(expected.getType(), actual.getType());
    }

    @Test
    public void integrityCheckTest() throws Exception{
        String message = "test";
        MessageDigest md = MessageDigest.getInstance("MD5");

        MessagePacket packet = new MessagePacket(Packet.OK, message.getBytes());
        FilePacket filePacket = new FilePacket(message.getBytes(), 0);

        Assert.assertFalse(packet.isCorrupt());
        Assert.assertFalse(filePacket.isCorrupt());
    }

    @Test
    public void sendingOverNetworkTest() throws IOException {
        DatagramSocket socket = new DatagramSocket(8888);
        FileTransmitter transmitter = new FileTransmitter(socket);

        DatagramSocket receivingSocket = new DatagramSocket(7777);
        socket.connect(InetAddress.getByName("localhost"), 7777);
        FileTransmitter receivingTransmitter = new FileTransmitter(receivingSocket);


        for(int i = 0; i < 5; i++) {
            FilePacket packet = new FilePacket(new byte[1024], 1);

            transmitter.sendPacket(packet);
            FilePacket received = receivingTransmitter.receivePacket(500);

            Assert.assertFalse(received.isCorrupt());
            Assert.assertTrue(received.isNotCorrupt());
            Assert.assertArrayEquals(packet.getData(), received.getData());
            Assert.assertArrayEquals(packet.getHash(), received.getHash());
        }
    }


}
