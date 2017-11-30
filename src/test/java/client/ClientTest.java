package client;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import server.Server;
import settings.Message;
import settings.ServerSettings;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;


public class ClientTest {
    private ConnectionToServer connectionMock = mock(ConnectionToServer.class);
    private BufferedReader keyboardMock = mock(BufferedReader.class);
    private ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private String user = "user";

    @Before
    public void init() throws IOException {
        System.setOut(new PrintStream(outContent));
    }

    @Test
    public void loginIsFree() throws Exception {

        testLogin(new Message("", user, ServerSettings.LOGIN_CHECK),
                new Message("", user, ServerSettings.LOGIN_IS_FREE),
                false,
                true);
    }

    @Test
    public void loginIsBusy() throws Exception {

        testLogin(new Message("", user, ServerSettings.LOGIN_CHECK),
                new Message("", user, ServerSettings.LOGIN_IS_NOT_FREE),
                false,
                false);
    }

    @Test(expected = IOException.class)
    public void loginNoServerAnswer() throws Exception {

        testLogin(new Message("", user, ServerSettings.LOGIN_CHECK),
                null,
                true,
                false);
        verify(connectionMock, times(1)).closeConnection();
    }

    @Test
    public void testClientQuit() throws Exception {
        Message userMessage = new Message("hello from user", "user", "");
        Message serverAnswer = new Message("hello from server", "admin", "");

        setConnectionAndKeyboardProperties(userMessage, serverAnswer);
        startClient();
        verify(connectionMock, times(1)).closeConnection();
    }

    @Test
    public void testHugeServerAnswer() throws Exception {
        Message userMessage = new Message("hello from user", "user", "");
        Message serverAnswer = new Message("hello from server 11111111111111111111111111" +
                "2222222222222222222222222222222222222222222222222222222222222222222222222222222" +
                "ыыыыыыыыыыыыыыыыыыыыыыыыыыыыыыыыыыыыыыыыыыыыыыыыыыыыыыыыыыыыыыыыыыыыыыыыыыыыыыы" +
                "№№№№№№№№№№№№№№№№№№№№№№№№№№№№№№№№№№№№№№№№№№" +
                "###############################################################################" +
                "$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$" +
                "*******************************************************************************" +
                "@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@" +
                "......;;;;;;;;;,,,,,,///////////??????????````````````)))))))))((((((((((((((((" +
                "", "admin", "");

        setConnectionAndKeyboardProperties(userMessage, serverAnswer);
        startClient();
        verify(connectionMock, times(1)).closeConnection();
    }

    @Test
    public void testWrongServerAnswer() throws Exception {
        Message userMessage = new Message("hello from user", "user", "");
        setConnectionAndKeyboardProperties(userMessage, null);
        startClient();
        verify(connectionMock, times(2)).closeConnection();
    }


    private void setConnectionAndKeyboardProperties(Message userMessage, Message serverAnswer) throws Exception {
        when(connectionMock.isConnected()).thenReturn(true);
        if (serverAnswer != null)
            when(connectionMock.readFromServer()).thenReturn(serverAnswer);
        else
            when(connectionMock.readFromServer()).thenThrow(IOException.class);
        doNothing().when(connectionMock).sendToServer(userMessage);
        doNothing().when(keyboardMock).close();
        when(keyboardMock.readLine()).thenReturn(ServerSettings.QUIT);
    }

    private void startClient(){
        Client c = new Client(keyboardMock);
        c.connectToServer(connectionMock);
        c.startChat();
    }

    private void testLogin(Message input, Message serverAnswer,
                           boolean isException, boolean isTrueResult) throws Exception {

        when(connectionMock.isConnected()).thenReturn(true);
        doNothing().when(connectionMock).sendToServer(input);

        if (!isException)
            when(connectionMock.readFromServer()).thenReturn(serverAnswer);
        else
            doThrow(IOException.class).when(connectionMock).readFromServer();

        Client c = new Client();
        c.connectToServer(connectionMock);

        if (isTrueResult)
            assertTrue(c.login(user));
        else
            assertFalse(c.login(user));
    }


}