package com.kylenicholls.stash.parameterizedbuilds.item;

public class UserToken {
    private String baseUrl;
    private String alias;
    private String projectKey;
    private String projectName;
    private String userSlug;
    private String token;

    public UserToken(
            String baseUrl,
            String alias,
            String projectKey,
            String projectName,
            String userSlug,
            String token) {
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

    public String toJson() {
        return "{"
                + "\"url\":" + jsonString(baseUrl) + ","
                + "\"alias\":" + jsonString(alias) + ","
                + "\"project_key\":" + jsonString(projectKey) + ","
                + "\"project_name\":" + jsonString(projectName) + ","
                + "\"default_user\":" + jsonString(userSlug) + ","
                + "\"default_token\":" + jsonString(token)
                + "}";
    }

    private static String jsonString(String value) {
        if (value == null) {
            return "null";
        }

        StringBuilder result = new StringBuilder("\"");

        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);

            switch (character) {
                case '"':
                    result.append("\\\"");
                    break;
                case '\\':
                    result.append("\\\\");
                    break;
                case '\b':
                    result.append("\\b");
                    break;
                case '\f':
                    result.append("\\f");
                    break;
                case '\n':
                    result.append("\\n");
                    break;
                case '\r':
                    result.append("\\r");
                    break;
                case '\t':
                    result.append("\\t");
                    break;
                default:
                    if (character < 0x20) {
                        result.append(String.format(
                                "\\u%04x",
                                (int) character));
                    } else {
                        result.append(character);
                    }
            }
        }

        return result.append('"').toString();
    }
}