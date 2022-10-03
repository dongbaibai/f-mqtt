package com.fmqtt.metric.handler;

import com.fmqtt.metric.MetricService;
import com.fmqtt.metric.metrics.Counter;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

import static io.netty.channel.ChannelFutureListener.CLOSE_ON_FAILURE;

@ChannelHandler.Sharable
public class MessageMetricHandler extends ChannelDuplexHandler {

    private final MetricService metricService;
    private final Counter sentCount;
    private final Counter sentBytes;
    private final Counter receivedCount;
    private final Counter receivedBytes;

    public MessageMetricHandler(MetricService metricService) {
        this.metricService = metricService;
        sentCount = metricService.counter("message.sent.count", "$SYS/sent/count");
        sentBytes = metricService.counter("message.sent.bytes", "$SYS/sent/bytes");
        receivedCount = metricService.counter("message.received.count", "$SYS/received/count");
        receivedBytes = metricService.counter("message.received.bytes", "$SYS/received/bytes");
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        sentCount.inc();
        sentBytes.inc(((ByteBuf) msg).readableBytes());
        ctx.write(msg, promise).addListener(CLOSE_ON_FAILURE);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        receivedCount.inc();
        receivedBytes.inc(((ByteBuf) msg).readableBytes());
        ctx.fireChannelRead(msg);
    }

}
