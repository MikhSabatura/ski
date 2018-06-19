package transmitters;

import packets.Packet;

import java.io.IOException;
import java.net.DatagramSocket;

public abstract class TransmitterBase<T extends Packet> {

    protected DatagramSocket socket;

    public TransmitterBase(DatagramSocket socket) {
        this.socket = socket;
    }

    abstract T receivePacket(int timeout) throws IOException;

    public void sendPacket(T packet) throws IOException {
        socket.send(packet.pack());
    }

}
