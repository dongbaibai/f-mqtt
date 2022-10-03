package com.fmqtt.broker.transport;

import com.fmqtt.common.config.BrokerConfig;
import com.fmqtt.common.config.TlsMode;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.cert.CertificateException;
import java.util.List;
import java.util.function.Consumer;

public class TransportServer extends AbstractServerTransport {

    private final static Logger log = LoggerFactory.getLogger(TransportServer.class);

    private final Consumer<ChannelPipeline> pipelineConsumer;

    public TransportServer(Consumer<ChannelPipeline> pipelineConsumer) {
        this.pipelineConsumer = pipelineConsumer;
    }

    private void startPlainTCPTransport() throws InterruptedException {
        log.info("Plain TCP transport started, port:[{}]",
                startServer(BrokerConfig.mqttPort, null));
    }

    private void startWebSocketTransport() throws InterruptedException {
        log.info("WebSocket TCP transport started, port:[{}]",
                startServer(BrokerConfig.wsPort, pipeline -> {
                    pipeline.addLast(new HttpServerCodec());
                    pipeline.addLast("aggregator", new HttpObjectAggregator(65536));
                    pipeline.addLast("webSocketHandler",
                            new WebSocketServerProtocolHandler(BrokerConfig.websocketPath, BrokerConfig.protocolCsvList, false, BrokerConfig.maxFrameSize));
                    pipeline.addLast("ws2bytebufDecoder", new WebSocketFrameToByteBufDecoder());
                    pipeline.addLast("bytebuf2wsEncoder", new ByteBufToWebSocketFrameEncoder());
                }));
    }

    private void startSSLTCPTransport() throws InterruptedException {
        log.info("SSL TCP transport started, port:[{}]",
                startServer(BrokerConfig.mqttsPort, pipeline ->
                        pipeline.addLast(sslContext.newHandler(pipeline.channel().alloc()))));
    }

    private void startWSSTransport() throws InterruptedException {
        log.info("WSS TCP transport started, port:[{}]",
                startServer(BrokerConfig.wssPort, pipeline -> {
                    pipeline.addLast(sslContext.newHandler(pipeline.channel().alloc()));
                    pipeline.addLast(new HttpServerCodec());
                    pipeline.addLast("aggregator", new HttpObjectAggregator(65536));
                    pipeline.addLast("webSocketHandler",
                            new WebSocketServerProtocolHandler(BrokerConfig.websocketPath, BrokerConfig.protocolCsvList, false, BrokerConfig.maxFrameSize));
                    pipeline.addLast("ws2bytebufDecoder", new WebSocketFrameToByteBufDecoder());
                    pipeline.addLast("bytebuf2wsEncoder", new ByteBufToWebSocketFrameEncoder());
                }));
    }

    private int startServer(int port, Consumer<ChannelPipeline> special) throws InterruptedException {
        ServerBootstrap serverBootstrap = new ServerBootstrap();
        serverBootstrap.group(workerGroup, bossGroup)
                .channel(channelClass)
                .option(ChannelOption.SO_BACKLOG, BrokerConfig.nettySoBacklog)
                .option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                .childOption(ChannelOption.TCP_NODELAY, BrokerConfig.nettyTcpNodelay)
                .childOption(ChannelOption.SO_KEEPALIVE, BrokerConfig.nettySoKeepalive)
                .childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                .childOption(ChannelOption.SO_SNDBUF, BrokerConfig.serverSocketSndBufSize)
                .childOption(ChannelOption.SO_RCVBUF, BrokerConfig.serverSocketRcvBufSize)
                .localAddress(new InetSocketAddress(port))
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        if (special != null) {
                            special.accept(ch.pipeline());
                        }
                        TransportServer.this.pipelineConsumer.accept(ch.pipeline());
                    }
                });
        ChannelFuture future = serverBootstrap.bind().sync();
        InetSocketAddress addr = (InetSocketAddress) future.channel().localAddress();
        return addr.getPort();
    }

    public void startSSL() throws InterruptedException {
        TlsMode tlsMode = BrokerConfig.tlsMode;
        log.info("Server is running in TLS {} mode", tlsMode.getName());

        if (tlsMode != TlsMode.DISABLED) {
            try {
                sslContext = TlsHelper.buildSslContext();
                log.info("SSLContext created for server");

                startSSLTCPTransport();
                startWSSTransport();

            } catch (CertificateException | IOException e) {
                log.error("Failed to create SSLContext for server", e);
            }
        }
    }

    @Override
    public void start() throws Exception {
        startPlainTCPTransport();
        startWebSocketTransport();
        startSSL();
    }

    static class WebSocketFrameToByteBufDecoder extends MessageToMessageDecoder<BinaryWebSocketFrame> {

        @Override
        protected void decode(ChannelHandlerContext chc, BinaryWebSocketFrame frame, List<Object> out)
                throws Exception {
            // convert the frame to a ByteBuf
            ByteBuf bb = frame.content();
            bb.retain();
            out.add(bb);
        }
    }

    static class ByteBufToWebSocketFrameEncoder extends MessageToMessageEncoder<ByteBuf> {

        @Override
        protected void encode(ChannelHandlerContext chc, ByteBuf bb, List<Object> out) throws Exception {
            // convert the ByteBuf to a WebSocketFrame
            BinaryWebSocketFrame result = new BinaryWebSocketFrame();
            result.content().writeBytes(bb);
            out.add(result);
        }
    }

}
