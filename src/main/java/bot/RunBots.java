package bot;

import client.Client;
import client.ConnectionToServer;
import client.ServerListenThread;
import settings.Message;
import settings.ServerSettings;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class RunBots {
    private final Random random;
    private final int BOT_COUNT = 500;
    private final KeyBoardListener keyBoardListener;
    private List<String> messages;
    private Object obj;

    public RunBots() {
        this.keyBoardListener = new KeyBoardListener();
        random = new Random();
        messages = createBotMessages();
        obj = new Object();
    }

    public static void main(String[] args) throws FileNotFoundException {
        RunBots runBots = new RunBots();

        KeyBoardListener k = runBots.getKeyBoardListener();
        runBots.run();

        Scanner sc = new Scanner(System.in);
        while (sc.hasNext()) {
            if (sc.next().equals(ServerSettings.QUIT)) {
                System.out.println("Bots stopping began. Please wait");
                k.setAlive(false);
                break;
            }
        }
    }

    private void run(){

        System.out.println("Bots creating is in process.");

//        final List<Thread> botList = new ArrayList<Thread>(BOT_COUNT);
        int i = 0;
        Thread thread = null;
        while (i < BOT_COUNT){

            try {
                thread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        Bot b = createBot();
                    }
                });
                thread.start();
//                botList.add(thread);
                Thread.sleep(100);
                System.out.println("Bots are running: " + (++i));

            } catch (InterruptedException e) {
                System.out.println("Bot creating error: " + e);
            }
        }

        System.out.println("All bots are running");
        System.out.println("To stop running bots type command: " + ServerSettings.QUIT);
    }

    private List<String> createBotMessages(){
        List<String> messages = new ArrayList<String>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        new FileInputStream("src\\main\\resources\\BotMessages.txt"),StandardCharsets.UTF_8))){
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.equals(""))
                    messages.add(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return messages;
    }

    private Bot createBot(){

        Bot bot = new Bot(messages, this.keyBoardListener);
        try {
            Socket socket = new Socket(ServerSettings.INET_ADDRESS, ServerSettings.PORT);
            ConnectionToServer connection = new ConnectionToServer(socket);
            bot.connectToServer(connection);
            try {
                boolean isLogged = false;
                while (!isLogged) {
                    isLogged = bot.login(generateLogin());
                }
                bot.startChat();

            } catch (Exception e) {
                System.out.println("Bot connection error: " + e);
            }
        } catch (IOException e) {
            System.out.println("Bot socket creation error: " + e);
        }
        return bot;
    }

    private synchronized String generateLogin(){
        StringBuilder sb = new StringBuilder();
        sb.append("user").append(random.nextInt(1500));
        return sb.toString();
    }

    public KeyBoardListener getKeyBoardListener() {
        return keyBoardListener;
    }
}

class Bot extends Client {
    private Random random;
    private List<String> messages;
    private KeyBoardListener keyBoardListener;
    private boolean isAlive;

    Bot(List<String> messages, KeyBoardListener keyBoardListener) {
        super();
        random = new Random();
        this.messages = messages;
        this.keyBoardListener = keyBoardListener;
        isAlive = true;
    }

    @Override
    public void startChat() {
        ConnectionToServer connection = getConnection();
        if (connection.isConnected()) {
            try {
                ServerListenThread serverListenThread = new ServerListenThread(connection, true);
                while (isAlive &&
                        (!serverListenThread.isInterrupted() && serverListenThread.isAlive())) {
                    try {
                        Thread.sleep(random.nextInt(5000));
                        int idx = random.nextInt(messages.size());
                        Message message = new Message(messages.get(idx), getUserName());
                        connection.sendToServer(message);
                        if (!keyBoardListener.isAlive()) {
                            isAlive = false;
                        }

                    } catch (Exception e) {
                        System.out.println("Bot socket error: " + e);
                        return;
                    }
                }
            } finally {
                closeConnection();
            }
        }
    }
}

class KeyBoardListener {
    private boolean isAlive;

    public KeyBoardListener() {
        isAlive = true;
    }

    public boolean isAlive() {
        return isAlive;
    }

    public void setAlive(boolean alive) {
        isAlive = alive;
    }
}
