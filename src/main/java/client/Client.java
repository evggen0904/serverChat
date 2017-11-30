package client;



import settings.Message;
import settings.ServerSettings;

import java.io.*;
import java.net.Socket;

public class Client {
    private String userName;
    private ConnectionToServer connection;
    private BufferedReader keyBoard;

    public Client(){}

    public Client(BufferedReader keyBoard){
        this.keyBoard = keyBoard;
    }
/*Создаем коннект к серверу через Socket*/
    public void connectToServer(ConnectionToServer connection){
        this.connection = connection;
    }

    public boolean login(String login) throws Exception{

        if (checkLogin(login)) {
            userName = login;
            return true;
        }
        return false;
    }

/*Для проверки успешного логина к серверу отсылаем на сервер наш ник и
 * префикс, по которому сервер поймет, что происходит попытка логина.
 * Метод возвращает true, если получает положительный ответ от сервера*/
    private boolean checkLogin(String login) throws Exception {
        if (connection.isConnected()) {
            try {
                Message message = new Message();
                message.setUser(login);
                message.setCommand(ServerSettings.LOGIN_CHECK);
                connection.sendToServer(message);

                message = connection.readFromServer();
                if (ServerSettings.LOGIN_IS_FREE.equals(message.getCommand()))
                    return true;

            } catch (Exception e) {
                closeConnection();
                throw e;
            }
        }
        return false;
    }

    /*
    * Для получения команд от сервера, клиентский сокет должен постоянно сканировать
    * через in.read не пришло ли новое сообщение. Для обработки собственных сообщений
    * используется отдельный поток на чтение данных с клавиатуры.
    * Для обработки выхода из чата используется отсыл команды /QUIT на сервер, для
    * того, что сервер мог разослать сообщения другим пользователям и удалить текущего
    * пользователя из массовой рассылки.
    * */
    public void startChat(){
        if (connection.isConnected()) {
            ServerListenThread serverListener = null;
            try {
                serverListener = new ServerListenThread(connection, false);
                boolean isAlive = true;
                while (isAlive &&
                        (!serverListener.isInterrupted() && serverListener.isAlive())) {
                    String msg = keyBoard.readLine();
                    Message message = new Message(msg, userName);
                    connection.sendToServer(message);

                    if (msg.equals(ServerSettings.QUIT))
                        isAlive = false;
                }
            } catch (Exception e) {
                System.out.println("Connection to the sever lost: " + e);
            } finally {
                closeConnection();
            }
        }
    }

    public void closeConnection(){
        if (keyBoard != null){
            try {
                keyBoard.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        connection.closeConnection();
    }

    public String getUserName() {
        return userName;
    }

    public BufferedReader getKeyBoard() {
        return keyBoard;
    }

    public ConnectionToServer getConnection() {
        return connection;
    }

    public static void main(String[] args) {

        Client client = new Client(new BufferedReader(new InputStreamReader(System.in)));
        try {
            Socket socket = new Socket(ServerSettings.INET_ADDRESS, ServerSettings.PORT);
            ConnectionToServer connection = new ConnectionToServer(socket);
            client.connectToServer(connection);
            if (client.getConnection().isConnected()) {
                try {
                    BufferedReader keyBoard = client.getKeyBoard();
                    boolean isLogged = false;
                    String name = "";
                    while (!isLogged) {
                        System.out.print("Enter your login: ");
                        name = keyBoard.readLine();
                        isLogged = client.login(name);
                        if (!isLogged)
                            System.out.println("Login '" + name + "' is busy. Please, try again.");
                    }
                    System.out.println("Hello, " + name + "! You have logged successfully! Now you can type your messages.");
                    System.out.println("To get help type: " + ServerSettings.HELP);
                    client.startChat();

                } catch (IOException e) {
                    e.printStackTrace();
                } catch (Exception e) {
                    e.printStackTrace();
                }

            } else {
                System.out.println("No connection to server");
            }
        }
        catch (IOException e){
            System.out.println("Socket creation error: " + e);
        }

    }
}
