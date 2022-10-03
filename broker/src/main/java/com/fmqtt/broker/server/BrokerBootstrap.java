package com.fmqtt.broker.server;

import com.fmqtt.common.config.BrokerConfig;
import com.fmqtt.common.constant.AllConstants;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;
import org.apache.log4j.PropertyConfigurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 启动类
 */
public class BrokerBootstrap {

    private final static Logger log = LoggerFactory.getLogger(BrokerBootstrap.class);

    private final static String LOGGER_PATH = "/log4j.properties";

    public static void main(String[] args) throws Exception {
        Options options = new Options();
        Option directoryOpt = new Option("d", "directory", true,
                "fmqtt.home directory");
        directoryOpt.setRequired(true);
        options.addOption(directoryOpt);
        PosixParser parser = new PosixParser();
        CommandLine commandLine = parser.parse(options, args);
        String fmqttHome = commandLine.getOptionValue("d");
        PropertyConfigurator.configure(fmqttHome + LOGGER_PATH);
        log.info("Starting Broker fmqtt.home:[{}]", fmqttHome);
        AllConstants.setDirectory(fmqttHome);
        BrokerServer brokerServer = new BrokerServer();
        BrokerConfig.loadConfig();
        try {
            brokerServer.start();
            log.info("Broker start successfully");
        } catch (Throwable e) {
            e.printStackTrace();
            System.exit(-1);
        }
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                brokerServer.shutdown();
            } catch (Exception e) {
                log.error("Error occurs to shutdown", e);
            }
        }));
    }

}
