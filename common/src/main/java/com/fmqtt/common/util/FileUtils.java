package com.fmqtt.common.util;

import com.fmqtt.common.constant.AllConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public abstract class FileUtils {

    private final static Logger log = LoggerFactory.getLogger(FileUtils.class);

    public static Properties getPropertiesFromFile(String filePath) throws IOException {
        Properties properties = new Properties();
        File file = new File(AllConstants.DIRECTORY, filePath);
        if (!(file.exists() && file.isFile() && file.canRead())) {
            log.warn("Fail to getProperties for Config file:[{}]", file.getPath());
        } else {
            FileInputStream is = null;
            try {
                is = new FileInputStream(new File(AllConstants.DIRECTORY, filePath));
                properties.load(is);
            } finally {
                if (is != null) {
                    is.close();
                }
            }
        }
        return properties;
    }

}
