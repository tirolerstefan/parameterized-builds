package com.kylenicholls.stash.parameterizedbuilds.ciserver;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.atlassian.bitbucket.project.ProjectService;
import com.atlassian.bitbucket.user.ApplicationUser;
import com.kylenicholls.stash.parameterizedbuilds.item.UserToken;

public class AccountServer extends CIServer {

    private static final String PROJECT_TOKENS_KEY = "projectTokens";
    private static final String USER_KEY = "user";

    private final transient ProjectService projectService;
    private final ApplicationUser user;
    private final Jenkins jenkins;

    public AccountServer(
            Jenkins jenkins,
            ApplicationUser user,
            ProjectService projectService) {
        this.jenkins = jenkins;
        this.user = user;
        this.projectService = projectService;
        this.JENKINS_SETTINGS = "jenkins.user.settings";
        this.ADDITIONAL_JS = "jenkins-user-settings-form";
    }

    @Override
    public Map<String, Object> renderMap(
            Map<String, Object> renderOptions) {

        List<UserToken> projectTokens = jenkins
                .getAllUserTokens(
                        user,
                        projectService.findAllKeys(),
                        projectService);

        String projectTokensJson = projectTokens.stream()
                .map(UserToken::toJson)
                .collect(Collectors.joining(",", "[", "]"));

        Map<String, Object> baseMap = new LinkedHashMap<>();
        baseMap.put(USER_KEY, user);
        baseMap.put(PROJECT_TOKENS_KEY, projectTokensJson);
        baseMap.putAll(renderOptions);

        return Collections.unmodifiableMap(baseMap);
    }
}