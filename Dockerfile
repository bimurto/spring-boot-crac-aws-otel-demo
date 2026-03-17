FROM bellsoft/liberica-runtime-container:jdk-21-crac-glibc as build
WORKDIR /app
COPY ./build/libs/demo.jar .
RUN java -Djarmode=layertools -jar demo.jar extract

FROM bellsoft/liberica-runtime-container:jdk-21-crac-glibc as runtime

ARG DRONE_TAG
ENV APP_VERSION ${DRONE_TAG}
ENV FLYWAY_VERSION 8.5.13
ENV APP_HOME /usr/src/app

WORKDIR $APP_HOME

# Installing dependencies
RUN apk update && apk add bash openssh aws-cli openssl curl
RUN mkdir -p $APP_HOME

COPY ./libs/timezone/tzupdater.jar $APP_HOME/tzupdater.jar
COPY ./libs/opentelemetry-grafana/grafana-opentelemetry-java-v2.5.0-beta.1.jar $APP_HOME/grafana-opentelemetry-java.jar
COPY ./scripts/migrate.sh $APP_HOME/migrate.sh
COPY scripts/crac/automatic_checkpoint_creation.sh $APP_HOME/automatic_checkpoint_creation.sh
COPY scripts/crac/from_checkpoint_api.sh $APP_HOME/from_checkpoint_api.sh
COPY scripts/crac/from_checkpoint_listener.sh $APP_HOME/from_checkpoint_listener.sh


RUN ["java", "-jar", "tzupdater.jar", "-v", "-f", "-l", "https://www.iana.org/time-zones/repository/tzdata-latest.tar.gz"]
RUN chmod +x $APP_HOME/migrate.sh
RUN chmod +x $APP_HOME/automatic_checkpoint_creation.sh
RUN chmod +x $APP_HOME/from_checkpoint_api.sh
RUN chmod +x $APP_HOME/from_checkpoint_listener.sh

ADD https://repo1.maven.org/maven2/org/flywaydb/flyway-commandline/${FLYWAY_VERSION}/flyway-commandline-${FLYWAY_VERSION}.zip flyway.zip
RUN unzip flyway.zip && rm flyway.zip && mv flyway-${FLYWAY_VERSION} /flyway && ln -s /flyway/flyway /usr/local/bin/flyway

EXPOSE 8080

COPY --from=build /app/dependencies/BOOT-INF/lib ./lib
COPY --from=build /app/spring-boot-loader/ ./
COPY --from=build /app/snapshot-dependencies/ ./
COPY --from=build /app/application/BOOT-INF/classes ./
COPY --from=build /app/application/META-INF ./META-INF

CMD java \
  ${ADDITIONAL_JAVA_OPTIONS} \
  -javaagent:$APP_HOME/grafana-opentelemetry-java.jar \
  -Djava.security.egd=file:/dev/./urandom \
  -cp $APP_HOME:$APP_HOME/lib/* \
  io.bimurto.crac.Application
