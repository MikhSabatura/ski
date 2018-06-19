package participants;

import packets.Converter;
import packets.FilePacket;
import packets.MessagePacket;
import packets.Packet;
import transmitters.FileTransmitter;
import transmitters.MessageTransmitter;

import javax.xml.bind.DatatypeConverter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.*;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

// receives the data
public class Server {
    //todo: thread for taking the input for adjusting the speed + sending according messages to the client

    private int speed;

    private File file;

    private DatagramSocket fileSocket;
    private DatagramSocket messageSocket;

    private MessageTransmitter mesTrans;
    private FileTransmitter fileTrans;

    private MessageDigest md; // needed for calculation of the whole file's hash

    boolean endReached = false;
    boolean collectingMode = false;

    public Server(String[] args) {
        processInitArgs(args);
        try {
            this.messageSocket = new DatagramSocket(7771);

            this.md = MessageDigest.getInstance("MD5");
            this.mesTrans = new MessageTransmitter(messageSocket);
            this.fileTrans = new FileTransmitter(fileSocket);
        } catch (SocketException e) {
            System.err.println("COULDN'T CREATE A MESSAGE SOCKET");
            System.exit(1);
        } catch (NoSuchAlgorithmException e) {
            System.err.println("CHECKSUM ALGORITHM NOT FOUND");
            System.exit(1);
        }

        try {
            System.out.printf("participants.Server created\nAddress: %s\nRequested speed: %d kb\\s\n",
                    InetAddress.getLocalHost().getHostAddress(), speed);
        } catch (UnknownHostException e) {
            System.err.println("COULDN'T GET LOCALHOST ADDRESS");
        }
    }

    public static void main(String... args) {
        Server server = new Server(args);
        server.getClientConnection();
        new Thread(() -> server.receiveFile()).start();
    }

    private void processInitArgs(String[] args) {
        if (args.length != 4) {
            System.out.printf("participants.Server: illegal number of arguments, 4 are required instead of %d\n", args.length);
            System.exit(1);
        }

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-port":
                    try {
                        fileSocket = new DatagramSocket(Integer.parseInt(args[++i]));
                    } catch (NumberFormatException e) {
                        System.out.printf("participants.Server: '%s' is not a valid port number\n", args[i]);
                        System.exit(1);
                    } catch (SocketException e) {
                        System.err.println("COULDN'T CREATE A FILE SOCKET");
                        System.exit(1);
                    }
                    break;
                case "-speed":
                    try {
                        speed = Integer.parseInt(args[++i]);
                    } catch (NumberFormatException e) {
                        System.out.printf("participants.Server: '%s' is not a valid speed\n", args[i]);
                        System.exit(1);
                    }
                    break;
                default:
                    System.out.printf("participants.Server: '%s' is not a valid command.\n", args[i]);
                    System.exit(1);
            }
        }
    }


    private void getClientConnection() {
        MessagePacket messagePacket;

        System.out.println("\nWaiting for client to connect...");
        try {
            //waiting for request and connecting
            messagePacket = mesTrans.waitForPacket(Packet.START);
            fileSocket.connect(messagePacket.getSourceInetAddress(), Converter.bytesToInt(messagePacket.getData()));
            messageSocket.connect(messagePacket.getSourceInetAddress(), 7770);
            System.out.println("participants.Client connected " + fileSocket.getInetAddress());

            file = new File(receiveFileInfo());
            System.out.println("File name: " + file.getName());

            new Thread(this::ensureConnectionEstablished).start();
        } catch (IOException e) {
            System.err.println("COULDN'T ESTABLISH CONNECTION WITH A CLIENT");
            System.exit(1);
        }

    }

    private void finishReceiving() {
        int resendCount = 0;
        while (resendCount < 5) {
            try {
                byte[] hash = md.digest();
                mesTrans.sendPacket(new MessagePacket(Packet.END, hash));
                MessagePacket response = mesTrans.receivePacket(300);
                if (response == null) {
                    continue;
                }
                if (response.getType() == Packet.OK) {
                    System.out.println("The file successfully downloaded");
                    System.out.println("MD5: " + DatatypeConverter
                            .printHexBinary(hash).toLowerCase());
                }
            } catch (SocketTimeoutException e) {
                resendCount++;
            } catch (IOException e) {
                System.err.println("ERROR WHEN CLOSING THE CONNECTION");
                System.exit(1);
            }
        }
    }

    public String receiveFileInfo() {
        System.out.println("\nReceiving file info...");
        MessagePacket infoPacket;

        int resendCount = 0;
        while (resendCount < 5) {
            try {
                mesTrans.sendPacket(new MessagePacket(Packet.OK));
                infoPacket = mesTrans.receivePacket(300);

                if (infoPacket == null) {
                    resendCount++;
                    continue;
                }
                if (infoPacket.getType() == Packet.FILE) {
                    return new String(infoPacket.getData());
                }
            } catch (SocketTimeoutException e) {
                resendCount++;
            } catch (IOException e) {
                System.err.println("COULDN'T GET THE FILE INFO");
                System.exit(1);
            }
        }
        System.err.println("THE CLIENT DISCONNECTED");
        System.exit(1);
        return null; // completely pointless line
    }

    private void ensureConnectionEstablished() {
        int count = 0;

        while (count < 5) {
            try {
                MessagePacket packet = mesTrans.receivePacket(300);
                if (packet.getType() == Packet.OK) {
                    return;
                }
            } catch (SocketTimeoutException e) {
                count++;
            } catch (IOException e) {
                System.err.println("ERROR WHEN ESTABLISHING CONNECTION");
                System.exit(1);
            }
            sendSpeed();
        }
        System.err.println("THE CLIENT STOPPED RESPONDING");
        System.exit(1);
    }

    private void receiveFile() {
        System.out.println("Downloading the file...");
        try (DigestOutputStream outStream = new DigestOutputStream(new FileOutputStream(file), md)) {
            Thread saveTrhead = null;
            while (!endReached) {
                List<FilePacket> segment = receiveSegment();
                saveTrhead = new Thread(() -> saveSegment(segment, outStream)); //in order to save time
                saveTrhead.start();
            }
            saveTrhead.join();
        } catch (IOException | InterruptedException e) {
            System.err.println("ERROR WHEN WRITING TO FILE");
        }
        finishReceiving();
    }

    private synchronized void saveSegment(List<FilePacket> segment, OutputStream out) {
        Collections.sort(segment);
        for (Packet p : segment) {
            try {
                if (p.getType() == Packet.END) {
                    return;
                }

                if (p.getType() == Packet.FILE) {
                    out.write(p.getData());
                    out.flush();
                }
            } catch (IOException e) {
                e.printStackTrace();
                System.err.println("ERROR WHEN SAVING FILE");
                System.exit(1);
            }
        }
    }

    private List<FilePacket> receiveSegment() {

        int currSeqNum = 0;
        int nothingReceivedCount = 0;
        List<FilePacket> packets = new LinkedList<>();

        while (currSeqNum < 20 && nothingReceivedCount < 5) {
            new Thread(this::collectPackets).start();

            boolean nothingReceived = true;

            while (collectingModeOn()) {
                FilePacket packet = null;
                try {
                    packet = fileTrans.receivePacket(100);
                } catch (SocketTimeoutException e) {
                    nothingReceivedCount++;
                } catch (IOException e) {
                    System.err.println("ERROR WHEN RECEIVING THE FILE");
                }

                if (packet == null) {
                    continue;
                }
                if (packet.getSeqNum() == currSeqNum) {

                    if (packet.getType() == Packet.END) {
                        endReached = true;
                    }
                    packets.add(packet);
                    try {
                        fileTrans.sendPacket(new FilePacket(Packet.OK, currSeqNum));
                    } catch (IOException e) {
                        System.err.println("ERROR WHEN SENDING ACKNOWLEDGE");
                        System.exit(1);
                    }
                    currSeqNum++;
                    nothingReceived = false;
                    nothingReceivedCount = 0;
                }
                if (nothingReceived) {
                    nothingReceivedCount++;
                }
            }
        }
        if (nothingReceivedCount >= 5) {
            if (endReached) {
                return packets;
            }
            System.err.println("THE CLIENT STOPPED RESPONDING");
            System.exit(1);
        }
        return packets;
    }

    private void sendSpeed() {
        try {
            mesTrans.sendPacket(new MessagePacket(Packet.SPEED, getSpeed()));
        } catch (IOException e) {
            System.err.println("COULDN'T SEND SPEED");
            System.exit(1);
        }
    }

    private synchronized int getSpeed() {
        return speed;
    }

    private synchronized void setSpeed(int speed) {
        this.speed = speed;
    }

    private void collectPackets() {
        synchronized (this) {
            collectingMode = true;
        }
        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            System.err.println("ERROR WHEN COLLECTING PACKETS");
        }
        synchronized (this) {
            collectingMode = false;
        }
    }

    private synchronized boolean collectingModeOn() {
        return collectingMode;
    }


}
