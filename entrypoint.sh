set -ex
JAVA_OPTS="-server -Xconcurrentio -Xmx4g -Xms4g -XX:+UseG1GC"

scripts/container/create-cache

java $JAVA_OPTS \
  -Ddw.server.applicationConnectors[0].bindHost=0.0.0.0 \
  -Ddw.server.applicationConnectors[0].port=80 \
  -Ddw.graphhopper.graph.location=graph-cache \
  -Ddw.graphhopper.datareader.file=prince-edward-island-latest.osm.pbf \
  -jar web/target/graphhopper-web-1.0-SNAPSHOT.jar \
  server \
  scripts/container/config.yml
