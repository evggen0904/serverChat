package settings;


import java.io.Serializable;

public class Message implements Serializable{
    private String message;
    private String command;
    private String user;

    public Message(){}

    public Message(String message, String user) {
        this.message = message;
        this.user = user;
    }

    public Message(String message, String user, String command) {
        this.message = message;
        this.command = command;
        this.user = user;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }
}
