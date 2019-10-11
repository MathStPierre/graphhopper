/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderRelation;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.profiles.EncodedValue;
import com.graphhopper.routing.profiles.EncodedValueLookup;
import com.graphhopper.routing.profiles.EnumEncodedValue;
import com.graphhopper.routing.profiles.RouteNetwork;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.IntsRef;

import java.util.List;

import static com.graphhopper.routing.util.EncodingManager.getKey;

public class OSMFootNetworkTagParser implements RelationTagParser {
    private EnumEncodedValue<RouteNetwork> footRouteEnc;
    // used only for internal transformation from relations into way flags -> priorityHikeRoute
    private EnumEncodedValue<RouteNetwork> transformerRouteRelEnc;

    @Override
    public void createRelationEncodedValues(EncodedValueLookup lookup, List<EncodedValue> registerNewEncodedValue) {
        registerNewEncodedValue.add(transformerRouteRelEnc = new EnumEncodedValue<>(getKey("foot", "route_relation"), RouteNetwork.class));
    }

    @Override
    public IntsRef handleRelationTags(IntsRef relFlags, ReaderRelation relation) {
        RouteNetwork footNetwork = RouteNetwork.OTHER;
        if (relation.hasTag("route", "hiking") || relation.hasTag("route", "foot")) {
            String tag = relation.getTag("network", "lwn").toLowerCase();
            if ("lwn".equals(tag)) {
                footNetwork = RouteNetwork.LOCAL;
            } else if ("rwn".equals(tag)) {
                footNetwork = RouteNetwork.REGIONAL;
            } else if ("nwn".equals(tag)) {
                footNetwork = RouteNetwork.NATIONAL;
            } else if ("iwn".equals(tag)) {
                footNetwork = RouteNetwork.INTERNATIONAL;
            }
        }

        transformerRouteRelEnc.setEnum(false, relFlags, footNetwork);
        return relFlags;
    }

    @Override
    public void createEncodedValues(EncodedValueLookup lookup, List<EncodedValue> registerNewEncodedValue) {
        registerNewEncodedValue.add(footRouteEnc = new EnumEncodedValue<>(getKey("foot", RouteNetwork.PART_NAME), RouteNetwork.class));
    }

    @Override
    public IntsRef handleWayTags(IntsRef edgeFlags, ReaderWay way, EncodingManager.Access access, IntsRef relationFlags) {
        // just copy value into different bit range
        RouteNetwork footNetwork = transformerRouteRelEnc.getEnum(false, relationFlags);
        footRouteEnc.setEnum(false, edgeFlags, footNetwork);
        return edgeFlags;
    }
}
