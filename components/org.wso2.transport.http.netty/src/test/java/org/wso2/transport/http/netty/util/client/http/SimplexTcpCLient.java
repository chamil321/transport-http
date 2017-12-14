package org.wso2.transport.http.netty.util.client.http;

import io.netty.handler.codec.http.HttpMethod;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.URI;

/**
 * Created by chamil on 12/3/17.
 */
public class SimplexTcpCLient {

    public void createAndSendRequest(URI baseURI, String path, HttpMethod method, String stringContent) throws IOException {
        String host = baseURI.getHost();
        int port = baseURI.getPort();
        String uri = path;
        HttpMethod method1 = method;

//        String sentence;
//        String modifiedSentence;
//        BufferedReader inFromUser = new BufferedReader(new InputStreamReader(System.in));
        Socket clientSocket = new Socket(host, port);
        DataOutputStream outToServer = new DataOutputStream(clientSocket.getOutputStream());
//        BufferedReader inFromServer = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
//        sentence = inFromUser.readLine();
        outToServer.writeBytes(stringContent + '\n');
//        modifiedSentence = inFromServer.readLine();
//        System.out.println("FROM SERVER: " + modifiedSentence);
        clientSocket.close();
    }
}
