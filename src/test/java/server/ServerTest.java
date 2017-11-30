package server;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import settings.Message;
import settings.ServerSettings;
import settings.UserCommand;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;


public class ServerTest {
    private BufferedReader keyboardMock = mock(BufferedReader.class);
    private ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private ServerSocket serverSocketMock = mock(ServerSocket.class);
    private Socket socketMock = mock(Socket.class);
    private ServerToClientThread userThreadMock = mock(ServerToClientThread.class);
    private Server server;

    @Before
    public void init() throws IOException {
        server = server = new Server(serverSocketMock, "TestServer");
        System.setOut(new PrintStream(outContent));
    }

    @Test
    public void testServerStop() throws Exception {
        when(serverSocketMock.accept()).thenReturn(socketMock);
        when(keyboardMock.readLine()).thenReturn(ServerSettings.STOP);
        StringBuilder sb = new StringBuilder();
        sb.append("To stop server type: /STOP\r\n")
                .append("Server will be stopped\r\n")
                .append("Server TestServer stopped.\r\n");

        ServerStub server = new ServerStub(serverSocketMock, "TestServer", userThreadMock, keyboardMock, false);
        server.startServer();

        assertThat(outContent.toString(), is(sb.toString()));
    }

    @Test
    public void testSendLastMessages_Ok() throws Exception {
        Field messagesField = Server.class.getDeclaredField("messages");
        messagesField.setAccessible(true);
        List<String> messages = (List<String>)messagesField.get(server);
        messages.add("user1");
        messages.add("user2");
        messages.add("user3");
        doNothing().when(userThreadMock).sendMessage(any(Message.class));

        server.sendLastMessages(userThreadMock);

        verify(userThreadMock, times(1)).sendMessage(any(Message.class));
    }

    @Test
    public void testSendMessages_NoMessages() throws IOException {
        doNothing().when(userThreadMock).sendMessage(any(Message.class));

        server.sendLastMessages(userThreadMock);

        verify(userThreadMock, times(0)).sendMessage(any(Message.class));
    }

    @Test
    public void testBroadcast_ToTwoUsersFromList() throws Exception{
        Message message = new Message("hello", "user1", "");
        Map<String, ServerToClientThread> users = prepareUsers();
        users.put("user1", userThreadMock);
        users.put("user2", userThreadMock);
        users.put("user3", userThreadMock);
        doNothing().when(userThreadMock).sendMessage(any(Message.class));
        when(userThreadMock.getUser_name()).thenReturn("user1");

        server.sendBroadcastMessage(userThreadMock, message);
        verify(userThreadMock, times(2)).sendMessage(any(Message.class));
    }

    @Test
    public void testBroadcast_ToZeroUsers() throws Exception{
        Message message = new Message("hello", "user1", "");
        Map<String, ServerToClientThread> users = prepareUsers();
        users.put("user1", userThreadMock);
        doNothing().when(userThreadMock).sendMessage(any(Message.class));
        when(userThreadMock.getUser_name()).thenReturn("user1");

        server.sendBroadcastMessage(userThreadMock, message);
        verify(userThreadMock, times(0)).sendMessage(any(Message.class));
    }

    @Test(expected = IOException.class)
    public void testBroadcast_Exception() throws Exception{
        Message message = new Message("hello", "user1", "");
        Map<String, ServerToClientThread> users = prepareUsers();
        users.put("user1", userThreadMock);
        users.put("user2", userThreadMock);
        doThrow(IOException.class).when(userThreadMock).sendMessage(any(Message.class));
        when(userThreadMock.getUser_name()).thenReturn("user1");

        server.sendBroadcastMessage(userThreadMock, message);
    }

    @Test
    public void testGetUserMethod_Ok() throws Exception{
        Map<String, Method> user_methods = prepareUserMethods();

        Message message = new Message("/HELP message","user","");
        Method helpMethod = Server.class.getDeclaredMethod("help", new Class[]{ServerToClientThread.class});
        assertThat(server.getUserMethod(message), is(helpMethod));
    }

    @Test
    public void testGetUserMethod_NoSuchMethod() throws Exception{
        Map<String, Method> user_methods = prepareUserMethods();

        Message message = new Message("/NO_SUCH_COMMAND message","user","");
        assertThat(server.getUserMethod(message), is(nullValue()));
    }

    @Test
    public void testRemoveUser_Ok() throws Exception {
        Map<String, ServerToClientThread> users = prepareUsers();
        users.put("user1", userThreadMock);
        users.put("user2", userThreadMock);
        users.put("user3", userThreadMock);

        when(userThreadMock.getUser_name()).thenReturn("user1");
        assertThat(server.removeUser(userThreadMock), is(userThreadMock));
    }

    @Test
    public void testRemoveUser_NoSuchUser() throws Exception{
        Map<String, ServerToClientThread> users = prepareUsers();
        users.put("user1", userThreadMock);
        users.put("user2", userThreadMock);

        when(userThreadMock.getUser_name()).thenReturn("user3");
        assertThat(server.removeUser(userThreadMock), is(nullValue()));
    }

    @Test
    public void testOnLoggedClient_OK() throws Exception {
        Message message = new Message("","user3", ServerSettings.LOGIN_CHECK);

        ServerStub server = new ServerStub(serverSocketMock, "TestServer", userThreadMock, keyboardMock, false);
        Map<String, ServerToClientThread> users = prepareUsersStub(server);
        doNothing().when(userThreadMock).sendMessage(any(Message.class));
        boolean isLogged = server.onLoggedClient(userThreadMock, socketMock, message);

        verify(userThreadMock, times(1)).sendMessage(any(Message.class));
        assertTrue(isLogged);
    }

    @Test
    public void testOnLoggedClient_LoginIsNotFree() throws Exception {
        Message message = new Message("","user1", ServerSettings.LOGIN_CHECK);
        ServerStub server = new ServerStub(serverSocketMock, "TestServer", userThreadMock, keyboardMock, false);
        Map<String, ServerToClientThread> users = prepareUsersStub(server);
        doNothing().when(userThreadMock).sendMessage(any(Message.class));
        boolean isLogged = server.onLoggedClient(userThreadMock, socketMock, message);

        verify(userThreadMock, times(1)).sendMessage(any(Message.class));
        assertFalse(isLogged);
    }

    @Test
    public void testOnReceiveMessage_SendBroadcast() throws Exception{
        Message message = new Message("","user1", "RUN_PARENT_METHOD");
        String answer = "Broadcast message was send\r\n";

        ServerStub server = new ServerStub(serverSocketMock, "TestServer", userThreadMock, keyboardMock, false);
        server.onReceiveMessage(userThreadMock, socketMock, message);

        assertThat(outContent.toString(), is(answer));
    }

    @Test
    public void testOnReceiveMessage_UserMethod() throws Exception{
        Message message = new Message(ServerSettings.HELP,"user1", "RUN_PARENT_METHOD");
        String answer = "help invoked\r\n";
        ServerStub server = new ServerStub(serverSocketMock, "TestServer", userThreadMock, keyboardMock, false);
        Map<String, Method> user_methods = prepareUserMethods();

        server.onReceiveMessage(userThreadMock, socketMock, message);

        assertThat(outContent.toString(), is(answer));
    }

    @Test
    public void testOnStopSocket_UserRemoved() throws Exception{
        when(userThreadMock.getUser_name()).thenReturn("user1").thenReturn("user1");
        ServerStub server = new ServerStub(serverSocketMock, "TestServer", userThreadMock, keyboardMock, true);
        Map<String, ServerToClientThread> users = prepareUsersStub(server);

        assertThat(users.size(), is(2));
        server.onStopSocket(userThreadMock, socketMock);
        assertThat(users.size(), is(1));
    }

    @Test
    public void testOnlineUsers() throws Exception{
        Map<String, ServerToClientThread> users = prepareUsers();
        users.put("admin", userThreadMock);
        users.put("user", userThreadMock);
        doNothing().when(userThreadMock).sendMessage(any(Message.class));

        server.getOnlineUsers(userThreadMock);
        verify(userThreadMock, times(1)).sendMessage(any(Message.class));
    }

    @Test
    public void testChangeLogin_LoginIsFree() throws Exception{
        Message message = new Message(ServerSettings.RENAME + " newUser", "user", "");
        Map<String, ServerToClientThread> users = prepareChangeLogin(message);

        server.changeLogin(userThreadMock);
        assertThat(users.containsKey("user"), is(false));
        assertThat(users.containsKey("newUser"), is(true));
    }

    @Test
    public void testChangeLogin_LoginIsBusy() throws Exception{
        Message message = new Message(ServerSettings.RENAME + " admin", "user", "");
        Map<String, ServerToClientThread> users = prepareChangeLogin(message);

        server.changeLogin(userThreadMock);
        assertThat(users.containsKey("user"), is(true));
    }

    @Test
    public void testSendDirectUserMessage_OK() throws Exception {
        Message message = new Message(ServerSettings.DIRECT_TO + " :admin privet", "user", "");
        prepareDirectSend(message);

        assertTrue(server.sendDirectUserMessage(userThreadMock));
    }

    @Test
    public void testSendDirectUserMessage_NoSuchUser() throws Exception {
        Message message = new Message(ServerSettings.DIRECT_TO + " :superUser privet", "user", "");
        prepareDirectSend(message);

        assertFalse(server.sendDirectUserMessage(userThreadMock));
    }

    @Test
    public void testSendDirectUserMessage_EmptyMessage() throws Exception{
        Message message = new Message(ServerSettings.DIRECT_TO + " :admin", "user", "");
        prepareDirectSend(message);

        assertFalse(server.sendDirectUserMessage(userThreadMock));
    }

    @Test
    public void testHelp_OK() throws Exception{
        Field field = Server.class.getDeclaredField("user_commands");
        field.setAccessible(true);
        Map<String, String> user_methods = (Map<String, String>)field.get(server);
        user_methods.put("/HELP", "HELP");
        user_methods.put("/QUIT", "QUIT");

        StringBuilder sb = new StringBuilder();
        sb.append("AVAILABLE USER COMMANDS:\n");
        for (Map.Entry<String,String> entry: user_methods.entrySet()) {
            String command = entry.getKey();
            if (!command.equals(ServerSettings.HELP)) {
                sb.append(command).append(" - ").append(entry.getValue()).append("\n");
            }
        }

        assertThat(server.help(userThreadMock).toString(), is(sb.toString()));
    }

    private void prepareDirectSend(Message message) throws Exception {
        Map<String, ServerToClientThread> users = prepareUsers();
        users.put("admin", userThreadMock);
        users.put("user", userThreadMock);
        users.put("geek", userThreadMock);
        when(userThreadMock.getReceivedMessage()).thenReturn(message);
        doNothing().when(userThreadMock).sendMessage(any(Message.class));
    }


    private Map<String, ServerToClientThread> prepareChangeLogin(Message message) throws Exception {
        Map<String, ServerToClientThread> users = prepareUsers();
        users.put("admin", userThreadMock);
        users.put("user", userThreadMock);
        users.put("geek", userThreadMock);

        doNothing().when(userThreadMock).sendMessage(any(Message.class));
        when(userThreadMock.getUser_name()).thenReturn("user");
        when(userThreadMock.getReceivedMessage()).thenReturn(message);
        return users;
    }

    private Map<String, ServerToClientThread> prepareUsersStub(ServerStub server) throws Exception{
        Field field = ServerStub.class.getDeclaredField("users");
        field.setAccessible(true);
        Map<String, ServerToClientThread> users = (Map<String, ServerToClientThread>)field.get(server);
        users.put("user1", userThreadMock);
        users.put("user2", userThreadMock);
        return users;
    }

    private Map<String, Method>  prepareUserMethods() throws  Exception{
        Field field = Server.class.getDeclaredField("user_methods");
        field.setAccessible(true);
        Map<String, Method> user_methods = (Map<String, Method>)field.get(server);
        Method[] methods = Server.class.getMethods();
        for (Method m: methods) {
            if (m.isAnnotationPresent(UserCommand.class)){
                user_methods.put(m.getAnnotation(UserCommand.class).commandName(), m);
            }
        }
        return user_methods;
    }

    private Map<String, ServerToClientThread> prepareUsers() throws  Exception{
        Field field = Server.class.getDeclaredField("users");
        field.setAccessible(true);
        Map<String, ServerToClientThread> users = (Map<String, ServerToClientThread>)field.get(server);

        return users;
    }

    @After
    public void stop(){
        System.setOut(null);
    }

}