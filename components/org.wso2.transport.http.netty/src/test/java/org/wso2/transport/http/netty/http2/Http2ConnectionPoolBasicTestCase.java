/*
 *  Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 */

package org.wso2.transport.http.netty.http2;

import io.netty.handler.codec.http.HttpMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wso2.transport.http.netty.contract.Constants;
import org.wso2.transport.http.netty.contract.HttpClientConnector;
import org.wso2.transport.http.netty.contract.HttpWsConnectorFactory;
import org.wso2.transport.http.netty.contract.ServerConnector;
import org.wso2.transport.http.netty.contract.ServerConnectorException;
import org.wso2.transport.http.netty.contract.ServerConnectorFuture;
import org.wso2.transport.http.netty.contract.config.ListenerConfiguration;
import org.wso2.transport.http.netty.contract.config.SenderConfiguration;
import org.wso2.transport.http.netty.contract.config.ServerBootstrapConfiguration;
import org.wso2.transport.http.netty.contract.config.TransportsConfiguration;
import org.wso2.transport.http.netty.contractimpl.DefaultHttpWsConnectorFactory;
import org.wso2.transport.http.netty.message.HttpCarbonMessage;
import org.wso2.transport.http.netty.message.HttpConnectorUtil;
import org.wso2.transport.http.netty.message.HttpMessageDataStreamer;
import org.wso2.transport.http.netty.passthrough.PassthroughMessageProcessorListener;
import org.wso2.transport.http.netty.util.TestUtil;
import org.wso2.transport.http.netty.util.client.http2.MessageGenerator;
import org.wso2.transport.http.netty.util.client.http2.MessageSender;
import org.wso2.transport.http.netty.util.server.HttpServer;
import org.wso2.transport.http.netty.util.server.initializers.Http2SendChannelIDInitializer;

import java.util.HashMap;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNotNull;
import static org.wso2.transport.http.netty.util.TestUtil.SERVER_CONNECTOR_PORT;


/**
 * Test case for HTTP/2 client connection pool.
 */
public class Http2ConnectionPoolBasicTestCase {
    private static final Logger LOG = LoggerFactory.getLogger(Http2ConnectionPoolBasicTestCase.class);

    private HttpWsConnectorFactory httpWsConnectorFactory;
    private ServerConnector serverConnector;
    private HttpServer http2Server;

    @BeforeClass
    public void setup() {
        http2Server = TestUtil
            .startHTTPServer(TestUtil.HTTP_SERVER_PORT, new Http2SendChannelIDInitializer(), 1, 2);

        httpWsConnectorFactory = new DefaultHttpWsConnectorFactory(1, 2, 2);
        ListenerConfiguration listenerConfiguration = new ListenerConfiguration();
        listenerConfiguration.setPort(SERVER_CONNECTOR_PORT);
        listenerConfiguration.setScheme(Constants.HTTP_SCHEME);
        listenerConfiguration.setVersion(String.valueOf(Constants.HTTP_2_0));
        serverConnector = httpWsConnectorFactory
            .createServerConnector(new ServerBootstrapConfiguration(new HashMap<>()), listenerConfiguration);
        ServerConnectorFuture serverConnectorFuture = serverConnector.start();
        TransportsConfiguration transportsConfiguration = new TransportsConfiguration();
        SenderConfiguration h2cSenderConfiguration = HttpConnectorUtil.getSenderConfiguration(transportsConfiguration,
                                                                                              Constants.HTTP_SCHEME);
        h2cSenderConfiguration.setHttpVersion(String.valueOf(Constants.HTTP_2_0));
        serverConnectorFuture.setHttpConnectorListener(
            new PassthroughMessageProcessorListener(h2cSenderConfiguration, true));
        try {
            serverConnectorFuture.sync();
        } catch (InterruptedException e) {
            LOG.warn("Interrupted while waiting for server connector to start");
        }
    }

    @Test
    public void testH2CUpgradeWithPool() {
        //Since we have only two eventloops, upstream will have two different pools.
        HttpClientConnector client1 = getTestClient(); //Upstream uses eventloop1 pool
        String response1 = getResponse(client1);
        HttpClientConnector client2 = getTestClient(); //Upstream uses eventloop2 pool
        String response2 = getResponse(client2);
        HttpClientConnector client3 = getTestClient(); //Upstream uses eventloop1 pool
        String response3 = getResponse(client3);
        HttpClientConnector client4 = getTestClient(); //Upstream uses eventloop2 pool
        String response4 = getResponse(client4);

        assertNotEquals(response1, response2,
                        "Client uses two different pools, hence response 1 and 2 should not be equal");
        assertNotEquals(response3, response4,
                        "Client uses two different pools, hence response 3 and 4 should not be equal");
        assertEquals(response1, response3, "Client uses the same pool, hence response 1 and 3 should be equal");
        assertEquals(response2, response4, "Client uses the same pool, hence response 2 and 4 should be equal");
    }

    @Test
    public void testPriorKnowledgeWithPool() {

    }

    @Test
    public void testH2ClientWithPool() {

    }

    private String getResponse(HttpClientConnector client1) {
        HttpCarbonMessage httpCarbonMessage = MessageGenerator.generateRequest(HttpMethod.GET, null,
                                                                               SERVER_CONNECTOR_PORT);
        HttpCarbonMessage response = new MessageSender(client1).sendMessage(httpCarbonMessage);
        assertNotNull(response);
        return TestUtil.getStringFromInputStream(new HttpMessageDataStreamer(response).getInputStream());
    }

    @AfterClass
    public void cleanUp() throws ServerConnectorException {
        try {
            serverConnector.stop();
            http2Server.shutdown();
            httpWsConnectorFactory.shutdown();
        } catch (Exception e) {
            LOG.warn("Interrupted while waiting for response two", e);
        }
    }

    /**
     * Get the test client. Each test client has their own connection manager and does not use source pools.
     *
     * @return
     */
    private HttpClientConnector getTestClient() {
        TransportsConfiguration transportsConfiguration = new TransportsConfiguration();
        SenderConfiguration senderConfiguration = HttpConnectorUtil.getSenderConfiguration(transportsConfiguration,
                                                                                           Constants.HTTP_SCHEME);
        senderConfiguration.setHttpVersion(String.valueOf(Constants.HTTP_2_0));
//        senderConfiguration.setForceHttp2(true);       // Force to use HTTP/2 without an upgrade
        return httpWsConnectorFactory.createHttpClientConnector(
            HttpConnectorUtil.getTransportProperties(transportsConfiguration), senderConfiguration);
    }
}
