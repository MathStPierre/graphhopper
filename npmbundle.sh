set -ex
currentpath=`pwd`
echo $currentpath

if  [[ $currentpath == *checkout ]] ;
then
    cd $currentpath/web && npm install && npm run bundleProduction
fi