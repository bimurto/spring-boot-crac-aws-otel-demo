cd build/libs/

docker run -p 8080:8080 \
  --rm --privileged \
  -v "$(pwd)/crac:/crac/" \
  -w /crac \
  -e AWS_REGION=eu-west-1 \
  -e AWS_ACCESS_KEY_ID=id \
  -e AWS_SECRET_ACCESS_KEY=key \
  --network host \
  --name demo \
  bellsoft/liberica-runtime-container:jdk-21-crac-slim-glibc \
  java -Xmx512m -XX:CRaCRestoreFrom=/crac/checkpoint/api