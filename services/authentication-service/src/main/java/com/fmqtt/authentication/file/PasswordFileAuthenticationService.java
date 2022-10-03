package com.fmqtt.authentication.file;

import com.fmqtt.authentication.AbstractAuthenticationService;
import com.fmqtt.common.config.BrokerConfig;
import com.fmqtt.common.util.AssertUtils;
import com.fmqtt.common.util.FileUtils;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Properties;

public class PasswordFileAuthenticationService extends AbstractAuthenticationService {

    private final static Logger log = LoggerFactory.getLogger(PasswordFileAuthenticationService.class);

    private Properties users;

    public PasswordFileAuthenticationService() throws IOException {
        AssertUtils.isTrue(BrokerConfig.pwdFilePath != null
                , BrokerConfig.PWD_FILE_PATH + " MUST be set");
        this.users = FileUtils.getPropertiesFromFile(BrokerConfig.pwdFilePath);
        log.info("PasswordFile load finish, users:[{}]", users);
    }

    @Override
    public boolean doAuthenticate(String username, byte[] password, String clientId) {
        String pwd = users.getProperty(username);
        if (StringUtils.isBlank(pwd)) {
            return false;
        }
        return pwd.equals(DigestUtils.sha256Hex(password));
    }

}
