/*
 *  Copyright (c) 2021, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.synapse.api;

import org.apache.axis2.Constants;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpStatus;
import org.apache.synapse.MessageContext;
import org.apache.synapse.SynapseConstants;
import org.apache.synapse.SynapseException;
import org.apache.synapse.api.version.ContextVersionStrategy;
import org.apache.synapse.api.version.DefaultStrategy;
import org.apache.synapse.api.version.URLBasedVersionStrategy;
import org.apache.synapse.core.axis2.Axis2MessageContext;
import org.apache.synapse.rest.RESTConstants;
import org.apache.synapse.api.dispatch.DefaultDispatcher;
import org.apache.synapse.api.dispatch.RESTDispatcher;
import org.apache.synapse.api.dispatch.URITemplateBasedDispatcher;
import org.apache.synapse.api.dispatch.URLMappingBasedDispatcher;
import org.apache.synapse.transport.nhttp.NhttpConstants;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ApiUtils {

    private static final Log log = LogFactory.getLog(ApiUtils.class);

    private static final List<RESTDispatcher> dispatchers = new ArrayList<RESTDispatcher>();

    static {
        dispatchers.add(new URLMappingBasedDispatcher());
        dispatchers.add(new URITemplateBasedDispatcher());
        dispatchers.add(new DefaultDispatcher());
    }

    public static String trimSlashes(String url) {
        if (url.startsWith("/")) {
            url = url.substring(1);
        }
        if (url.startsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    public static String trimTrailingSlashes(String url) {
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    public static String getFullRequestPath(MessageContext synCtx) {
        Object obj = synCtx.getProperty(RESTConstants.REST_FULL_REQUEST_PATH);
        if (obj != null) {
            return (String) obj;
        }
        org.apache.axis2.context.MessageContext msgCtx = ((Axis2MessageContext) synCtx).
                getAxis2MessageContext();
        String url = (String) msgCtx.getProperty(Constants.Configuration.TRANSPORT_IN_URL);
        if (url == null) {
            url = (String) synCtx.getProperty(NhttpConstants.SERVICE_PREFIX);
        }

        if (url.startsWith("http://") || url.startsWith("https://")) {
            try {
                url = new URL(url).getFile();
            } catch (MalformedURLException e) {
                handleException("Request URL: " + url + " is malformed", e);
            }
        } else if (url.startsWith("ws://") || url.startsWith("wss://")) {
            try {
                URI uri = new URI(url);
                String query = uri.getQuery();
                if (query == null) {
                    url = uri.getPath();
                } else {
                    url = uri.getPath() + "?" + query;
                }
            } catch (URISyntaxException e) {
                handleException("Request URL: " + url + " is malformed", e);
            }
        }
        synCtx.setProperty(RESTConstants.REST_FULL_REQUEST_PATH, url);
        return url;
    }

    /**
     * Populate Message context properties for the query parameters extracted from the url
     *
     * @param synCtx MessageContext of the request
     */
    public static void populateQueryParamsToMessageContext(MessageContext synCtx) {

        String path = getFullRequestPath(synCtx);
        String method = (String) synCtx.getProperty(RESTConstants.REST_METHOD);

        int queryIndex = path.indexOf('?');
        if (queryIndex != -1) {
            String query = path.substring(queryIndex + 1);
            String[] entries = query.split(RESTConstants.QUERY_PARAM_DELIMITER);
            String name = null;
            String value;
            for (String entry : entries) {
                int index = entry.indexOf('=');
                if (index != -1) {
                    try {
                        name = entry.substring(0, index);
                        value = URLDecoder.decode(entry.substring(index + 1),
                                RESTConstants.DEFAULT_ENCODING);
                        synCtx.setProperty(RESTConstants.REST_QUERY_PARAM_PREFIX + name, value);
                    } catch (UnsupportedEncodingException uee) {
                        handleException("Error processing " + method + " request for : " + path, uee);
                    } catch (IllegalArgumentException e) {
                        String errorMessage = "Error processing " + method + " request for : " + path
                                + " due to an error in the request sent by the client";
                        synCtx.setProperty(SynapseConstants.ERROR_CODE, HttpStatus.SC_BAD_REQUEST);
                        synCtx.setProperty(SynapseConstants.ERROR_MESSAGE, errorMessage);
                        org.apache.axis2.context.MessageContext inAxisMsgCtx =
                                ((Axis2MessageContext) synCtx).getAxis2MessageContext();
                        inAxisMsgCtx.setProperty(SynapseConstants.HTTP_SC, HttpStatus.SC_BAD_REQUEST);
                        handleException(errorMessage, e);
                    }
                } else {
                    // If '=' sign isn't present in the entry means that the '&' character is part of
                    // the query parameter value. If so query parameter value should be updated appending
                    // the remaining characters.
                    String existingValue = (String) synCtx.getProperty(RESTConstants.REST_QUERY_PARAM_PREFIX + name);
                    value = RESTConstants.QUERY_PARAM_DELIMITER + entry;
                    synCtx.setProperty(RESTConstants.REST_QUERY_PARAM_PREFIX + name, existingValue + value);
                }
            }
        }
    }

    public static String getSubRequestPath(MessageContext synCtx) {
        return (String) synCtx.getProperty(RESTConstants.REST_SUB_REQUEST_PATH);
    }

    /**
     * Checks whether the provided resource is capable of processing the message from the provided message context.
     * The resource becomes capable to do this when the it contains either the name of the api caller,
     * or {@value ApiConstants#DEFAULT_BINDING_ENDPOINT_NAME}, in its binds-to.
     *
     * @param resource  Resource object
     * @param synCtx    MessageContext object
     * @return          Whether the provided resource is bound to the provided message context
     */
    public static boolean isBound(Resource resource, MessageContext synCtx) {
        Collection<String> bindings = resource.getBindsTo();
        Object apiCaller = synCtx.getProperty(ApiConstants.API_CALLER);
        if (apiCaller != null) {
            return bindings.contains(apiCaller.toString());
        }
        return bindings.contains(ApiConstants.DEFAULT_BINDING_ENDPOINT_NAME);
    }

    public static Set<Resource> getAcceptableResources(Map<String, Resource> resources, MessageContext synCtx) {
        List<Resource> acceptableResourcesList = new LinkedList<>();
        for (Resource r : resources.values()) {
            if (isBound(r, synCtx) && r.canProcess(synCtx)) {
                if (Arrays.asList(r.getMethods()).contains(RESTConstants.METHOD_OPTIONS)) {
                    acceptableResourcesList.add(0, r);
                } else {
                    acceptableResourcesList.add(r);
                }
            }
        }
        return new LinkedHashSet<Resource>(acceptableResourcesList);
    }

    /**
     * This method is used to locate the specific API that has been invoked from the collection of all APIs.
     *
     * @param synCtx MessageContext of the request
     * @return Selected API
     */
    public static API getSelectedAPI(MessageContext synCtx) {
        //getting the API collection from the synapse configuration to find the invoked API
        Collection<API> apiSet = synCtx.getEnvironment().getSynapseConfiguration().getAPIs();
        //Since swapping elements are not possible with sets, Collection is converted to a List
        List<API> defaultStrategyApiSet = new ArrayList<API>(apiSet);
        //To avoid apiSet being modified concurrently
        List<API> duplicateApiSet = new ArrayList<>(apiSet);
        //identify the api using canProcess method
        for (API api : duplicateApiSet) {
            if (identifySelectedAPI(api, synCtx, defaultStrategyApiSet)) {
                return api;
            }
        }
        for (API api : defaultStrategyApiSet) {
            api.setLogSetterValue();
            if (api.canProcess(synCtx)) {
                if (log.isDebugEnabled()) {
                    log.debug("Located specific API: " + api.getName() + " for processing message");
                }
                return api;
            }
        }
        return null;
    }

    private static boolean identifySelectedAPI(API api, MessageContext synCtx, List defaultStrategyApiSet) {
        API defaultAPI = null;
        api.setLogSetterValue();
        if ("/".equals(api.getContext())) {
            defaultAPI = api;
        } else if (api.getVersionStrategy().getClass().getName().equals(DefaultStrategy.class.getName())) {
            //APIs whose VersionStrategy is bound to an instance of DefaultStrategy, should be skipped and processed at
            // last.Otherwise they will be always chosen to process the request without matching the version.
            defaultStrategyApiSet.add(api);
        } else if (api.getVersionStrategy().getClass().getName().equals(ContextVersionStrategy.class.getName())
                || api.getVersionStrategy().getClass().getName().equals(URLBasedVersionStrategy.class.getName())) {
            api.setLogSetterValue();
            if (api.canProcess(synCtx)) {
                if (log.isDebugEnabled()) {
                    log.debug("Located specific API: " + api.getName() + " for processing message");
                }
                return true;
            }
        } else if (api.canProcess(synCtx)) {
            if (log.isDebugEnabled()) {
                log.debug("Located specific API: " + api.getName() + " for processing message");
            }
            return true;
        }
        return false;
    }

    public static List<RESTDispatcher> getDispatchers() {
        return dispatchers;
    }

    private static void handleException(String msg, Throwable t) {
        log.error(msg, t);
        throw new SynapseException(msg, t);
    }

    /**
     * Identify the API by matching the context of the invoking api
     * with the path of each api in the api list.
     *
     * @param path    request path
     * @param context API context
     * @return true if the invoking api context matches with the path
     * and false if the two values don't match
     */
    public static boolean matchApiPath(String path, String context) {
        if (!path.startsWith(context + "/") && !path.startsWith(context + "?")
                && !context.equals(path) && !"/".equals(context)) {
            return false;
        }
        return true;
    }
}