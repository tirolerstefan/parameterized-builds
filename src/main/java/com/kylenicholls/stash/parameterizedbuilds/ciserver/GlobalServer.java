package com.kylenicholls.stash.parameterizedbuilds.ciserver;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class GlobalServer extends CIServer{

    public GlobalServer(){
        this.JENKINS_SETTINGS = "jenkins.admin.settings";
        this.ADDITIONAL_JS = "jenkins-settings-form";
    }

    public Map<String, Object> renderMap(Map<String, Object> renderOptions){
        @SuppressWarnings("serial")
        Map<String, Object> baseMap = new HashMap<String, Object>() {{
            putAll(renderOptions);
        }};
        return Collections.unmodifiableMap(new LinkedHashMap<>(baseMap));
    }
}
