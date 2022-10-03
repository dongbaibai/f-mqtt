package com.fmqtt.authorization.file;

import com.fmqtt.authorization.AbstractAuthorizationService;
import com.fmqtt.authorization.Action;
import com.fmqtt.authorization.Authorization;
import com.fmqtt.authorization.AuthorizationService;
import com.fmqtt.common.config.BrokerConfig;
import com.fmqtt.common.constant.AllConstants;
import com.fmqtt.common.util.TopicUtils;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.text.ParseException;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static java.nio.charset.StandardCharsets.UTF_8;

public class AclFileAuthorizationService extends AbstractAuthorizationService {

    private final static Logger log = LoggerFactory.getLogger(AclFileAuthorizationService.class);

    private final static String USER_KEYWORD = "user";
    private final static String TOPIC_KEYWORD = "topic";
    private Map<String, List<Authorization>> authorizations = Maps.newHashMap();

    public AclFileAuthorizationService() throws IOException, ParseException {
        File f = new File(AllConstants.DIRECTORY, BrokerConfig.aclFilePath);
        BufferedReader reader = Files.newBufferedReader(f.toPath(), UTF_8);
        ACLFileParser aclFileParser = new ACLFileParser();
        aclFileParser.parse(reader);
        log.info("AclFileAuthorizationService load finish, authorizations:[{}]", authorizations);
    }


    private Authorization createAuthorization(String line, String[] tokens) throws ParseException {
        if (tokens.length > 2) {
            try {
                Action action = Action.valueOf(tokens[1].toUpperCase());
                return new Authorization(line.substring(line.indexOf(tokens[2])), action);
            } catch (IllegalArgumentException iaex) {
                throw new ParseException("Invalid permission token", 1);
            }
        }
        return new Authorization(tokens[1]);
    }

    @Override
    public boolean doAuthorize(String topic, String username, String clientId, Action action) {
        if (StringUtils.isBlank(username)) {
            log.warn("Authorize failed, empty username");
            return false;
        }
        List<Authorization> authorizations = this.authorizations.get(username);
        if (CollectionUtils.isEmpty(authorizations)) {
            log.warn("Authorize failed, empty authorizations username:[{}]", username);
            return false;
        }

        // 1.match action first
        // 2.match topic lately
        return authorizations.stream()
                .filter(authorization ->
                        Action.READWRITE.name().equals(authorization.getAction().name())
                                || action.name().equals(authorization.getAction().name()))
                .anyMatch(authorization ->
                        TopicUtils.matchTopic(authorization.getTopic(), topic));
    }

    public class ACLFileParser {

        private boolean parsingUser;
        private String currentUser = "";

        public void parse(Reader reader) throws ParseException {
            BufferedReader br = new BufferedReader(reader);
            String line;

            Pattern emptyLine = Pattern.compile("^\\s*$");
            Pattern commentLine = Pattern.compile("^#.*");
            Pattern invalidCommentLine = Pattern.compile("^\\s*#.*");

            try {
                while ((line = br.readLine()) != null) {
                    if (line.isEmpty() || emptyLine.matcher(line).matches() || commentLine.matcher(line).matches()) {
                        // skip it's a black line or comment
                        continue;
                    } else if (invalidCommentLine.matcher(line).matches()) {
                        // it's a malformed comment
                        int commentMarker = line.indexOf('#');
                        throw new ParseException(line, commentMarker);
                    }

                    parse(line);
                }
            } catch (IOException ex) {
                throw new ParseException("Failed to read", 1);
            }
        }

        void parse(String line) throws ParseException {
            Authorization acl = parseAuthLine(line);
            if (acl == null) {
                // skip it's a user
                return;
            }
            if (parsingUser) {
                if (!authorizations.containsKey(currentUser)) {
                    authorizations.put(currentUser, Lists.newArrayList());
                }
                List<Authorization> userAuths = authorizations.get(currentUser);
                userAuths.add(acl);
            }
        }

        private Authorization parseAuthLine(String line) throws ParseException {
            String[] tokens = line.split("\\s+");
            String keyword = tokens[0].toLowerCase();
            switch (keyword) {
                case TOPIC_KEYWORD:
                    return createAuthorization(line, tokens);
                case USER_KEYWORD:
                    parsingUser = true;
                    currentUser = tokens[1];
                    return null;
                default:
                    throw new ParseException(String.format("Invalid line definition found %s", line), 1);
            }
        }

    }

}
