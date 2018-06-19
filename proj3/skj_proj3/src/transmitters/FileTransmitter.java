package transmitters;

import packets.FilePacket;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.Arrays;

public class FileTransmitter extends TransmitterBase<FilePacket> {

    public FileTransmitter(DatagramSocket socket) {
        super(socket);
    }

    @Override
    public FilePacket receivePacket(int timeout) throws IOException {
        byte[] buf = new byte[1045]; // 1024(data) + 16(hash) + 5(header) = 1045
        DatagramPacket datagramPacket = new DatagramPacket(buf, buf.length);

        socket.setSoTimeout(timeout);
        socket.receive(datagramPacket);
        socket.setSoTimeout(0);

        FilePacket filePacket = new FilePacket(datagramPacket);
        return filePacket.isNotCorrupt() ? filePacket : null;
    }


}
