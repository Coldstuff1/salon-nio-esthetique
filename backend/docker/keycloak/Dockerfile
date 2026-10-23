# Production Keycloak: optimized build for Postgres with the nio theme and the production realm baked in.
# Dev runs the stock image in start-dev mode instead (backend/compose.yaml).
FROM quay.io/keycloak/keycloak:26.8.1 AS build
ENV KC_DB=postgres \
    KC_HEALTH_ENABLED=true
COPY themes/nio /opt/keycloak/themes/nio
RUN /opt/keycloak/bin/kc.sh build

FROM quay.io/keycloak/keycloak:26.8.1
COPY --from=build /opt/keycloak/ /opt/keycloak/
# imported on first start only; afterwards the realm lives in the database
COPY realm-nio.json /opt/keycloak/data/import/realm-nio.json
ENTRYPOINT ["/opt/keycloak/bin/kc.sh"]
CMD ["start", "--optimized", "--import-realm"]
