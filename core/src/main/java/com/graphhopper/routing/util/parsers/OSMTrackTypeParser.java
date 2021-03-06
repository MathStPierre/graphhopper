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

import static com.graphhopper.routing.profiles.TrackType.OTHER;

import java.util.List;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.profiles.EncodedValue;
import com.graphhopper.routing.profiles.EncodedValueLookup;
import com.graphhopper.routing.profiles.EnumEncodedValue;
import com.graphhopper.routing.profiles.TrackType;
import com.graphhopper.routing.util.EncodingManager.Access;
import com.graphhopper.storage.IntsRef;

public class OSMTrackTypeParser implements TagParser {

    private final EnumEncodedValue<TrackType> trackTypeEnc;

    public OSMTrackTypeParser() {
        this.trackTypeEnc = new EnumEncodedValue<>(TrackType.KEY, TrackType.class);
    }

    @Override
    public void createEncodedValues(EncodedValueLookup lookup, List<EncodedValue> link) {
        link.add(trackTypeEnc);
    }

    @Override
    public IntsRef handleWayTags(IntsRef edgeFlags, ReaderWay readerWay, Access access,
                    long relationFlags) {
        if (!access.isWay())
            return edgeFlags;

        String trackTypeTag = readerWay.getTag("tracktype");
        if (trackTypeTag == null)
            return edgeFlags;
        TrackType trackType = TrackType.find(trackTypeTag);
        if (trackType != OTHER)
            trackTypeEnc.setEnum(false, edgeFlags, trackType);
        return edgeFlags;
    }

}
