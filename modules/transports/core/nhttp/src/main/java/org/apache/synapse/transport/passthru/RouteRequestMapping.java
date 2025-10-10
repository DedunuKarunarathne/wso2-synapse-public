/**
 *  Copyright (c) 2025, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.synapse.transport.passthru;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.conn.routing.HttpRoute;

import java.util.Objects;

public class RouteRequestMapping {

    private static final Log log = LogFactory.getLog(RouteRequestMapping.class);

    private final HttpRoute route;

    private final String identifier;

    public RouteRequestMapping(HttpRoute route, String identifier) {
        if (log.isDebugEnabled()) {
            log.debug("Creating new host route: " + route);
        }
        this.route = route;
        this.identifier = identifier;
    }

    public HttpRoute getRoute() {
        return route;
    }

    public String getIdentifier() {
        return identifier;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        RouteRequestMapping that = (RouteRequestMapping) o;

        return Objects.equals(route, that.route) &&
                Objects.equals(identifier, that.identifier);
    }

    @Override
    public int hashCode() {
        return Objects.hash(route, identifier);
    }
}
