set -ex
path=`pwd`
echo $path
cd $path/web && npm install && npm run bundleProduction