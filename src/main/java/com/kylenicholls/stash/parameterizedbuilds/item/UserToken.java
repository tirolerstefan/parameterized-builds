package com.kylenicholls.stash.parameterizedbuilds.item;

import java.util.LinkedHashMap;
import java.util.Map;

public class UserToken {
    private String baseUrl;
    private String alias;
    private String projectKey;
    private String projectName;
    private String userSlug;
    private String token;

    public UserToken(String baseUrl, String alias, String projectKey, String projectName, 
            String userSlug, String token) {
        this.baseUrl = baseUrl;
        this.alias = alias;
        this.projectKey = projectKey;
        this.projectName = projectName;
        this.userSlug = userSlug;
        this.token = token;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getAlias() {
        return alias;
    }

    public String getProjectKey() {
        return projectKey;
    }

    public String getProjectName() {
        return projectName;
    }

    public String getUserSlug() {
        return userSlug;
    }

    public String getToken() {
        return token;
    }

    public Map<String, Object> asMap() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("url", baseUrl);
        result.put("alias", alias);
        result.put("project_key", projectKey);
        result.put("project_name", projectName);
        result.put("default_user", userSlug);
        result.put("default_token", token);
        return result;
    }
}
