FROM bellsoft/liberica-runtime-container:jdk-21-crac-glibc as build
WORKDIR /app
COPY ./build/libs/demo.jar .
RUN java -Djarmode=layertools -jar demo.jar extract

FROM bellsoft/liberica-runtime-container:jdk-21-crac-glibc as runtime

ENV FLYWAY_VERSION 8.5.13
ENV APP_HOME /usr/src/app

WORKDIR $APP_HOME

# Installing dependencies
RUN apk update && apk add bash openssh aws-cli openssl curl
RUN mkdir -p $APP_HOME


COPY scripts/crac/automatic_checkpoint_creation.sh $APP_HOME/automatic_checkpoint_creation.sh
COPY scripts/crac/from_checkpoint_app.sh $APP_HOME/from_checkpoint_app.sh

RUN chmod +x $APP_HOME/automatic_checkpoint_creation.sh
RUN chmod +x $APP_HOME/from_checkpoint_app.sh

EXPOSE 8080

COPY --from=build /app/dependencies/BOOT-INF/lib ./lib
COPY --from=build /app/spring-boot-loader/ ./
COPY --from=build /app/snapshot-dependencies/ ./
COPY --from=build /app/application/BOOT-INF/classes ./
COPY --from=build /app/application/META-INF ./META-INF

CMD java \
  ${ADDITIONAL_JAVA_OPTIONS} \
  -Djava.security.egd=file:/dev/./urandom \
  -cp $APP_HOME:$APP_HOME/lib/* \
  io.bimurto.crac.Application
