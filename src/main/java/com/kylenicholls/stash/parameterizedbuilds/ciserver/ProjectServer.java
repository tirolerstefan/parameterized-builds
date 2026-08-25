package com.kylenicholls.stash.parameterizedbuilds.ciserver;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class ProjectServer extends CIServer{

    static final String PROJECT_KEY = "projectKey";

    private String projectKey;

    public ProjectServer(String projectKey){
        this.projectKey = projectKey;
        this.JENKINS_SETTINGS = "jenkins.admin.settingsProjectAdmin";
        this.ADDITIONAL_JS = "jenkins-settings-form";
    }

    public Map<String, Object> renderMap(Map<String, Object> renderOptions){
        @SuppressWarnings("serial")
        Map<String, Object> baseMap = new HashMap<String, Object>() {{
            put(PROJECT_KEY, projectKey);
            putAll(renderOptions);
        }};
        return Collections.unmodifiableMap(new LinkedHashMap<>(baseMap));
    }
}
