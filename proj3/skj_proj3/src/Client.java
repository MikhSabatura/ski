import packets.Converter;
import packets.FilePacket;
import packets.MessagePacket;
import packets.Packet;
import transmitters.FileTransmitter;
import transmitters.MessageTransmitter;

import javax.xml.bind.DatatypeConverter;
import java.io.*;
import java.net.*;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

// sends the data
public class Client {
    // adjust the speed by changing the frequency of sending the packets
    // compare time before and after sending and calculate the sleep time based on that

    private int serverPort;
    private InetAddress serverAddress;

    private int speed;
    private File file;

    private DatagramSocket fileSocket;
    private DatagramSocket messageSocket;

    private MessageTransmitter mesTrans;
    private FileTransmitter fileTrans;

    private MessageDigest md; // needed for calculation of the whole file's hash

    boolean endReached = false;
    boolean collectingAcks = false;

    public Client(String[] args) {
        processInitArgs(args);
        try {
            this.fileSocket = new DatagramSocket();
            this.messageSocket = new DatagramSocket(7770);

            this.md = MessageDigest.getInstance("MD5");
            this.fileTrans = new FileTransmitter(fileSocket);
            this.mesTrans = new MessageTransmitter(messageSocket);

            // connecting the sockets to server's sockets
            this.fileSocket.connect(serverAddress, serverPort);
            this.messageSocket.connect(serverAddress, 7771);
        } catch (SocketException e) {
            System.err.println("COULDN'T CREATE A MESSAGE SOCKET");
        } catch (NoSuchAlgorithmException e) {
            System.err.println("CHECKSUM ALGORITHM NOT FOUND");
        }


        System.out.printf("Client initialized\nFile to sendPacket: %s\nServer address: %s\n",
                file.getName(), serverAddress.getHostAddress() + ":" + serverPort);

    }

    public static void main(String... args) {
        Client client = new Client(args);
        client.connectToServer();
        new Thread(() -> client.sendFile()).start();
    }

    private void processInitArgs(String[] args) {
        if (args.length > 0 && args.length != 6) {
            System.out.printf("Client: illegal number of arguments, 6 are required instead of %d\n", args.length);
            System.exit(1);
        }

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-port":
                    try {
                        serverPort = Integer.parseInt(args[++i]);
                    } catch (NumberFormatException e) {
                        System.out.printf("Client: '%s' is not a valid port number\n", args[i]);
                        System.exit(1);
                    }
                    break;
                case "-server":
                    try {
                        serverAddress = InetAddress.getByName(args[++i]);
                    } catch (Exception e) {
                        System.out.printf("Client: '%s' is not a valid IP address\n", args[i]);
                        System.exit(1);
                    }
                    break;
                case "-file":
                    file = new File(args[++i]);
                    if (!file.exists()) {
                        System.out.printf("Client: the file '%s' doesn't exist\n", args[i]);
                        System.exit(1);
                    }
                    if (file.isDirectory()) {
                        System.out.printf("Client: the given file '%s' is a directory\n", args[i]);
                        System.exit(1);
                    }
                    break;
                default:
                    System.out.printf("Client: '%s' is not a valid command.\n", args[i]);
                    System.exit(1);
            }
        }
    }

    private void connectToServer() {
        MessagePacket messagePacket = new MessagePacket(Packet.START, fileSocket.getLocalPort());
        MessagePacket response;

        int resendCount = 0;
        while (resendCount < 5) {
            try {
                mesTrans.sendPacket(messagePacket);
                response = mesTrans.receivePacket(300);

                // in case the packet is corrupt, retransmit
                if (response == null) {
                    resendCount++;
                    continue;
                }
                // end in case right type of packet is received
                if (response.getType() == Packet.OK) {
                    sendFileInfo();
                    return;
                }
            } catch (SocketTimeoutException e) {
                resendCount++;
            } catch (IOException e) {
                System.err.println("COULDN'T CONNECT TO THE SERVER");
            }
        }
        System.err.println("COULDN'T ESTABLISH CONNECTION WITH THE SERVER");
        System.exit(1);
    }

    private void sendFileInfo() {
        MessagePacket fileInfo = new MessagePacket(Packet.FILE, file.getName().getBytes());
        MessagePacket response;

        System.out.println("Sending file info...");
        int resendCount = 0;
        while (resendCount < 5) {
            try {
                mesTrans.sendPacket(fileInfo);
                response = mesTrans.receivePacket(300);

                if (response == null) {
                    resendCount++;
                    continue;
                }
                if (response.getType() == Packet.SPEED) {
                    System.out.println("The requested speed is " + Converter.bytesToInt(response.getData()));
                    setSpeed(Converter.bytesToInt(response.getData()));
                    mesTrans.sendPacket(new MessagePacket(Packet.OK));
                    return;
                }
            } catch (SocketTimeoutException e) {
                resendCount++;
            } catch (IOException e) {
                System.err.println("COULDN'T SEND THE FILE INFO");
            }
        }
        System.err.println("THE SERVER STOPPED RESPONDING");
        System.exit(1);
    }

    private void sendFile() {
        try (DigestInputStream inStream = new DigestInputStream(new FileInputStream(file), md)) {
            List<FilePacket> segment;

            while (!endReached) {
                segment = nextSegment(inStream);
                sendSegment(segment);
            }
        } catch (IOException e) {
            System.err.println("ERROR WHEN SENDING THE FILE");
        }
        finishSending();
    }

    private void finishSending() {
        int resendCount = 0;
        byte[] hash = md.digest();
        while (resendCount < 5) {
            try {
                MessagePacket checksum = mesTrans.receivePacket(300);
                if (checksum == null) {
                    continue;
                }
                if (checksum.getType() == Packet.END && Arrays.equals(checksum.getData(), hash)) {
                    mesTrans.sendPacket(new MessagePacket(Packet.OK));
                    break;
                }
            } catch (SocketTimeoutException e) {
                resendCount++;
            } catch (IOException e) {
                System.err.println("ERROR WHEN FINISHING THE TRANSMISSION");
            }
        }
        if (resendCount >= 5) {
            System.err.println("COULDN'T CLOSE THE CONNECTION");
            System.exit(1);
        }
        System.out.println("The file successfully transmitted");
        System.out.println("MD5: " + DatatypeConverter
                .printHexBinary(hash).toLowerCase());
    }

    private List<FilePacket> nextSegment(InputStream in) {
        List<FilePacket> seg = new ArrayList<>(20);

        for (int i = 0; i < 20; i++) {
            byte[] bytes = new byte[1024];
            try {
                int read = 0;
                if ((read = in.read(bytes)) >= 0) {
                    bytes = Arrays.copyOfRange(bytes, 0, read);

                    seg.add(new FilePacket(bytes, i));
                } else {
                    seg.add(new FilePacket(Packet.END, i));
                    endReached = true;
                    break;
                }
            } catch (IOException e) {
                System.err.println("ERROR WHEN READING THE FILE");
            }
        }
        return seg;
    }

    private void sendSegment(List<FilePacket> packets) {
        int currSeqNum = 0;
        int nothingReceivedCount = 0;

        while (currSeqNum < packets.size() && nothingReceivedCount < 5) {
            List<Integer> acks = new LinkedList<>();

            for (int i = currSeqNum; i < packets.size(); i++) {
                try {
                    fileTrans.sendPacket(packets.get(i));
                } catch (IOException e) {
                    System.err.println("ERROR WHEN SENDING THE PACKET");
                    System.exit(1);
                }
            }

            new Thread(this::collectAcks).start();

            while (isCollectingAcks()) {
                FilePacket ackPacket = null;
                try {
                    ackPacket = fileTrans.receivePacket(100);
                } catch (SocketTimeoutException e) {
                } catch (IOException e) {
                    System.err.println("ERROR WHEN WAITING FOR RESPONSE");
                }
                if (ackPacket != null && ackPacket.getType() == Packet.OK) {
                    acks.add(ackPacket.getSeqNum());
                }
            }
            if (acks.isEmpty()) {
                nothingReceivedCount++;
                continue;
            }
            nothingReceivedCount = 0;
            Collections.sort(acks);
            for (int i : acks) {
                if (i == currSeqNum) {
                    currSeqNum++;
                }
            }
        }
        if (nothingReceivedCount == 5) {
            System.err.println("THE SERVER DOESN'T RESPOND");
            System.exit(1);
        }
    }

    private synchronized int getSpeed() {
        return speed;
    }

    private synchronized void setSpeed(int speed) {
        this.speed = speed;
    }

    private void collectAcks() {
        synchronized (this) {
            collectingAcks = true;
        }
        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            System.err.println("ERROR WHEN COLLECTING ACKS");
        }
        synchronized (this) {
            collectingAcks = false;
        }
    }

    private synchronized boolean isCollectingAcks() {
        return collectingAcks;
    }

}
