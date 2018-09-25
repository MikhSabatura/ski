import java.io.EOFException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class ClientThread implements Runnable {

    private Agent agent;
    private Socket toClientSocket;

    private int id;
    private ObjectInputStream in;
    private ObjectOutputStream out;

    public ClientThread(Agent agent, Socket clientSocket) {
        this.agent = agent;
        this.toClientSocket = clientSocket;
    }

    @Override
    public void run() {
        try {
            out = new ObjectOutputStream(toClientSocket.getOutputStream());
            in = new ObjectInputStream(toClientSocket.getInputStream());

            System.out.println("Client " + toClientSocket.getRemoteSocketAddress());
            Communicate communicate;

            while(true) {
                communicate = (Communicate) in.readObject();

                switch (communicate) {
                    case ID:
                        out.writeInt(agent.getID());
                        out.flush();
                        break;
                    case ADD: // establish connection to the serverSocket of the agent
                        id = agent.addAgent(in);
                        break;
                    case DEL:
                        agent.deleteAgent(id);
                        return;
                    case SYN:
                        agent.synchronizeClock();
                        break;
                    case CLK:
                        out.writeLong(agent.getClockValue());
                        out.flush();
                        break;
                    case NET:
                        agent.sendAgentsInfo(out);
                        break;
                    case DIE:
                        agent.disconnect();
                        System.exit(0);
                }
            }

        } catch (ClassCastException e) {
            System.err.println("ILLEGAL SIGNAL");
            e.printStackTrace();
        } catch (EOFException e) {
            if(id != -1) {
                agent.deleteAgent(id);
            }
            System.out.println("Agent " + toClientSocket.getRemoteSocketAddress() + " disconnected");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}