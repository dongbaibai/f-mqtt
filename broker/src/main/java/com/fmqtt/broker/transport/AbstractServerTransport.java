package com.fmqtt.broker.transport;

import com.fmqtt.broker.server.BrokerServer;
import com.fmqtt.common.lifecycle.AbstractLifecycle;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.ssl.SslContext;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public abstract class AbstractServerTransport extends AbstractLifecycle {

    protected EventLoopGroup bossGroup;
    protected EventLoopGroup workerGroup;
    protected Class<? extends ServerSocketChannel> channelClass;
    protected volatile SslContext sslContext;

    public AbstractServerTransport() {
        int core = Runtime.getRuntime().availableProcessors();
        Executor boss = new ThreadPoolExecutor(core, core, 0, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(1024),
                new ThreadFactoryBuilder().setNameFormat("boss-thread-%s").build());
        Executor worker = new ThreadPoolExecutor(core, core, 0, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(1024),
                new ThreadFactoryBuilder().setNameFormat("worker-thread-%s").build());
        this.bossGroup = BrokerServer.epoll() ? new EpollEventLoopGroup(core, boss) : new NioEventLoopGroup(core, boss);
        this.workerGroup = BrokerServer.epoll() ? new EpollEventLoopGroup(core, worker) : new NioEventLoopGroup(core, worker);
        this.channelClass = BrokerServer.epoll() ? EpollServerSocketChannel.class : NioServerSocketChannel.class;
    }

    public void shutdown() {
        this.bossGroup.shutdownGracefully();
        this.workerGroup.shutdownGracefully();
    }

}
