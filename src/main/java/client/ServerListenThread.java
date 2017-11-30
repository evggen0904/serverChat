package client;

import settings.Message;
import settings.ObjectSerialization;

import java.io.*;

/*класс отвечает за прием сообщений от сервера*/
public class ServerListenThread extends Thread {
    private ConnectionToServer connection;
    private boolean isBot;

    public ServerListenThread(ConnectionToServer connection, boolean isBot) {
        this.connection = connection;
        this.isBot = isBot;
        start();
    }

    @Override
    public void run() {
        while (true) {
            try {
                Message message = connection.readFromServer();
                if (!isBot && message != null) {
                    System.out.println(message.getMessage());
                }
            } catch (Exception e) {
                System.out.println("Server listening error: " + e);
                connection.closeConnection();
                return;
            }
        }
    }

}
