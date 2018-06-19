import java.io.IOException;
import java.net.*;
import java.util.Arrays;

public class Controller {

    //signals
    private final String GET_COUNT = "GET_COUNT";
    private final String SET_COUNT = "SET_COUNT";
    private final String GET_PERIOD = "GET_PERIOD";
    private final String SET_PERIOD = "SET_PERIOD";
    private final String OK = "OK";

    private DatagramSocket socket;
    private InetAddress target;
    private String[] command;

    public Controller(String[] args) {
        try {
            this.target = InetAddress.getByName(args[0]);
            this.command = Arrays.asList(args).subList(1, args.length).toArray(args); //array without the first element
            this.socket = new DatagramSocket();
        } catch (IndexOutOfBoundsException e) {
            System.err.println("ILLEGAL ARGUMENTS");
        } catch (UnknownHostException e) {
            System.err.println("ILLEGAL TARGET ADDRESS");
        } catch (SocketException e) {
            System.err.println("COULDN'T CREATE A SOCKET");
        }
    }

    public static void main(String... args) {
        Controller controller = new Controller(args);
        controller.execute();
    }

    public void execute() {
        try {
            switch (command[0]) {
                case "get":
                    switch (command[1]) {
                        case "counter":
                            System.out.println("The Agent's counter = " + getCounter());
                            break;
                        case "period":
                            System.out.println("The Agent's synchronization period = " + getSyncPeriod());
                            break;
                        default:
                            throw new IllegalArgumentException();
                    }
                    break;
                case "set":
                    switch (command[1]) {
                        case "counter":
                            setCounter(Long.parseLong(command[2]));
                            break;
                        case "period":
                            setSyncPeriod(Integer.parseInt(command[2]));
                            break;
                        default:
                            throw new IllegalArgumentException();
                    }
                    break;
                default:
                    throw new IllegalArgumentException();
            }
        } catch (Exception e) {
            System.err.println("ILLEGAL ARGUMENT");
            e.printStackTrace();
        }

    }

    private long getCounter() {
        int resendCount = 0;
        while (resendCount < 3) {
            try {
                DatagramPacket packet = new DatagramPacket(GET_COUNT.getBytes(), GET_COUNT.getBytes().length, target, 7777);
                socket.send(packet);

                byte[] buf = new byte[256];
                packet = new DatagramPacket(buf, buf.length);
                socket.setSoTimeout(100);
                socket.receive(packet);
                return Long.parseLong(new String(packet.getData(), 0, packet.getLength()));
            } catch (SocketTimeoutException | NumberFormatException e) {
                resendCount++;
            } catch (IOException e) {
                System.err.println("COULDN'T GET COUNTER VALUE");
                System.exit(1);
            }
        }
        System.out.println("The Agent didn't respond");
        System.exit(0);
        return -1;
    }

    private int getSyncPeriod() {
        int resendCount = 0;
        while (resendCount < 3) {
            try {
                DatagramPacket packet = new DatagramPacket(GET_PERIOD.getBytes(), GET_PERIOD.getBytes().length, target, 7777);
                socket.send(packet);

                byte[] buf = new byte[256];
                packet = new DatagramPacket(buf, buf.length);
                socket.setSoTimeout(100);
                socket.receive(packet);
                return Integer.parseInt(new String(packet.getData(), 0, packet.getLength()));
            } catch (SocketTimeoutException | NumberFormatException e) {
                resendCount++;
            } catch (IOException e) {
                System.err.println("COULDN'T GET COUNTER VALUE");
                System.exit(1);
            }
        }
        System.out.println("The Agent didn't respond");
        System.exit(0);
        return -1;
    }

    private void setCounter(long val) {
        int resendCount = 0;
        while (resendCount < 3) {
            try {
                String message = SET_COUNT + " " + val;
                DatagramPacket packet = new DatagramPacket(message.getBytes(), message.getBytes().length, target, 7777);
                socket.send(packet);

                byte[] buf = new byte[256];
                packet = new DatagramPacket(buf, buf.length);
                socket.setSoTimeout(100);
                socket.receive(packet);

                message = new String(packet.getData(), 0, packet.getLength());
                if (message.equals(OK)) {
                    System.out.println("Agent's counter set to " + val);
                    return;
                }
                resendCount++;
            } catch (SocketTimeoutException e) {
                resendCount++;
            } catch (IOException e) {
                System.err.println("COULDN'T GET COUNTER VALUE");
                System.exit(1);
            }
        }
        System.out.println("The Agent didn't respond");
    }

    private void setSyncPeriod(int val) {
        int resendCount = 0;
        while (resendCount < 3) {
            try {
                String message = SET_PERIOD + " " + val;
                DatagramPacket packet = new DatagramPacket(message.getBytes(), message.getBytes().length, target, 7777);
                socket.send(packet);

                byte[] buf = new byte[256];
                packet = new DatagramPacket(buf, buf.length);
                socket.setSoTimeout(100);
                socket.receive(packet);

                message = new String(packet.getData(), 0, packet.getLength());
                if (message.equals(OK)) {
                    System.out.println("Agent's synchronization period set to " + val);
                    return;
                }
                resendCount++;
            } catch (SocketTimeoutException e) {
                resendCount++;
            } catch (IOException e) {
                System.err.println("COULDN'T GET COUNTER VALUE");
                System.exit(1);
            }
        }
        System.out.println("The Agent didn't respond");
    }

}
