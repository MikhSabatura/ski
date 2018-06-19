import java.io.IOException;
import java.net.*;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Agent {

    //signals
    private final String GET_COUNT = "GET_COUNT";
    private final String SET_COUNT = "SET_COUNT";
    private final String GET_PERIOD = "GET_PERIOD";
    private final String SET_PERIOD = "SET_PERIOD";
    private final String OK = "OK";

    private final Pattern NUMERIC_PATTERN = Pattern.compile("\\d+");
    private final Pattern SET_COUNT_PATTERN = Pattern.compile(SET_COUNT + "\\s(?<VAL>\\d+)");
    private final Pattern SET_PERIOD_PATTERN = Pattern.compile(SET_PERIOD + "\\s(?<VAL>\\d+)");

    //Agent fields
    private long counterStartPoint;
    private int syncPeriod;

    private DatagramSocket socket;

    private boolean collectingMode; //indicates if the counter values are being gathered or not
    private List<Long> counterVals;
    private String localAddress; //needed in order to avoid getting it every time, which takes too long on mac


    public Agent(long initCounterValue, int syncPeriod) {
        this.counterStartPoint = System.currentTimeMillis() - initCounterValue;
        this.syncPeriod = syncPeriod;
        this.collectingMode = false;
        this.counterVals = new LinkedList<>();
        try {
            this.socket = new DatagramSocket(7777);
            socket.setBroadcast(true);
        } catch (SocketException e) {
            System.err.println("COULDN'T CREATE A SOCKET");
            e.printStackTrace();
        }
        try {
            this.localAddress = InetAddress.getLocalHost().getCanonicalHostName();
            System.out.println("Agent created, address: " + localAddress);
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
    }

    public static void main(String... args) {
        Agent agent = new Agent(Long.parseLong(args[0]), Integer.parseInt(args[1]));
        new Thread(() -> agent.listenForMessages()).start();
        new Thread(() -> agent.updateCounter()).start();

    }

    //directs the numeric messages into counterVals list and does the rest
    private void updateCounter() {
        while (true) {
            try {
                collectingMode = true;
                socket.send(new DatagramPacket(GET_COUNT.getBytes(), GET_COUNT.getBytes().length, InetAddress.getByName("255.255.255.255"), socket.getLocalPort()));
                Thread.sleep(300); //timeout
                collectingMode = false;

                long countSum = counterVals.stream().reduce((a, b) -> a + b).orElse(0L);
                counterStartPoint = System.currentTimeMillis() - ((countSum + getCurrentCounterValue()) / (counterVals.size() + 1));
                System.out.println("Updated counter = " + getCurrentCounterValue() + " synced with " + counterVals.size() + " agents");
                counterVals.clear();

                Thread.sleep(syncPeriod * 1000);
            } catch (Exception e) {
                System.err.println("COULDN'T UPDATE THE COUNTER");
                e.printStackTrace();
            }

        }
    }

    private void listenForMessages() {
        while (true) {
            try {
                byte[] buffer = new byte[256];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                new Thread(() -> processMessage(packet)).start();

            } catch (IOException e) {
                System.err.println("COULDN'T RECEIVE THE PACKET");
            }
        }
    }

    private void processMessage(DatagramPacket packet) {
        String message = new String(packet.getData(), 0, packet.getLength());
        Matcher matcher;

        try {
            if (collectingMode && NUMERIC_PATTERN.matcher(message).matches() && !packet.getAddress().getCanonicalHostName().equals(localAddress)) {
                counterVals.add(Long.parseLong(message));

            } else if ((matcher = SET_COUNT_PATTERN.matcher(message)).matches()) {
                setCounter(Long.parseLong(matcher.group("VAL")), packet.getSocketAddress());

            } else if ((matcher = SET_PERIOD_PATTERN.matcher(message)).matches()) {
                setPeriod(Integer.parseInt(matcher.group("VAL")), packet.getSocketAddress());

            } else if (message.equals(GET_COUNT)) {
                sendCountVal(packet.getSocketAddress());

            } else if (message.equals(GET_PERIOD)) {
                sendSyncPeriod(packet.getSocketAddress());
            }
        } catch (Exception e) {
            System.err.println("COULDN'T PROCESS THE MESSAGE");
            e.printStackTrace();
        }
    }

    private void sendCountVal(SocketAddress address) {
        String message = Long.toString(getCurrentCounterValue());
        try {
            socket.send(new DatagramPacket(message.getBytes(), message.getBytes().length, address));
        } catch (IOException e) {
            System.err.println("COULDN'T SEND THE COUNTER VALUE");
        }
    }

    private void sendSyncPeriod(SocketAddress address) {
        String message = Integer.toString(syncPeriod);
        try {
            socket.send(new DatagramPacket(message.getBytes(), message.getBytes().length, address));
        } catch (IOException e) {
            System.err.println("COULDN'T SEND THE SYNC PERIOD");
        }
    }

    private void setCounter(long val, SocketAddress address) {
        counterStartPoint = System.currentTimeMillis() - val;
        System.out.println("Counter set to " + val);
        try {
            socket.send(new DatagramPacket(OK.getBytes(), OK.getBytes().length, address));
        } catch (IOException e) {
            System.err.println("COULDN'T CONFIRM COUNTER");
        }
    }

    private void setPeriod(int val, SocketAddress address) {
        syncPeriod = val;
        System.out.println("Period set to " + val);
        try {
            socket.send(new DatagramPacket(OK.getBytes(), OK.getBytes().length, address));
        } catch (IOException e) {
            System.err.println("COULDN'T CONFIRM UPDATING PERIOD");
        }
    }

    public long getCurrentCounterValue() {
        return System.currentTimeMillis() - counterStartPoint;
    }

}
