/*
 * Copyright 2014 Matthew Collins
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.co.thinkofdeath.prismarine.network;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import uk.co.thinkofdeath.prismarine.log.LogUtil;
import uk.co.thinkofdeath.prismarine.network.protocol.PacketHandler;
import uk.co.thinkofdeath.prismarine.network.protocol.ProtocolDirection;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class NetworkManager {

    private static final Logger logger = LogUtil.get(NetworkManager.class);
    private Channel channel;

    private boolean onlineMode = true;
    private KeyPair networkKeyPair;
    private ProtocolDirection incomingPacketType;

    public NetworkManager() {
    }

    public void listen(String address, int port, Class<? extends PacketHandler> initialHandler) {
        incomingPacketType = ProtocolDirection.SERVERBOUND;
        if (isOnlineMode()) {
            logger.info("Generating encryption keys");
            try {
                KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
                generator.initialize(1024);
                networkKeyPair = generator.generateKeyPair();
            } catch (NoSuchAlgorithmException e) {
                logger.info("Failed to generate encryption keys");
                throw new RuntimeException(e);
            }
        }

        logger.log(Level.INFO, "Starting on {0}:{1,number,#}",
                new Object[]{address, port});

        final EventLoopGroup group = new NioEventLoopGroup();
        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(group)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                .childHandler(new ConnectionInitializer(this, initialHandler))
                .childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                .childOption(ChannelOption.TCP_NODELAY, true);

        channel = bootstrap.bind(address, port)
                .channel();
        channel.closeFuture()
                .addListener(future -> group.shutdownGracefully());

    }

    public void close() {
        logger.info("Disconnecting players");
        channel.close().awaitUninterruptibly();
    }

    public boolean isOnlineMode() {
        return onlineMode;
    }

    public void setOnlineMode(boolean onlineMode) {
        this.onlineMode = onlineMode;
    }

    public KeyPair getNetworkKeyPair() {
        return networkKeyPair;
    }

    public ProtocolDirection getIncomingPacketType() {
        return incomingPacketType;
    }
}