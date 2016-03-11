/*
 * Copyright (c) 2015, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.
 */

package org.wso2.carbon.transport.http.netty.sender;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpContentCompressor;
import io.netty.handler.codec.http.HttpRequestEncoder;
import io.netty.handler.codec.http.HttpResponseDecoder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.carbon.transport.http.netty.common.ssl.SSLHandlerFactory;
import org.wso2.carbon.transport.http.netty.config.SenderConfiguration;
import org.wso2.carbon.transport.http.netty.sender.channel.BootstrapConfiguration;

/**
 * A class that responsible for initialize target server pipeline.
 */
public class NettyClientInitializer extends ChannelInitializer<SocketChannel> {

    private static final Logger log = LoggerFactory.getLogger(NettyClientInitializer.class);

    private SenderConfiguration senderConfiguration;

    protected static final String HANDLER = "handler";
    private TargetHandler handler;
    private int soTimeOut;

    public NettyClientInitializer(SenderConfiguration senderConfiguration) {
        this.senderConfiguration = senderConfiguration;
        soTimeOut = BootstrapConfiguration.getInstance().getSocketTimeout();
    }

    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        // Add the generic handlers to the pipeline
        // e.g. SSL handler
        if (senderConfiguration.getSslConfig() != null) {
            log.debug("adding ssl handler");
            SslHandler sslHandler = new SSLHandlerFactory(senderConfiguration.getSslConfig()).create();
            sslHandler.engine().setUseClientMode(true);
            ch.pipeline().addLast("ssl", sslHandler);
        }
        ch.pipeline().addLast("compressor", new HttpContentCompressor());
        ch.pipeline().addLast("decoder", new HttpResponseDecoder());
        ch.pipeline().addLast("encoder", new HttpRequestEncoder());
        ch.pipeline().addLast("chunkWriter", new ChunkedWriteHandler());


        if (senderConfiguration.isDisruptorOn()) {
            log.debug("Register target handler in pipeline which will dispatch events to Disruptor threads");
            handler = new TargetHandler(soTimeOut);
            ch.pipeline().addLast(HANDLER, handler);
        } else {
            log.debug("Register  engine dispatching handler in pipeline ");
            handler = new EngineDispatchingTargetHandler(soTimeOut, senderConfiguration);
            ch.pipeline().addLast(HANDLER, handler);
        }


    }

    public TargetHandler getTargetHandler() {
        return handler;
    }
}
