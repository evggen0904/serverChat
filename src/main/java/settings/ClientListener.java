package settings;

import server.ServerToClientThread;


import java.io.IOException;
import java.net.Socket;

public interface ClientListener {
    public void onStartSocket(ServerToClientThread userThread, Socket socket);
    public boolean onLoggedClient(ServerToClientThread userThread, Socket socket, Message message) throws IOException;
    public void onReceiveMessage(ServerToClientThread userThread, Socket socket, Message message) throws IOException;
    public void onStopSocket(ServerToClientThread userThread, Socket socket);
    public void onException(ServerToClientThread userThread, Socket socket, Exception e);
}
