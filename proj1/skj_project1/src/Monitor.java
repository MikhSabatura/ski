import java.net.InetSocketAddress;
import java.net.Socket;
import java.io.*;
import java.util.*;

public class Monitor {

    private Map<Integer, InetSocketAddress> agents;
    private Map<Integer, Long> clocks;

    public static void main(String... args) {
        Monitor monitor = new Monitor(args);

        while(true) {
            monitor.loadNetwork();
            monitor.printNetwork();
            try {
                Thread.sleep(800);
            } catch (Exception e){}
        }

    }

    public Monitor(String[] args) {
        agents = new HashMap<>();
        clocks = new HashMap<>();
        try {
            Socket socket = new Socket(args[0], Integer.parseInt(args[1]));
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

            out.writeObject(Communicate.ID);
            out.flush();
            int id = in.readInt();
            agents.put(id, (InetSocketAddress) socket.getRemoteSocketAddress());
            loadNetwork();
        } catch (Exception e) {
            System.err.println("ILLEGAL ARGS");
            e.printStackTrace();
        }
    }

    public void loadNetwork() {
        int id = new ArrayList<>(agents.keySet()).get(0);

        try {
            Socket socket = new Socket(agents.get(id).getAddress(), agents.get(id).getPort());
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

            out.writeObject(Communicate.NET);
            out.flush();
            agents = (Map<Integer, InetSocketAddress>) in.readObject();
            agents.put(id, (InetSocketAddress)socket.getRemoteSocketAddress());

            out.close();
            in.close();
            socket.close();

            for(Map.Entry<Integer, InetSocketAddress> e : agents.entrySet()) {
                socket = new Socket(e.getValue().getAddress(), e.getValue().getPort());
                out = new ObjectOutputStream(socket.getOutputStream());
                in = new ObjectInputStream(socket.getInputStream());

                out.writeObject(Communicate.CLK);
                out.flush();
                long clock  = in.readLong();

                clocks.put(e.getKey(), clock);

                out.close();
                in.close();
                socket.close();
            }
        } catch (Exception e) {
            System.err.println("COULDN'T LOAD THE NETWORK");
            e.printStackTrace();
        }


    }

    public void printNetwork() {
        String format = "| %-4s | %-52s | %-20d|\n";

        System.out.printf("+------+------------------------------------------------------+---------------------+\n");
        System.out.printf("|ID    |                    IP : PORT                         |         CLOCK       |\n");
        System.out.printf("+------+------------------------------------------------------+---------------------+\n");

        for(Map.Entry<Integer, InetSocketAddress> e : agents.entrySet()) {
            System.out.printf(format, e.getKey(), e.getValue().getAddress() + " : " + e.getValue().getPort(), clocks.get(e.getKey()));
//            System.out.println(e.getKey() + "|  " + e.getValue() + "           " + clocks.get(e.getKey()));
        }
        System.out.printf("+------+------------------------------------------------------+---------------------+\n\n\n\n");



    }

}
