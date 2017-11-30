package server;


import settings.ClientListener;
import settings.Message;
import settings.ObjectSerialization;

import java.io.*;
import java.net.Socket;

/* отдельный поток для работы с каждым клиентом*/
public class ServerToClientThread extends Thread {
    private Socket socket;
    private String user_name;
    private DataOutputStream out;
    private DataInputStream in;
    private boolean isLogged;
    private Message receivedMessage;
    private ClientListener clientListener;
    private ObjectSerialization serialization;

    public ServerToClientThread(Socket socket, ClientListener clientListener){
        this.socket = socket;
        this.clientListener = clientListener;
        isLogged = false;
        serialization = new ObjectSerialization();

        start();
    }

    @Override
    public void run() {
        try
        {
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());
            clientListener.onStartSocket(this, socket);
            while (true) {
                receivedMessage = readFromClient();
                if (!isLogged)
                    isLogged = clientListener.onLoggedClient(this, socket, receivedMessage);
                else
                    clientListener.onReceiveMessage(this, socket, receivedMessage);
            }
        }
        catch(Exception e) {
            clientListener.onException(this, socket, e);
        }
        finally {
            clientListener.onStopSocket(this, socket);
        }

    }

    public Message readFromClient() throws Exception {
        try {
            int size = in.readInt();
            byte[] bytes = new byte[size];
            in.readFully(bytes);
            return (Message) serialization.toObject(bytes);
        }catch (EOFException e){
        }catch (IOException e){
            throw e;
        }
        return null;
    }

    public void sendMessage(Message message) throws IOException {
        byte[] bytes = serialization.toBytes(message);
        out.writeInt(bytes.length);
        out.write(bytes);
        out.flush();
    }


    public Message getReceivedMessage() {
        return receivedMessage;
    }

    public String getUser_name() {
        return user_name;
    }

    public void setUser_name(String user_name) {
        this.user_name = user_name;
    }
}
