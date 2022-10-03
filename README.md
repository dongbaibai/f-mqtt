# f-mqtt

    f-mqtt 是开源的,支持集群,高可用的 mqtt broker 实现
    目前支持 3.1.1 和 3.1 版本的 mqtt 协议,服务质量仅支持 qos 0 和 1
    f-mqtt 试图提供一个可自定义的 mqtt broker 框架,
    基于该框架你可以自定义属于符合自身要求的 mqtt broker

## 集群细节

###1.数据同步实现

    1.订阅树
        1.作用
            用于构建全局订阅树,有了全局订阅树之后,才可以通过topic找到对应的订阅客户端
            再根据clientId拿到Session信息,这样就可以通过Session中的Channel发送消息
        2.存储方式
            对于mqtt broker来说,主要的工作就是收发消息,所以订阅树以内存的方式存在是最好的
        3.同步方式
            订阅信息的载体是Session信息,同步方式详见Session同步
            基于客户端的subscribe、unsubscribe,对订阅树进行增删改
            具体实现详见,subscription-service中的MemorySubscriptionService
            
    2.Session信息
        1.作用
            Session作为客户端载体,承载了该客户端的订阅信息,queue信息,元数据信息等
        2.存储方式
            cleanSession=true,无需进行持久化,故只考虑cleanSession=false的情况
                将Session信息分成两部分进行存储
                    1.元数据
                        1.本地存储「由于每个节点都是对等节点,如果存在大量的持久化Session,建议使用第三方存储」
                            Session信息的数据结构属于JSON,存在增删改的场景
                            对于本地存储,需要实现JSON的增量更新较为复杂
                            最终选型,h2-mvstore来实现「支持事务、mvcc等」
                            具体实现详见,session-service中的H2SessionService
                        2.第三方存储
                            通过Hash结构对会话信息进行存储
                            具体实现详见,session-service中的RedisSessionService
                    2.Queue队列
                        1.第三方存储
                            由于消息的特殊性,直接通过第三方进行存储
                            在Redis中构建了三个key
                            1.inflight
                                Hash结构,clientId+":"+inflight为key,messageId为field,value为QueueMessage
                            2.queueList,List结构
                                List结构,clientId+":"+queueList为key,messageId为value,为了保证队列的顺序
                            3.clientId+":"+queueMap
                                Hash结构,clientId+":"+queueMap为key,messageId为field,value为QueueMessage,
                                为了对齐inflight,便于在Lua中更好的进行queueMap到inflight的消息转移
                            当broker.session.maxInflightMessages=1时,就能保证QoS1消息的顺序
                            具体实现详见,queue-service中的RedisQueueService
                    3.订阅信息
                            
        3.同步方式,最终一致性
            1.基于消息中间件,能够对消息进行持久化,并且可以削峰填谷,
                1.如果是本地存储的话,
                    可以通过消费Kafka重新构建最新的订阅信息,
                    对于一个全新的消费者组来说,通过消费所有消息来构建Session信息是不符合预期的.
                2.如果是第三方存储的话,
                    因为消息中间件属于增量同步,速度很快,
                    如果broker定期做全量同步的话,对收发消息影响很大.
                    
                最终选型,Kafka,有了Kafka之后为后期切换存储方式也提供遍历,
                仅需把Kafka中的数据ETL到第三方即可
                        
            2.基于RPC请求通知,需要实现broker之间的服务发现,并且还不能解决持久化问题
            
            3.消息顺序
                1.保证消息顺序的意义
                    比如同一个主题,客户端取消订阅事件先于订阅事件被消费,会导致一直订阅着某个主题;
                    相反订阅事件先于取消订阅事件,会导致订阅丢失.
                    
                    无论存储、同步方式如何,顺序都逃不掉的
                
                2.实现方式
                    1.同一JVM中,需要对Session加锁,保证线程安全,
                        最终对clientId进行加锁
                    2.不同JVM之间,
                        1.首先排除通过消息中间件本身来保持顺序的方式,性能太低
                        2.最终采用版本号的方式保证消息顺序
                            1.在处理客户端的connect、disconnect时,会自增该clientId对应的sessionVersion,
                                当消费connect、disconnect消息时,会判断消息的版本号是否低于全局版本号,低于则忽略
                                
                            2.在处理客户端的subscribe、unsubscribe时,会自增该clientId+topic对应的subscriptionVersion
                                当消费subscribe、unsubscribe消息时,会判断消息的版本号是否低于全局版本号,低于则忽略
                        
                            3.版本号的方式会在connect、disconnect、subscribe、unsubscribe时,
                                需要与获取全局的版本号,这里会有损耗,但是这四类消息量相对较少,是可以接收的
                            
                            4.Queue信息
                                在处理客户端的connect时,会自增该clientId对应的queueVersion,「仅此一个地方」
                                    随后对queue的操作,添加消息、删除消息、删除queue队列,都会判断当前版本是否低于全局版本号,低于则忽略
                                    如果出现乱序,则会导致上个版本号的消息存在遗留的问题,但是这也满足QoS1至少一次的语义,
                                    并且通常情况下,断开连接之后不会立即连接上.
                                    
                            5.基于Redis实现,利用Lua脚本保证多个操作的原子性
                            
    3.retain信息
        1.存储方式
            1.本地存储
                具体实现详见,retain-service中的H2QueueService
                「大量Retain消息,或经常变更的情况下,建议使用第三方存储,并且关闭Retain消息缓存,retain.cache.enable」
            2.第三方存储
                具体实现详见,retain-service中的RedisQueueService,「」
        2.同步方式,最终一致性
            1.基于版本实现

###2.消息转发
1.概念
对订阅相同topic,但并未连接同一broker的客户端进行消息转发
例如c1,c2两个客户端分别连接到b1,b2,并且同时订阅了t1这个topic,
现c3往b3的t1发送了消息,那么c1,c2就需要通过消息转发,接收到该消息
2.实现
为了提升速度,需要通过集群间的通信来实现消息转发,
在现有集群通信框架JGroup、Akka中,最终选择通过Akka来实现集群通信

# 组件说明TODO,重新补充一下
* authentication-service,身份认证服务

  提供基于username、password、clientId的身份认证方式
  身份认证信息可以存储在MySQL、Redis、PASSWORD_FILE中,或对远程服务进行调用
  默认提供实现为PASSWORD_FILE
  最佳实践「存储在MySQL中,通过Redis或者LocalCache进行加速」

* authorization-service,授权服务

  提供基于topic, username, clientId, action的授权方式
  权限信息可以存储在MySQL、Redis、ACL_FILE中,或对远程服务进行调用
  默认提供实现为ACL_FILE
  最佳实践「存储在MySQL中,通过Redis或者LocalCache进行加速」

* subscription-service,订阅服务

  目前支持Hash或Trie数据结构
  如果你不需要使用通配符「#,+」那么Hash是最好的选择
  对于Trie,提供对topic层数的限制,从而最低限度保证字典树的性能
  默认提供实现为MemorySubscriptionService,位于内存中,读写性能都很高,
  根据Session的Connect、Disconnect、Subscribe、Unsubscribe事件进行构建订阅信息
  使用读写锁,保证订阅信息的线程安全

* queue-service,队列服务

  实现对qos1消息队列管理,基于clientId来保证线程安全

* plugin-service,插件服务

  支持以下操作的异步回调函数
  connect
  disconnect
  connectionLost
  subscribe
  unsubscribe
  publish
  ack(qos1)

* retain-service

  retain消息的服务「包含Publish以及Will中的消息」

* session-service

  通过session-service,将对Session的操作通过ClientId进行线程安全的保证

* event-service

  核心功能为事件持久化、可回溯、削峰填谷,event-service本身的可伸缩性

  以事件的形式将CONNECT、DISCONNECT、SUBSCRIBE、UNSUBSCRIBE、ADD_RETAIN进行分发,并且持久化
  保证接入event-service的节点能够接收重要事件

  数据同步问题,对于Broker来说,本身仅做MQTT协议层的解析,并且仅存在内存相关的操作
  对于Session同步,订阅信息同步,Retain同步以及Queue消息全部交由消息总线的消费方进行处理「Kafka-Stream」
  对于消息的顺序问题,可以通过version-service进行解决「目前仅考虑了Long.MAX_VALUE,后续空了再说」

  但是对于一个操作中发送多条消息,并没有实现事务操作,这是后续需要优化的点

* cluster-service

  核心功能为服务发现以及消息转发「PublishMessage」

  目前默认提供AkkaClusterService,借助Akka快速实现一套成熟的分布式集群

  对于Akka不熟悉的同学,无须担心,后期会提供另外的实现方式

* metric-service

  核心功能,1.指标类型的提供,2.指标的采集发送

  ##### 指标如下
  1.client,客户端

        $SYS/client/connected,在线客户端数
        $SYS/client/disconnected,在Broker上注册但当前断开连接的持久客户机(禁用干净会话)的总数

  2.retained messages,retain消息数

        $SYS/retain/count,Broker上活动的保留消息的总数,Broker上活动的保留消息的总数

  3.messages,消息

        $SYS/sent/count,自Broker启动以来发送的任何类型的消息的总数
        $SYS/sent/bytes,自Broker启动以来发送的任何类型的消息的总大小
        $SYS/received/count,自Broker启动以来接收到的任何类型的消息的总数
        $SYS/received/bytes,自Broker启动以来接收到的任何类型的消息的总大小

  4.store messages,消息存储

        $SYS/inflight/count,所有session中inflight消息的总数
        $SYS/queued/count,所有session中queued消息的总数     

* limiter-service

  限流服务

  默认提供SentinelLimitingService,
  基于Sentinel对不同的MqttMessageType进行资源定义,
  然后将基于QPS进行限流

* third-part-service

  第三方服务

  RedissonService基于Redisson与Redis进行交互

# Building

    $ mvn -DskipTests clean install -U
    
    . -d 「f-mqtt root directory」

# todo list

    如果有兴趣的同学,期待你的加入

    出错了之后,异常信息打印出来呢
    