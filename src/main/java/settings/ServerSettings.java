package settings;


public class ServerSettings {
    /*настройка соединения*/
    public static final int PORT = 8888;
    public static final String INET_ADDRESS = "127.0.0.1";
    /*авторизация*/
    public static final String LOGIN_IS_FREE = "1";
    public static final String LOGIN_IS_NOT_FREE = "0";
    public static final String LOGIN_CHECK = "<login:?>";
    public static final String SESSION_STOPPED = "<SESSION STOPPED!!!>";

    public static final int MESSAGES_SIZE = 100;

    /*команды пользователя*/
    public static final String HELP = "/HELP";
    public static final String RENAME = "/RENAME";
    public static final String USERS = "/USERS";
    public static final String DIRECT_TO = "/DIRECTTO";
    public static final String QUIT = "/QUIT";

    /*команды сервера*/
    public static final String STOP = "/STOP";
}
