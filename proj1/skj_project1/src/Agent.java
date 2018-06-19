import java.io.*;
import java.net.*;
import java.util.*;

public class Agent {

    public static void main(String... args) {
        System.out.println("Creating the agent...");
        Agent agent;

        try {
            if(args.length == 0) {
                agent = new Agent();
            } else {
                agent = new Agent(args[0], Integer.parseInt(args[1]));
            }
            while(true) {
                new Thread(new ClientThread(agent, agent.serverSocket.accept())).start();
            }
        } catch (Exception e) {
            System.err.println("WRONG ARGUMENTS");
            e.printStackTrace();
        }


    }

    private int ID;
    private ServerSocket serverSocket; //listening socket

    private Map<Integer, Socket> agents;
    private Map<Integer, ObjectInputStream> inputStreams; // in order to avoid creating new streams
    private Map<Integer, ObjectOutputStream> outputStreams;

    private long clockStartTime;

    public Agent() {
        this.ID = 0;
        try {
            this.serverSocket = new ServerSocket(7777);
            this.agents = new HashMap<>();
            this.outputStreams = new HashMap<>();
            this.inputStreams = new HashMap<>();
            this.clockStartTime = System.currentTimeMillis();
        } catch (Exception e) {
            System.err.println("PROBLEM WITH CREATING SERVER SOCKET");
            e.printStackTrace();
        }
        System.out.println("Agent #" + ID + " created, address: " + serverSocket.getLocalSocketAddress());
    }

    public Agent(String host, int port) {
        try {
            this.serverSocket = new ServerSocket(0);
            this.agents = new HashMap<>();
            this.inputStreams = new HashMap<>();
            this.outputStreams = new HashMap<>();

            Socket toIntroAgentSocket = new Socket(host, port);
            ObjectOutputStream out = new ObjectOutputStream(toIntroAgentSocket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(toIntroAgentSocket.getInputStream());

            out.writeObject(Communicate.ID);
            out.flush();
            int introAgentID = in.readInt();

            establishConnections(downloadAgents(in, out));
            agents.put(introAgentID, toIntroAgentSocket);
            inputStreams.put(introAgentID, in);
            outputStreams.put(introAgentID, out);

            ID = agents.keySet().stream()
                     .max(Integer::compareTo)
                     .get() + 1;
            System.out.println("Agent #" + ID + " created, address: " + serverSocket.getLocalSocketAddress() + " intro: " + toIntroAgentSocket.getRemoteSocketAddress());

            new Thread(() -> {
                addToOtherAgentsLists();
                synchronizeClock();
                sendSYNs();
            }).start(); //in order for server to be able to connect as client

        } catch (IOException e) {
            System.err.println("COULDN'T CONNECT");
            e.printStackTrace();
            System.exit(-1);
        } catch (NoSuchElementException e) {
            System.err.println("PROBLEM WITH FINDING MAX ID, THE RECEIVED MAP IS EMPTY I GUESS");
            e.printStackTrace();
            System.exit(-1);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }

    public synchronized void sendAgentsInfo(ObjectOutputStream out) {
        System.out.println("Sending agents...");
        Map<Integer, InetSocketAddress> info = new HashMap<>();

        for(Map.Entry<Integer, Socket> e : agents.entrySet()) {
            info.put(e.getKey(), (InetSocketAddress) e.getValue().getRemoteSocketAddress());
        }
        try {
            out.writeObject(info);
            out.flush();
        } catch (Exception e) {
            System.err.println("COULDN'T SEND THE AGENTS' INFO");
        }
    }

    public Map<Integer, InetSocketAddress> downloadAgents(ObjectInputStream in, ObjectOutputStream out) {
        System.out.println("Downloading agents... ");
        Map<Integer, InetSocketAddress> result = null;
        try {
            out.writeObject(Communicate.NET);
            out.flush();
            result =  (Map<Integer, InetSocketAddress>) in.readObject();
        } catch(Exception e) {
            System.err.println("COULDN'T DOWNLOAD THE AGENTS");
            e.printStackTrace();
            System.exit(-1);
        }
        return result;
    }

    public void establishConnections(Map<Integer, InetSocketAddress> info) {
        System.out.println("Establishing connections...");
        try {
            for(Map.Entry<Integer, InetSocketAddress> e : info.entrySet()) {
                agents.put(e.getKey(), new Socket(e.getValue().getAddress(), e.getValue().getPort())); //adding sockets
                outputStreams.put(e.getKey(), new ObjectOutputStream(agents.get(e.getKey()).getOutputStream()) ); //adding streams (to avoid creating them several times)
                inputStreams.put(e.getKey(), new ObjectInputStream(agents.get(e.getKey()).getInputStream()) );
            }
        } catch(Exception e) {
            System.out.println("COULDN'T ESTABLISH CONNECTIONS");
            e.printStackTrace();
            System.exit(-1);
        }

    }

    public void addToOtherAgentsLists() {
        ObjectOutputStream out;
        try {
            for(int id : agents.keySet()) {
                out = outputStreams.get(id);
                out.writeObject(Communicate.ADD);
                out.writeInt(ID);
                out.writeObject(serverSocket.getLocalSocketAddress());
                out.flush();
            }
        } catch (Exception e) {
            System.err.println("COULDN'T ADD THE AGENT TO AGENTS LISTS");
        }

    }

    public void addAgent(ObjectInputStream in) {
        try {
            int id = in.readInt();
            InetSocketAddress address = (InetSocketAddress) in.readObject();

            Socket socket = new Socket(address.getAddress(), address.getPort());
            agents.put(id, socket);
            outputStreams.put(id, new ObjectOutputStream(socket.getOutputStream()));
            inputStreams.put(id, new ObjectInputStream(socket.getInputStream()));

            System.out.println("Added " + agents.get(id));
        } catch (Exception e) {
            System.err.println("COULDN'T ADD THE AGENT TO THE LIST");
            e.printStackTrace();
        }
    }

    public void synchronizeClock() {
        long sum = 0;
        try {
            for(int i : agents.keySet()) {
                outputStreams.get(i).writeObject(Communicate.CLK);
                outputStreams.get(i).flush();
                sum += inputStreams.get(i).readLong();
            }
        } catch(Exception e) {
            System.err.println("COULDN'T GET CLOCK VALUES");
        }
        setClockStartTime(System.currentTimeMillis() - sum / agents.size());
    }

    public void sendSYNs() {
        try {
            for(int i : agents.keySet()) {
                outputStreams.get(i).writeObject(Communicate.SYN);
            }
        } catch (Exception e) {
            System.err.println("COULDN'T SEND SYN-S");
        }

    }

    public void disconnect() {
        try {
            for(int i : agents.keySet()) {
                outputStreams.get(i).writeObject(Communicate.DEL);
                outputStreams.get(i).writeInt(ID);
                outputStreams.get(i).flush();
            }
        } catch (Exception e) {
            System.err.println("COULDN'T SEND DELETE COMMUNICATE");
            e.printStackTrace();
        }

    }

    public void deleteAgent(ObjectInputStream in) {
        try {
            int id = in.readInt();
            agents.get(id).close();
            outputStreams.get(id).close();
            inputStreams.get(id).close();

            agents.remove(id);
            outputStreams.remove(id);
            inputStreams.remove(id);
        } catch (Exception e) {
            System.err.println("SOMETHING WRONG SENT AFTER DELETE SIGNAL");
            e.printStackTrace();
        }

    }

    public synchronized long getClockStartTime() {
        return clockStartTime;
    }

    public synchronized void setClockStartTime(long clockStartTime) {
        this.clockStartTime = clockStartTime;
    }

    public synchronized long getClockValue() {
        return System.currentTimeMillis() - clockStartTime;
    }

    public int getID() {
        return ID;
    }

}
