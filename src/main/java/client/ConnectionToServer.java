package client;


import settings.Message;
import settings.ObjectSerialization;

import java.io.*;
import java.net.Socket;

public class ConnectionToServer{
    private DataOutputStream out;
    private DataInputStream in;
    private Socket socket;
    private ObjectSerialization serialization;

    public ConnectionToServer(Socket socket) {
        try {
            this.socket = socket;
            in = new DataInputStream(this.socket.getInputStream());
            out = new DataOutputStream(this.socket.getOutputStream());
            serialization = new ObjectSerialization();
        } catch (IOException e) {
            System.out.println("Socket streams initialization error:" + e);
            closeConnection();
        }

    }

    public void sendToServer(Message message) throws IOException {
        byte[] bytes = serialization.toBytes(message);
        out.writeInt(bytes.length);
        out.write(bytes);
        out.flush();
    }

    public Message readFromServer() throws Exception {
        try {
            int size = in.readInt();
            byte[] bytes = new byte[size];
            in.readFully(bytes);
            return (Message)serialization.toObject(bytes);
        }catch (EOFException e){
        }catch (IOException e){
            throw e;
        }

        return null;
    }

    public boolean isConnected(){
        return (socket != null);
    }

    public void closeConnection(){
        if (in != null) {
            try {
                ((DataInputStream)in).close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (out != null) {
            try {
                ((DataOutputStream)out).close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (socket != null){
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
