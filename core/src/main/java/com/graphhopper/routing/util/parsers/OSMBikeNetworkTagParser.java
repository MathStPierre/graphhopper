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

public class OSMBikeNetworkTagParser implements RelationTagParser {
    private EnumEncodedValue<RouteNetwork> bikeRouteEnc;
    // used only for internal transformation from relations into way flags -> priorityBikeRoute
    private EnumEncodedValue<RouteNetwork> transformerRouteRelEnc;

    @Override
    public void createRelationEncodedValues(EncodedValueLookup lookup, List<EncodedValue> registerNewEncodedValue) {
        registerNewEncodedValue.add(transformerRouteRelEnc = new EnumEncodedValue<>(getKey("bike", "route_relation"), RouteNetwork.class));
    }

    @Override
    public IntsRef handleRelationTags(IntsRef relFlags, ReaderRelation relation) {
        RouteNetwork bikeNetwork = RouteNetwork.OTHER;
        if (relation.hasTag("route", "bicycle")) {
            String tag = relation.getTag("network", "lcn").toLowerCase();
            if ("lcn".equals(tag)) {
                bikeNetwork = RouteNetwork.LOCAL;
            } else if ("rcn".equals(tag)) {
                bikeNetwork = RouteNetwork.REGIONAL;
            } else if ("ncn".equals(tag)) {
                bikeNetwork = RouteNetwork.NATIONAL;
            } else if ("icn".equals(tag)) {
                bikeNetwork = RouteNetwork.INTERNATIONAL;
            }
        }

        transformerRouteRelEnc.setEnum(false, relFlags, bikeNetwork);
        return relFlags;
    }

    @Override
    public void createEncodedValues(EncodedValueLookup lookup, List<EncodedValue> registerNewEncodedValue) {
        registerNewEncodedValue.add(bikeRouteEnc = new EnumEncodedValue<>(getKey("bike", RouteNetwork.PART_NAME), RouteNetwork.class));
    }

    @Override
    public IntsRef handleWayTags(IntsRef edgeFlags, ReaderWay way, EncodingManager.Access access, IntsRef relationFlags) {
        // just copy value into different bit range
        RouteNetwork routeNetwork = transformerRouteRelEnc.getEnum(false, relationFlags);
        bikeRouteEnc.setEnum(false, edgeFlags, routeNetwork);
        return edgeFlags;
    }
}
