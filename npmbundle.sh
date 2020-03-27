set -ex
PATH=`pwd`
echo $PATH
if  [[ $PATH == *checkout ]] ;
then
    cd $PATH/web && npm install && npm run bundleProduction
fi