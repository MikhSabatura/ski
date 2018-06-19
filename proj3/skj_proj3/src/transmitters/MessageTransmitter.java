package transmitters;

import packets.MessagePacket;
import packets.Packet;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

public class MessageTransmitter extends TransmitterBase<MessagePacket> {

    public MessageTransmitter(DatagramSocket socket) {
        super(socket);
    }

    public MessagePacket waitForPacket(byte type) throws IOException {
        byte[] buf = new byte[256];
        DatagramPacket rawPacket = new DatagramPacket(buf, buf.length);

        while (true) {
            socket.receive(rawPacket);
            MessagePacket messagePacket = new MessagePacket(rawPacket);
            if (!messagePacket.isCorrupt() && messagePacket.getType() == type) {
                return messagePacket;
            }
        }
    }

    // returns null in case the packet is corrupt
    @Override
    public MessagePacket receivePacket(int timeout) throws IOException {
        byte[] buf = new byte[256];
        DatagramPacket datagramPacket = new DatagramPacket(buf, buf.length);

        socket.setSoTimeout(timeout);
        socket.receive(datagramPacket);
        socket.setSoTimeout(0);

        MessagePacket messagePacket = new MessagePacket(datagramPacket);

        return messagePacket.isNotCorrupt() ? messagePacket : null;
    }


}
