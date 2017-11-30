package server;


import settings.Message;
import settings.ServerSettings;

import java.io.BufferedReader;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;

@SuppressWarnings("ALL")
public class ServerStub extends Server{
    private ServerToClientThread serverToClientThread;
    private BufferedReader keyBoard;
    private Map<String, ServerToClientThread> users;
    private Map<String, Method> user_methods;
    private boolean useParentMethod;

    public ServerStub(ServerSocket serverSocket, String name,
                      ServerToClientThread serverToClientThread,
                      BufferedReader keyBoard,
                      boolean useParentMethod) throws IOException {
        super(serverSocket, name);
        this.serverToClientThread = serverToClientThread;
        this.keyBoard = keyBoard;
        Field field = null;
        try {
            field = Server.class.getDeclaredField("users");
            field.setAccessible(true);
            this.users = (Map<String, ServerToClientThread>)field.get(this);

            field = Server.class.getDeclaredField("user_methods");
            field.setAccessible(true);
            this.user_methods = (Map<String, Method>)field.get(this);

        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }


//        this.users = getUsers();
//        this.user_methods = getUser_methods();
        this.useParentMethod = useParentMethod;
    }

    @Override
    public void startServer() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                String quitMessage = "To stop server type: " + ServerSettings.STOP;
                System.out.println(quitMessage);
                try {
                    while (true) {
                        String command = keyBoard.readLine();
                        if (command.equals(ServerSettings.STOP)) {
                            System.out.println("Server will be stopped");
                            stopServer();
                            break;
                        }
                        System.out.println(quitMessage);
                    }
                } catch (IOException e) {
                    System.out.println("KeyBoard error: " + e);
                } finally {
                    try {
                        keyBoard.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

            }
        }).start();

        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        try {
            new ServerToClientThread(getServerSocket().accept(), this);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }



    @Override
    public void onStartSocket(ServerToClientThread userThread, Socket socket) {

    }


    @Override
    public void onReceiveMessage(ServerToClientThread userThread, Socket socket, Message receivedMessage) throws IOException {
        if (receivedMessage.getCommand().equals("RUN_PARENT_METHOD")){
            super.onReceiveMessage(userThread,socket,receivedMessage);
        }
    }

    @Override
    public void onStopSocket(ServerToClientThread userThread, Socket socket) {
        if (useParentMethod){
            super.onStopSocket(userThread, socket);
        }
    }

    @Override
    public void onException(ServerToClientThread userThread, Socket socket, Exception e) {

    }

    @Override
    public void sendBroadcastMessage(ServerToClientThread fromUser, Message message) throws IOException {
        System.out.println("Broadcast message was send");
    }

    @Override
    public void sendLastMessages(ServerToClientThread user) throws IOException {

    }

    @Override
    public void addLog(ServerToClientThread userThread, String logMessage) {
//        System.out.println(logMessage);
    }

    @Override
    public void getOnlineUsers(ServerToClientThread userTo) throws IOException {

    }

    @Override
    public void changeLogin(ServerToClientThread userTo) throws IOException {

    }

    @Override
    public boolean sendDirectUserMessage(ServerToClientThread userFrom) throws IOException {
        return true;
    }

    @Override
    public void quit(ServerToClientThread userTo) throws IOException {

    }

    @Override
    public String help(ServerToClientThread userTo) throws IOException {
        System.out.println("help invoked");
        return null;
    }
}
