set -ex
JAVA_OPTS="-server -Xconcurrentio -Xmx4g -Xms4g -XX:+UseG1GC"

scripts/container/create-cache

java $JAVA_OPTS \
  -Ddw.server.applicationConnectors[0].bindHost=0.0.0.0 \
  -Ddw.server.applicationConnectors[0].port=80 \
  -Dgraphhopper.graph.location=graph-cache \
  -Dgraphhopper.datareader.file=kanto.osm.pbf \
  -Dgraphhopper.gtfs.file=rail.zip \
  -jar target/graphhopper-1.0-SNAPSHOT.jar \
  server \
  resources/config.yml
