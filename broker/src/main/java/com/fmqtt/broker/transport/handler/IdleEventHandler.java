package com.fmqtt.broker.transport.handler;

import com.fmqtt.authorization.Action;
import com.fmqtt.authorization.AuthorizationService;
import com.fmqtt.broker.util.ChannelInfo;
import com.fmqtt.common.events.DisconnectType;
import com.fmqtt.common.message.RetainMessage;
import com.fmqtt.common.message.Will;
import com.fmqtt.common.util.ExecutorServiceUtils;
import com.fmqtt.common.util.TransportUtils;
import com.fmqtt.session.Session;
import com.fmqtt.session.SessionService;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IdleEventHandler extends ChannelDuplexHandler {

    private final static Logger log = LoggerFactory.getLogger(IdleEventHandler.class);

    private final SessionService sessionService;
    private final AuthorizationService authorizationService;

    public IdleEventHandler(SessionService sessionService,
                            AuthorizationService authorizationService) {
        this.sessionService = sessionService;
        this.authorizationService = authorizationService;
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleState e = ((IdleStateEvent) evt).state();
            if (e == IdleState.ALL_IDLE) {
                Session session = ChannelInfo.session(ctx.channel());
                String username = ChannelInfo.username(ctx.channel());
                log.info("Close idle channel, addr:[{}], clientId:[{}], username:[{}]",
                        TransportUtils.parseChannelRemoteAddr(ctx.channel()), session.getClientId(), username);

                handleIdle(username, session, ctx.channel());
            }
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }

    private void handleIdle(String username, Session session, Channel channel) {
        final String clientId = session.getClientId();
        final Will will = session.getWill();
        if (will != null) {
            RetainMessage retainMessage = will.getMessage();
            if (!authorizationService.authorize(retainMessage.getTopic(), username,
                    clientId, Action.WRITE)) {
                log.error("Check WRITE Permission failed username:[{}], clientId:[{}], topic:[{}]"
                        , username, clientId, retainMessage.getTopic());
                return;
            }

            sessionService.handlePublish(clientId, retainMessage.getTopic(),
                    retainMessage.getPayload(), session.nextMessageId(),
                    retainMessage.getQos(), false, will.isRetain());
        }

        sessionService.closeSession(clientId, username, DisconnectType.PASSIVE)
                .thenAcceptAsync(result -> {
                    if (!result) {
                        log.error("Fail to closeSession for clientId:[{}]", clientId);
                        TransportUtils.dropConnection(MqttConnectReturnCode.CONNECTION_REFUSED_SERVER_UNAVAILABLE, channel);
                    }
                }, ExecutorServiceUtils.COMMON);
    }

}
