package server;


import settings.*;

import java.io.*;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Server implements ClientListener {

    private final String name;
    private final ServerSocket serverSocket;
    private final Map<String, ServerToClientThread> users;
    private final List<String> messages;
    private final Map<String, Method> user_methods;
    private final Map<String, String> user_commands;

    private final int WAIT_SEC = 10;
    private final AtomicInteger failSend;
    private volatile boolean isStopped;

    private final Lock broadcastLock;
    private final Object lastMessagesMonitor;
    private final Object loggedMonitor;
    private final Object receiveMonitor;
    private final Object stopMonitor;
    private final Object directMonitor;
    private final Object usersMonitor;
    private final Object renameMonitor;
    private final Object helpMonitor;
    private final Object quitMonitor;

    public Server(ServerSocket serverSocket, String name) throws IOException {
        this.name = name;
        this.serverSocket = serverSocket;
        messages = new CopyOnWriteArrayList<String>();
        users = new ConcurrentHashMap<String, ServerToClientThread>();
        user_methods = new HashMap<String, Method>();
        user_commands = new HashMap<String, String>();
        fillUserCommands();

        broadcastLock = new ReentrantLock();
        lastMessagesMonitor = new Object();
        loggedMonitor = new Object();
        receiveMonitor = new Object();
        stopMonitor = new Object();
        directMonitor = new Object();
        usersMonitor = new Object();
        renameMonitor = new Object();
        helpMonitor = new Object();
        quitMonitor = new Object();

        failSend = new AtomicInteger();
        failSend.set(0);
    }

    public static void main(String[] args) {
        int port = ServerSettings.PORT;

        try {
            ServerSocket serverSocket = new ServerSocket(port);
            serverSocket.setSoTimeout(60000);
            final Server server = new Server(serverSocket, "ChatServer");
            server.startServer();
        }catch (IOException e){
            System.out.println("Server socket creation error: " + e);
        }
    }

    /*Для осуществления возможность общения сервера с множеством пользовалей, для каждого пользовательского
    * сокета необходимо ораганизовать свой канал сообщения. Он реализован в виде отдельного потока
    * ServerToClientThread
    * */
    public void startServer(){
        startConsoleListener(new BufferedReader(new InputStreamReader(System.in)));
        startSocketListener();
    }

    private void startSocketListener(){
        try {
            System.out.println("Server " + name + " started.");
            while(true) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    ServerToClientThread serverToClientThread = new ServerToClientThread(clientSocket, this);
                }
                catch (SocketTimeoutException e){
                    continue;
                }
            }
        } catch (IOException e) {
            if (!isStopped)
                System.out.println("Server error: " + e);
        }
        finally {
            if (!isStopped)
                stopServer();
        }
    }

    private void startConsoleListener(final BufferedReader keyBoard){
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
    }

    /*
    * При инициализации сервера, загружаются пользовательские команды управления, описанные в классе
    * ServerSettings. Для этого создана специальная аннотация @UserCommand с двумя свойствами(commandName-
    * содержит значение команды, вводимой пользоватлем в консоль и commandDescription - описание того, как
    * использовать команду). Аннотация прицепляется на методы, которые будут соответсвовать исполняемым ко-
    * мандам.
    * Для удобства использования в коде заполняются Map'ы, ставящие в соответствие команду и ее описание,
    * а также команду и соответствующий ей метод.
    * Таким образом для добавления новой команды на серевер нужно:
    * 1) добавить команду в класс ServerSettings
    * 2) создать метод с аннотациаей @UserCommand
    * */
    private void fillUserCommands(){
        Method[] methods = Server.class.getMethods();
        for (Method m: methods) {
            if (m.isAnnotationPresent(UserCommand.class)){
                user_methods.put(m.getAnnotation(UserCommand.class).commandName(), m);
                user_commands.put(m.getAnnotation(UserCommand.class).commandName(),
                        m.getAnnotation(UserCommand.class).commandDescription());
            }
        }
    }

    /*
    * метод парсит строку сообщения для того, чтобы определить была ли введена
    * какая-либо команда
    * */
    public Method getUserMethod(Message message){
        if (message!= null && message.getMessage() != null) {
            String[] buffer = message.getMessage().split(" ");
            if (buffer.length > 0) {
                if (!user_methods.containsKey(buffer[0]))
                    return null;
                message.setCommand(buffer[0]);

                return user_methods.get(buffer[0]);
            }
        }
        return null;
    }

    /*массовая рассылка*/
    public void sendBroadcastMessage(ServerToClientThread fromUser, Message message) throws IOException {

        String logMsg;
        try {
            if (broadcastLock.tryLock(WAIT_SEC, TimeUnit.SECONDS)) {
                try {
                    String user_name = fromUser.getUser_name();
                    for (Map.Entry<String, ServerToClientThread> user : users.entrySet()) {
                        //самому себе не отсылаем свое же сообщение
                        if (!user.getKey().equals(user_name)) {
                            ServerToClientThread userTo = user.getValue();
                            userTo.sendMessage(message);
                        }
                    }
                    String command = message.getCommand();
                    //в лог сохраняем все сообщения, кроме тех что были направлены другому пользователя напрямую
                    if (!ServerSettings.DIRECT_TO.equals(command)) {
                        addMessage(message.getMessage());
                    }
                } catch (IOException e) {
                    throw e;
                } finally {
                    broadcastLock.unlock();
                }
            } else {
                incFailSend();
            }

        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            System.out.println("TryLock error from: " + fromUser.getUser_name() + ". " + e);
        }

    }

    private void addMessage(String message){
        /*храним только последний N сообщений, чтобы не захламлять память*/
        if (messages.size() > ServerSettings.MESSAGES_SIZE){
            messages.remove(0);
        }
        messages.add(message);
    }

    /*
    * собираем все  последние сообщения для вновь подключившегося
    * */
    public void sendLastMessages(ServerToClientThread user) throws IOException {

        synchronized (lastMessagesMonitor) {
            StringBuilder sb = new StringBuilder();
            for (String m : messages) {
                sb.append(m).append("\n");
            }

            if (sb.length() > 0) {
                Message message = new Message();
                message.setMessage(sb.toString());
                user.sendMessage(message);
            }
        }
    }

    public boolean isFreeUserLogin(String login){
        return users.containsKey(login);
    }

    public void addUser(ServerToClientThread user, String userName){
        users.put(userName, user);
    }

    public ServerToClientThread removeUser(ServerToClientThread user){
        if (user != null) {
            return users.remove(user.getUser_name());
        }
        return null;
    }

    public void addLog(ServerToClientThread userThread, String logMessage){
        System.out.println(userThread.getName() + ": " + logMessage);
    }

    // проверяем доступность логина и в случае успеха, добаляем его в список рассылки
    public boolean loginCheck(ServerToClientThread user, String login){

        if (!isFreeUserLogin(login)){
            addUser(user, login);
            return true;
        }
        return false;
    }

    /*========================================================================================================*/
    @Override
    public void onStartSocket(ServerToClientThread userThread, Socket socket) {
        addLog(userThread, "started.");
    }

    @Override
    public boolean onLoggedClient(ServerToClientThread userThread, Socket socket, Message receivedMessage) throws IOException {

        synchronized (loggedMonitor) {
            String command = receivedMessage.getCommand();
            String user_name = receivedMessage.getUser();

            Message answerMessage = new Message();
            answerMessage.setUser(user_name);

            boolean isLogged = false;
            //если пользователь только присоединился, проверяем доступность выбранного логина
            if (command.equals(ServerSettings.LOGIN_CHECK)) {
                if (loginCheck(userThread, receivedMessage.getUser())) {
                    // если проверка логина прошла успешно, отправляем клиенту команду с подтверждением
                    answerMessage.setCommand(ServerSettings.LOGIN_IS_FREE);
                    isLogged = true;
                    userThread.setUser_name(user_name);
                } else
                    answerMessage.setCommand(ServerSettings.LOGIN_IS_NOT_FREE);

                userThread.sendMessage(answerMessage);
                //для нового клиента нужно отправить последние N сообщений и
                //разослать всем уведомление о новом пользователе
                if (isLogged) {
                    sendLastMessages(userThread);
                    String msg = "USER '" + user_name + "' JOIN THE CHAT.";
                    answerMessage.setMessage(msg);
                    addLog(userThread, msg);
                    sendBroadcastMessage(userThread, answerMessage);
                }
            }
            return isLogged;
        }

    }

    @Override
    public void onReceiveMessage(ServerToClientThread userThread, Socket socket, Message receivedMessage) throws IOException {

        synchronized (receiveMonitor) {
            Method userMethod = getUserMethod(receivedMessage);
            String message = receivedMessage.getMessage();

            Message answerMessage = new Message();
            answerMessage.setUser(receivedMessage.getUser());
            // смотрим не прислал ли пользователь запрос на выполнение сервером какой-либо команды
            if (userMethod != null) {
            /*если пользователь ввел команду для сервера, запускаем ее выполнение*/
                try {
                    userMethod.invoke(this, userThread);
                } catch (Exception e) {
                    onException(userThread, socket, e);
                }
            } else {
            /*обычное сообщение расслается всем онлайн пользователям*/
                answerMessage.setMessage(receivedMessage.getUser() + ": " + message);
                sendBroadcastMessage(userThread, answerMessage);
            }
        }
    }

    @Override
    public void onStopSocket(ServerToClientThread userThread, Socket socket) {
        removeUser(userThread);
        synchronized (stopMonitor) {
            try {
                Message message = new Message();
                String logMessage = "USER '" + userThread.getUser_name() + "' QUIT THE CHAT.";
                message.setMessage(logMessage);
                addLog(userThread, logMessage);
                sendBroadcastMessage(userThread, message);

            } catch (Exception e) {
//                onException(userThread, socket, e);
            }
        }
    }

    @Override
    public void onException(ServerToClientThread userThread, Socket socket, Exception e) {
        addLog(userThread, userThread.getUser_name() + ": " + e);
    }

    /*========================================================================================================*/
    /**Получение списка пользователей, находящихся онлайн в данное время
     * Команда - ServerSettings.USERS
     * */
    @UserCommand(commandName = ServerSettings.USERS,
            commandDescription = "to see users online type /USERS in your chat.")
    public void getOnlineUsers(ServerToClientThread userTo) throws IOException {
        int averageSizeofNickName = 6;
        synchronized (usersMonitor) {
            StringBuilder onlineUsers = new StringBuilder(users.size() * averageSizeofNickName);
            int i = 0;
            for (Map.Entry<String, ServerToClientThread> e : users.entrySet()) {
                if (i++ == 0)
                    onlineUsers.append("ONLINE USERS:\n");
                onlineUsers.append(e.getKey()).append("\n");
            }
            Message message = new Message();
            message.setMessage(onlineUsers.toString());
            userTo.sendMessage(message);
        }
    }


    /*Переименование пользователя по его запросу, с проверкой валидности нового логина
    Команда - ServerSettings.RENAME
    * */
    @UserCommand(commandName = ServerSettings.RENAME,
            commandDescription = "to rename your login type /RENAME newLogin in your chat.")
    public void changeLogin(ServerToClientThread userTo) throws IOException {
        synchronized (renameMonitor) {
            String previousLogin = userTo.getUser_name();
            String receiveMessage = userTo.getReceivedMessage().getMessage();
            String newLogin = receiveMessage.substring(ServerSettings.RENAME.length() + 1, receiveMessage.length());
            if (loginCheck(userTo, newLogin)) {
                //если новый логин доступен, вначале удаляем привзяку к старому нику
                users.remove(userTo.getUser_name());
                userTo.setUser_name(newLogin);
                //затем добавляем клиента как нового в список рассылки
                users.put(newLogin, userTo);

                Message message = new Message();
                message.setMessage("YOUR LOGIN WAS SUCCESSFULLY CHANGED TO '" + newLogin + "'");
                userTo.sendMessage(message);

                //оповещаем остальных
                message = new Message();
                String msg = "USER '" + previousLogin + "' CHANGED NAME TO '" + newLogin + "'.";
                message.setMessage(msg);
                addLog(userTo, msg);
                sendBroadcastMessage(userTo, message);
            } else {
                Message message = new Message();
                message.setMessage("LOGIN '" + newLogin + "' IS BUSY.");
                userTo.sendMessage(message);
            }
        }
    }

    /* Отсылка сообщений указанному пользователю напрямую
    Команда - ServerSettings.DIRECT_TO
    Для отсылки парсится полученное на сервер сообщение
    * */
    @UserCommand(commandName = ServerSettings.DIRECT_TO,
            commandDescription = "to send direct message to the user type /DIRECTTO :user_name 'your sentence' .")
    public boolean sendDirectUserMessage(ServerToClientThread userFrom) throws IOException {
        synchronized (directMonitor) {
            StringBuilder sb = new StringBuilder(userFrom.getReceivedMessage().getMessage());
//       пробел и двоеточие удаляем для получения первого символа никнейма
            String command = ServerSettings.DIRECT_TO;
            if (sb.length() < command.length()+2)
                return false;

            sb.delete(0, ServerSettings.DIRECT_TO.length() + 2);
            int i = 0;
            while (i < sb.length() && sb.charAt(i) != ' ') {
                i++;
            }
        /*вычленили ник того, кому нужно отправить*/
            String targetUser = sb.substring(0, i);
            Message message = new Message();
            StringBuilder answer = new StringBuilder();
        /*проверяем валидность ника и отправляем сообщение*/
            if (users.containsKey(targetUser)) {
                if (i >= sb.length())
                    return false;
                answer.append("DIRECT MESSAGE FROM ").append(userFrom.getUser_name())
                        .append(": ").append(sb.substring(i + 1, sb.length()));

                ServerToClientThread target = users.get(targetUser);
                message.setMessage(answer.toString());
                target.sendMessage(message);
                return true;

            } else {
                answer.append("USER '").append(targetUser).append("' IS NOT IN THIS CHAT.");
                message.setMessage(answer.toString());
                userFrom.sendMessage(message);
            }
        }
        return false;
    }

    /*Обработка запроса на выход пользователя из чата.
    * Команда - ServerSettings.QUIT
    * */
    @UserCommand(commandName = ServerSettings.QUIT,
            commandDescription = "to quit the chat type /QUIT")
    public void quit(ServerToClientThread userTo) throws IOException {
        synchronized (quitMonitor) {
            Message message = new Message();
            message.setCommand(ServerSettings.SESSION_STOPPED);
            userTo.sendMessage(message);
        }
    }

    /*Получение информации по доступным командам пользователя
* Команда - ServerSettings.HELP
* */
    @UserCommand(commandName = ServerSettings.HELP,
            commandDescription = "get available user commands")
    public String help(ServerToClientThread userTo) throws IOException {
        synchronized (helpMonitor) {
            int averageSizeofCommand = 5;
            StringBuilder commands = new StringBuilder(user_commands.size() * averageSizeofCommand);
            int i = 0;
            for (Map.Entry<String, String> e : user_commands.entrySet()) {
                if (i++ == 0)
                    commands.append("AVAILABLE USER COMMANDS:\n");
                String command = e.getKey();
                if (!command.equals(ServerSettings.HELP)) {
                    commands.append(command).append(" - ").append(e.getValue()).append("\n");
                }
            }
            Message message = new Message();
            message.setMessage(commands.toString());
            userTo.sendMessage(message);
            return commands.toString();
        }
    }
/*========================================================================================================*/

    @ServerCommand(commandName = ServerSettings.STOP,
            commandDescription = "to stop the server type /STOP")
    public void stopServer(){
        if (serverSocket != null){
            try {
                serverSocket.close();
                isStopped = true;
            } catch (IOException e) {
                e.printStackTrace();
            }
            System.out.println("Server " + name + " stopped.");
        }
    }

    private void incFailSend(){
        failSend.getAndIncrement();
    }

    public ServerSocket getServerSocket() {
        return serverSocket;
    }

}
