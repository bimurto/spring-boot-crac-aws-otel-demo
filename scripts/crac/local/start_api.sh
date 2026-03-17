sudo rm -rf build/
./gradlew build -x test || exit 1

cd build/libs/ || exit 1
java -Djarmode=layertools -jar demo.jar extract || exit 1

rm -rf APP_HOME && mkdir APP_HOME
cp -r dependencies/BOOT-INF/lib APP_HOME/lib
cp -r spring-boot-loader APP_HOME/
cp -r snapshot-dependencies APP_HOME/
cp -r application/BOOT-INF/classes/* APP_HOME/
cp -r application/META-INF APP_HOME/META-INF

rm -rf crac && mkdir -p crac/checkpoint
sudo cp -r APP_HOME/* crac/
sudo cp ../../scripts/crac/automatic_checkpoint_creation.sh crac/

docker run -p 8080:8080 \
  --rm --privileged \
  -v "$(pwd)"/crac:/crac/ \
  -w /crac \
  -e AWS_REGION=eu-west-1 \
  -e AWS_ACCESS_KEY_ID=id \
  -e AWS_SECRET_ACCESS_KEY=key \
  --network host \
  --name demo \
  bellsoft/liberica-runtime-container:jdk-21-crac-slim-glibc \
  java -Xmx512m \
  -XX:CRaCCheckpointTo=/crac/checkpoint/api \
  -Dspring.profiles.include="crac" \
  -cp "../crac:../crac/lib/*" io.bimurto.crac.Application