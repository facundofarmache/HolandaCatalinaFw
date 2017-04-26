package org.hcjf.io.net.http.view;

import org.hcjf.io.net.http.HttpRequest;
import org.hcjf.io.net.http.layered.LayeredRequest;

import java.util.Map;

/**
 * @author Andrés Medina
 * @email armedina@gmail.com
 */
public class ViewRequest extends LayeredRequest {

    private final String action;
    private final String resourceName;
    private final String encodingImplementation;
    private final Map<String, Object> viewParameters;

    public ViewRequest(HttpRequest request, String action, String resourceName, String encodingImplementation, Map<String, Object> viewParameters) {
        super(request);
        this.action = action;
        this.resourceName = resourceName;
        this.encodingImplementation = encodingImplementation;
        this.viewParameters = viewParameters;
    }

    public String getAction() {
        return action;
    }

    public String getResourceName() {
        return resourceName;
    }

    public String getEncodingImplementation() {
        return encodingImplementation;
    }

    public Map<String, Object> getViewParameters() {
        return viewParameters;
    }
}