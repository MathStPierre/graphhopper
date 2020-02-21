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

package com.graphhopper.reader.gtfs;

import java.awt.Color;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;

import java.lang.Integer;

import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.graphhopper.PathWrapper;
import com.graphhopper.util.Helper;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Text;

import org.osgeo.proj4j.CRSFactory;
import org.osgeo.proj4j.CoordinateReferenceSystem;
import org.osgeo.proj4j.CoordinateTransform;
import org.osgeo.proj4j.CoordinateTransformFactory;
import org.osgeo.proj4j.ProjCoordinate;


class ProjFuncs {

    static CRSFactory mCsFactory = new CRSFactory();
    static CoordinateTransformFactory mCtFactory = new CoordinateTransformFactory();

    static String EPSG_WGS84 = "EPSG:4326";
    static String EPSG_GOOGLE_EARTH = "EPSG:3857";

    static CoordinateReferenceSystem crs1 = mCsFactory.createFromName(EPSG_WGS84);
    static CoordinateReferenceSystem crs2 = mCsFactory.createFromName(EPSG_GOOGLE_EARTH);
    static CoordinateTransform trans = mCtFactory.createTransform(crs1, crs2);

    public static ProjCoordinate latlonToGoogleEarthGcs(double lng, double lat) {
        ProjCoordinate p1 = new ProjCoordinate();
        ProjCoordinate p2 = new ProjCoordinate();
        p1.x = lng;
        p1.y = lat;
        trans.transform(p1, p2);
        return p2;
    }
}


public class GtfsGraphLogger {

    public enum NodeLogType {
        OSM_NODE, ENTER_EXIT_PT, BOARD_NODE, ARRIVAL_STOP_TIME_NODE, DEPARTURE_STOP_TIME_NODE, ALIGHT_NODE, BLOCK_TRANSFER_NODE, WAIT_NODE
    }

    public enum FindNodesStep {
        BACKWARD_SEARCH, FORWARD_SEARCH, RESULT
    }

    private boolean outputCsv = true;
    BufferedWriter csvReportWriter = null;

    class NodeInfo {

        NodeInfo(String nodeText, NodeLogType type, double lon, double lat, double xPos, double yPos, boolean expanded, FindNodesStep step, int exploredSequence) {
            this.nodeText = nodeText;
            this.type = type;
            this.xPos = xPos;
            this.yPos = yPos;
            this.lon = lon;
            this.lat = lat;
            this.expanded = expanded;
            this.findNodesStep = step;
            this.exploredSequence = exploredSequence;
        }

        public NodeLogType type;
        public String nodeText;
        public double xPos;
        public double yPos;
        public double lat;
        public double lon;
        public boolean expanded;
        public FindNodesStep findNodesStep;
        public int exploredSequence;

    }

    class EdgeInfo {

        EdgeInfo(String edgeType, String id, String srcNodeId, String targetNodeId, int exploredSequence) {
            this.edgeType = edgeType;
            this.id = id;
            this.srcNodeId = srcNodeId;
            this.targetNodeId = targetNodeId;
            this.exploredSequence = exploredSequence;
        }

        public String edgeType;
        public String id;
        public String srcNodeId;
        public String targetNodeId;
        public int exploredSequence;
    }

    private final Map<FindNodesStep, Map<String, NodeInfo>> insertedNodes = new HashMap<>();
    private final Map<FindNodesStep, Map<String, EdgeInfo>> insertedEdges = new HashMap<>();
    private int currentTripIndex = 0;
    private Color currentTripColor = null;
    private static Color OSM_NODE_COLOR = new Color(0,0,0);
    private static Color STOP_NODE_COLOR = new Color(200,0,0);
    private static Color NODE_TEXT_COLOR = new Color(255,255, 255);

    private int OSM_NODE_Y_POS = -20;
    private int TRIP_HEIGHT_SPACE = 70;
    private int BOARD_NODE_Y_DISTANCE_FROM_BASE = 10;
    private int TIME_NODE_Y_DISTANCE_FROM_BASE = 40;
    private int ALIGH_NODE_Y_DISTANCE_FROM_BASE = 50;
    private int BLOCK_TRANSFER_NODE_Y_DISTANCE_FROM_BASE = 60;

    private int BOARD_NODE_X_DISTANCE_FROM_CURRENT = 10;
    private int TIME_NODE_X_DISTANCE_FROM_CURRENT = 10;
    private int DEPARTURE_TIME_NODE_X_DISTANCE_INCREMENT = 150;
    private int ARRIVAL_TIME_NODE_X_DISTANCE_INCREMENT = 150;
    private int ALIGHT_NODE_X_DISTANCE_FROM_BASE = 10;

    private int currentXPos = 0;

    private Element appendXmlNode(final Document dom, final Element parentEle, final String nodeName, final String attributes) {
        Element keyEle = dom.createElement(nodeName);
        final String[] attributeList = attributes.split(" ");
        for (String attr : attributeList) {
            String[] attVal = attr.split("=");
            if (attVal.length > 1) {
                keyEle.setAttribute(attVal[0], attVal[1]);
            }
        }

        if (parentEle != null) {
            parentEle.appendChild(keyEle);
        }

        return keyEle;
    }

    public GtfsGraphLogger() throws ParserConfigurationException {

        findNextTripColor();
        resetLogger();
    }

    private void writeToCsv(String text) {

//        if (!outputCsv){
//            return;
//        }
//
//        if (csvReportWriter == null) {
//
//            try {
//                csvReportWriter = new BufferedWriter(new FileWriter(graphmlPath + ".csv"));
//                csvReportWriter.write("NodeId,Lng,Lat\n");
//            }
//            catch (IOException e) {
//                e.printStackTrace();
//            }
//        }
//
//        try {
//            csvReportWriter.write(text);
//        }
//        catch (IOException e) {
//            e.printStackTrace();
//        }
    }

    private void resetLogger() throws ParserConfigurationException {

        try {
            String val = System.getenv("GTFS_GRAPH_LOGGER_Y_SPACE_SCALE");

            if (val != null) {
                int spaceYScaleFactor = Integer.parseInt(val);

                OSM_NODE_Y_POS *= spaceYScaleFactor;
                TRIP_HEIGHT_SPACE *= spaceYScaleFactor;
                BOARD_NODE_Y_DISTANCE_FROM_BASE *= spaceYScaleFactor;
                TIME_NODE_Y_DISTANCE_FROM_BASE *= spaceYScaleFactor;
                ALIGH_NODE_Y_DISTANCE_FROM_BASE *= spaceYScaleFactor;
                BLOCK_TRANSFER_NODE_Y_DISTANCE_FROM_BASE *= spaceYScaleFactor;
            }
        }
        catch (Exception e) {
        }

        try {
            String val = System.getenv("GTFS_GRAPH_LOGGER_X_SPACE_SCALE");

            if (val != null) {
                int spaceYScaleFactor = Integer.parseInt(val);

                BOARD_NODE_X_DISTANCE_FROM_CURRENT *= spaceYScaleFactor;
                TIME_NODE_X_DISTANCE_FROM_CURRENT *= spaceYScaleFactor;
                DEPARTURE_TIME_NODE_X_DISTANCE_INCREMENT *= spaceYScaleFactor;
                ARRIVAL_TIME_NODE_X_DISTANCE_INCREMENT *= spaceYScaleFactor;
                ALIGHT_NODE_X_DISTANCE_FROM_BASE *= spaceYScaleFactor;
            }
        }
        catch (Exception e) {
        }

        insertedNodes.clear();
        insertedEdges.clear();
    }

    private double getXPos(NodeLogType type) {

        switch (type) {
            case OSM_NODE: return currentXPos;
            case ENTER_EXIT_PT: return currentXPos;
            case BOARD_NODE: return currentXPos + BOARD_NODE_X_DISTANCE_FROM_CURRENT;
            case ARRIVAL_STOP_TIME_NODE: {
                double x = currentXPos + TIME_NODE_X_DISTANCE_FROM_CURRENT;
                currentXPos += DEPARTURE_TIME_NODE_X_DISTANCE_INCREMENT;
                return x;
            }
            case DEPARTURE_STOP_TIME_NODE: {
                double x = currentXPos + TIME_NODE_X_DISTANCE_FROM_CURRENT;
                currentXPos += ARRIVAL_TIME_NODE_X_DISTANCE_INCREMENT;
                return x;
            }
            case ALIGHT_NODE:
                return currentXPos + ALIGHT_NODE_X_DISTANCE_FROM_BASE;
        }

        return currentXPos;
    }

    private double getYPos(NodeLogType type) {

        int yBasePos = currentTripIndex * TRIP_HEIGHT_SPACE;

        switch (type) {
            case OSM_NODE: return OSM_NODE_Y_POS;
            case ENTER_EXIT_PT: return yBasePos;
            case BOARD_NODE: return yBasePos + BOARD_NODE_Y_DISTANCE_FROM_BASE;
            case ARRIVAL_STOP_TIME_NODE:
            case DEPARTURE_STOP_TIME_NODE:
                return yBasePos + TIME_NODE_Y_DISTANCE_FROM_BASE;
            case ALIGHT_NODE: return yBasePos + ALIGH_NODE_Y_DISTANCE_FROM_BASE;
            case BLOCK_TRANSFER_NODE: return yBasePos + BLOCK_TRANSFER_NODE_Y_DISTANCE_FROM_BASE;
            default :
        }

        return yBasePos;
    }

    private boolean areColorsSimilar(Color a, Color b, int threshold) {
        return (Math.abs(a.getRed() - b.getRed()) + Math.abs(a.getGreen() - b.getGreen()) + Math.abs(a.getBlue() - b.getBlue())) < threshold;
    }

    private void findNextTripColor() {
        do {
            currentTripColor = new Color((int) (Math.random() * 0x1000000));
        } while (areColorsSimilar(currentTripColor, OSM_NODE_COLOR, 30) || areColorsSimilar(currentTripColor, STOP_NODE_COLOR, 30)
                || areColorsSimilar(currentTripColor, NODE_TEXT_COLOR, 250));
    }

    public void incrementTrip() {
        currentTripIndex++;
        findNextTripColor();
    }

    public void addNode(int id, double x, double y, NodeLogType type, String nodeText, boolean expanded, FindNodesStep step) {
        addNode(String.valueOf(id), x, y, type, nodeText, expanded, step);
    }

    public void addNode(String id, double x, double y, NodeLogType type, String nodeText, boolean expanded, FindNodesStep step) {

        Map<String, NodeInfo> nodesMap = insertedNodes.get(step);

        //Avoid creating duplicate nodes.
        if (nodesMap != null && nodesMap.containsKey(id)) {
            nodesMap.get(id).expanded |= expanded;
            return;
        }

        writeToCsv(id + "," + x + "," + y + "\n");

        ProjCoordinate coord = ProjFuncs.latlonToGoogleEarthGcs(x, y);

//       int xPos = getXPos(type);
//        int yPos = getYPos(type);
        double xPos = coord.x;
        double yPos = coord.y;

        if (nodesMap == null) {
            insertedNodes.put(step, new HashMap<>());
            nodesMap = insertedNodes.get(step);
        }

        nodesMap.put(id, new NodeInfo(nodeText, type, x, y, xPos, yPos, expanded, step, nodesMap.size()));
    }

    public ObjectNode appendNodes(ObjectNode geoJson) {

        ObjectNode jsonExploredColl = geoJson.putObject("exploredNodes");
        jsonExploredColl.put("type", "FeatureCollection");
        ArrayNode jsonExploredNodes = jsonExploredColl.putArray("features");

        insertedNodes.forEach( (s, v) -> v.forEach((id, nInfo) -> {
            ObjectNode feature = jsonExploredNodes.addObject();
            feature.put("type", "Feature");
            ObjectNode geometry = feature.putObject("geometry");
            geometry.put("type", "Point");
            ArrayNode coords = geometry.putArray("coordinates");
            coords.add(nInfo.lon).add(nInfo.lat);

            ObjectNode props = feature.putObject("properties");
            props.put("id", id);
            props.put("type", nInfo.type.toString());
            props.put("text", nInfo.nodeText);
            props.put("step", nInfo.findNodesStep.toString());
            props.put("expanded", nInfo.expanded);
            props.put("exploreSequence", nInfo.exploredSequence);
        }));

        return geoJson;
    }

    public void addXmlNode(Element graphEle, Document dom, String id, double x, double y, NodeLogType type, String nodeText, FindNodesStep step) {

        Element nodeEle = appendXmlNode(dom, graphEle, "node", "id=" + id);
        appendXmlNode(dom, graphEle, "data", "key=d0");
        Element dataNodeEle = appendXmlNode(dom, nodeEle, "data", "key=d6");
        Element shapeNodeEle = appendXmlNode(dom, dataNodeEle, "y:ShapeNode", "");
        appendXmlNode(dom, shapeNodeEle, "y:Geometry", "key=d6 height=40.0 width=75.0 x=" + String.valueOf(x) + " y=" + String.valueOf(y));

        String fillEleAttrs = "transparent=false color=";

        switch (type) {
            case OSM_NODE:
                fillEleAttrs += String.format("#%02x%02x%02x", OSM_NODE_COLOR.getRed(), OSM_NODE_COLOR.getGreen(), OSM_NODE_COLOR.getBlue()); //Black
                break;
            case ENTER_EXIT_PT:
                fillEleAttrs += String.format("#%02x%02x%02x", STOP_NODE_COLOR.getRed(), STOP_NODE_COLOR.getGreen(), STOP_NODE_COLOR.getBlue()); //Red
                break;
            default:
                fillEleAttrs += String.format("#%02x%02x%02x", currentTripColor.getRed(), currentTripColor.getGreen(), currentTripColor.getBlue());
                break;
        }

        appendXmlNode(dom, shapeNodeEle, "y:Fill", fillEleAttrs);
        appendXmlNode(dom, shapeNodeEle, "y:BorderStyle", "color=#000000 type=line width=1.0");

        Element nodeLabelEle = appendXmlNode(dom, shapeNodeEle, "y:NodeLabel", "alignment=center autoSizePolicy=content fontFamily=Dialog fontSize=16 fontStyle=plain hasBackgroundColor=false " +
                "hasLineColor=false hasText=true height=4.0 modelName=custom textColor=" +
                String.format("#%02x%02x%02x", NODE_TEXT_COLOR.getRed(), NODE_TEXT_COLOR.getGreen(), NODE_TEXT_COLOR.getBlue()) +
                " visible=true width=4.0 x=13.0 y=13.0");

        if (!nodeText.isEmpty()) {
            Text textNode = dom.createTextNode(nodeText);
            nodeLabelEle.appendChild(textNode);
        }

        Element labelModelEle = appendXmlNode(dom, nodeLabelEle, "y:LabelModel", "");
        appendXmlNode(dom, labelModelEle, "y:SmartNodeLabelModel", "distance=4.0");

        Element modelParamEle = appendXmlNode(dom, nodeLabelEle, "y:ModelParameter", "");
        appendXmlNode(dom, modelParamEle, "y:SmartNodeLabelModelParameter", "labelRatioX=0.0 labelRatioY=0.0 nodeRatioX=0.0 nodeRatioY=0.0 offsetX=0.0 offsetY=0.0 upX=0.0 upY=-1.0");
        appendXmlNode(dom, shapeNodeEle, "y:Shape", "type=ellipse");
    }

    public void addEdge(String edgeType, int id, int srcNodeId, int targetNodeId, FindNodesStep step) {
        addEdge(edgeType, String.valueOf(id), String.valueOf(srcNodeId), String.valueOf(targetNodeId), step);
    }

    public void addEdge(String edgeType, String id, String srcNodeId, String targetNodeId, FindNodesStep step) {

        Map<String, EdgeInfo> edgesMap = insertedEdges.get(step);

        //Avoid creating duplicate nodes.
        if (edgesMap != null && edgesMap.containsKey(id)) {
            return;
        }

        if (edgesMap == null) {
            insertedEdges.put(step, new HashMap<>());
            edgesMap = insertedEdges.get(step);
        }

        edgesMap.put(id, new EdgeInfo(edgeType, id, srcNodeId, targetNodeId, edgesMap.size()));
    }

    public void addXmlEdge(Element graphEle, Document dom, String edgeType, String id, String srcNodeId, String targetNodeId) {

        Element edgeEle = appendXmlNode(dom, graphEle, "edge", "id=" + id + " source=" + srcNodeId + " target=" + targetNodeId);
        Element dataEle = appendXmlNode(dom, edgeEle, "data", "key=d10");
        Element polyEdgeEle = appendXmlNode(dom, dataEle, "y:PolyLineEdge", "");

        appendXmlNode(dom, polyEdgeEle, "y:Path", "sx=0.0 sy=0.0 tx=0.0 ty=0.0");
        appendXmlNode(dom, polyEdgeEle, "y:LineStyle", "color=#000000 type=line width=1.0");
        appendXmlNode(dom, polyEdgeEle, "y:Arrows", "source=none target=standard");
        Element edgeLabelEle = appendXmlNode(dom, polyEdgeEle, "y:EdgeLabel", "alignment=center anchorX=27.526667606424326 anchorY=50.05534221010657 configuration=AutoFlippingLabel distance=2.0 fontFamily=Dialog fontSize=12 fontStyle=plain hasBackgroundColor=false hasLineColor=false height=18.1328125 modelName=custom preferredPlacement=anywhere ratio=0.5 textColor=#000000 upX=0.30976697067661274 upY=-0.9508125072157152 visible=true width=28.7734375 x=27.526667606424326 y=32.81443729410911");

        Text textNode = dom.createTextNode(edgeType + " " + id);
        edgeLabelEle.appendChild(textNode);

        Element labelModelEle = appendXmlNode(dom, edgeLabelEle, "y:LabelModel", "");
        appendXmlNode(dom, labelModelEle, "y:SmartEdgeLabelModel", "autoRotationEnabled=true defaultAngle=0.0 defaultDistance=10.0");

        Element modelParamEle = appendXmlNode(dom, edgeLabelEle, "y:ModelParameter", "");
        appendXmlNode(dom, modelParamEle, "y:SmartEdgeLabelModelParameter", "angle=0.0 distance=30.0 distanceToCenter=true position=right ratio=0.5 segment=0");
        appendXmlNode(dom, edgeLabelEle, "y:PreferredPlacementDescriptor", "angle=0.0 angleOffsetOnRightSide=0 angleReference=absolute angleRotationOnRightSide=co distance=-1.0 frozen=true placement=anywhere side=anywhere sideReference=relative_to_edge_flow");
        appendXmlNode(dom, polyEdgeEle, "y:BendStyle", "smoothed=false");

    }

    public void exportGraphmlToFile(String graphmlOutDir) throws ParserConfigurationException, IOException, TransformerException {

        for (FindNodesStep key : insertedNodes.keySet()) {
            Map<String, NodeInfo> nodesMap = insertedNodes.get(key);
            Map<String, EdgeInfo> edgesMap = insertedEdges.get(key);
            DocumentBuilderFactory dbf;
            DocumentBuilder db;
            Document dom;
            Element graphEle;

            dbf = DocumentBuilderFactory.newInstance();
            db = dbf.newDocumentBuilder();
            dom = db.newDocument();

            Element rootEle = appendXmlNode(dom, null, "graphml", "xmlns=http://graphml.graphdrawing.org/xmlns xmlns:java=http://www.yworks.com/xml/yfiles-common/1.0/java xmlns:sys=http://www.yworks.com/xml/yfiles-common/markup/primitives/2.0 xmlns:x=http://www.yworks.com/xml/yfiles-common/markup/2.0 xmlns:xsi=http://www.w3.org/2001/XMLSchema-instance xmlns:xsi=http://www.w3.org/2001/XMLSchema-instance xmlns:y=http://www.yworks.com/xml/graphml xmlns:yed=http://www.yworks.com/xml/yed/3 xsi:schemaLocation=http://graphml.graphdrawing.org/xmlns http://www.yworks.com/xml/schema/graphml/1.1/ygraphml.xsd");
            appendXmlNode(dom, rootEle, "key", "attr.name=Description attr.type=string for=graph id=d0");
            appendXmlNode(dom, rootEle, "key", "for=port id=d1 yfiles.type=portgraphics");
            appendXmlNode(dom, rootEle, "key", "for=port id=d2 yfiles.type=portgeometry");
            appendXmlNode(dom, rootEle, "key", "for=port id=d3 yfiles.type=portuserdata");
            appendXmlNode(dom, rootEle, "key", "attr.name=url attr.type=string for=node id=d4");
            appendXmlNode(dom, rootEle, "key", "attr.name=description attr.type=string for=node id=d5");
            appendXmlNode(dom, rootEle, "key", "for=node id=d6 yfiles.type=nodegraphics");
            appendXmlNode(dom, rootEle, "key", "for=graphml id=d7 yfiles.type=resources");
            appendXmlNode(dom, rootEle, "key", "attr.name=url attr.type=string for=edge id=d8");
            appendXmlNode(dom, rootEle, "key", "attr.name=description attr.type=string for=edge id=d9");
            appendXmlNode(dom, rootEle, "key", "for=edge id=d10 yfiles.type=edgegraphics");

            graphEle = dom.createElement("graph");
            graphEle.setAttribute("edgedefault", "directed");
            graphEle.setAttribute("id", "G");

            rootEle.appendChild(graphEle);

            dom.appendChild(rootEle);

            nodesMap.forEach((k, n) -> addXmlNode(graphEle, dom, k, n.xPos, n.yPos, n.type, n.nodeText, n.findNodesStep));
            Optional.ofNullable(edgesMap).ifPresent(m -> m.forEach((k, e) -> addXmlEdge(graphEle, dom, e.edgeType, k, e.srcNodeId, e.targetNodeId)));

            try {
                Transformer tr = TransformerFactory.newInstance().newTransformer();
                tr.setOutputProperty(OutputKeys.INDENT, "yes");
                tr.setOutputProperty(OutputKeys.METHOD, "xml");
                tr.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
                //tr.setOutputProperty(OutputKeys.DOCTYPE_SYSTEM, "roles.dtd");
                tr.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

                // send DOM to file
                tr.transform(new DOMSource(dom), new StreamResult(new FileOutputStream(graphmlOutDir + "/" + key.toString() + ".graphml")));
            } catch (IOException | TransformerException te) {
                System.out.println(te.getMessage());
            }
        }
    }
}
