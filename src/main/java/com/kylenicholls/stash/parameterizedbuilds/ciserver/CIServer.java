package com.kylenicholls.stash.parameterizedbuilds.ciserver;

import java.util.Collections;
import java.util.Map;

public abstract class CIServer {

    String JENKINS_SETTINGS;
    String ADDITIONAL_JS;

    public Map<String, Object> renderMap(){
        return renderMap(Collections.emptyMap());
    }

    public abstract Map<String, Object> renderMap(Map<String, Object> renderOptions);
}
