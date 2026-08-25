package com.kylenicholls.stash.parameterizedbuilds.ciserver;

import com.atlassian.bitbucket.project.ProjectService;
import com.atlassian.bitbucket.user.ApplicationUser;
import com.kylenicholls.stash.parameterizedbuilds.item.UserToken;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.codehaus.jackson.map.ObjectMapper;

public class AccountServer extends CIServer {

    private static final String PROJECT_TOKENS_KEY = "projectTokens";
    private static final String USER_KEY = "user";

    private final transient ProjectService projectService;
    private ApplicationUser user;
    private Jenkins jenkins;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public AccountServer(Jenkins jenkins, ApplicationUser user, ProjectService projectService){
        this.jenkins = jenkins;
        this.user = user;
        this.projectService = projectService;
        this.JENKINS_SETTINGS = "jenkins.user.settings";
        this.ADDITIONAL_JS = "jenkins-user-settings-form";
    }

    public Map<String, Object> renderMap(Map<String, Object> renderOptions){
        List<UserToken> projectTokens = jenkins
                .getAllUserTokens(user, projectService.findAllKeys(), projectService);

        List<Map<String, Object>> tokenMaps = projectTokens.stream()
                .map(UserToken::asMap)
                .toList();

        final String projectTokensJson;

        try {
            projectTokensJson = OBJECT_MAPPER.writeValueAsString(tokenMaps);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Unable to serialize Jenkins project tokens",
                    e);
        }

        @SuppressWarnings("serial")
        Map<String, Object> baseMap = new LinkedHashMap<String, Object>() {{
            put(USER_KEY, user);
            put(PROJECT_TOKENS_KEY, projectTokensJson);
            putAll(renderOptions);
        }};
        return Collections.unmodifiableMap(baseMap);
    }
}
