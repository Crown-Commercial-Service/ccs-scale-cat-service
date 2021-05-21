#!/bin/bash
#
# Login to GPaaS London Cloud Foundry ccs-scale-cat org with space determined by environment variable CF_SPACE.
# Push local app based on manifest, setting vars `CF_APP_PREFIX` from env var `CF_APP_PREFIX` and `CF_MEMORY` from `CF_MEMORY` 
#

set -meo pipefail

cf --version
cf api https://api.london.cloud.service.gov.uk

cf login -u $CLOUDFOUNDRY_USERNAME -p $CLOUDFOUNDRY_PASSWORD -o ccs-scale-cat -s $CF_SPACE

APP_NAME="${CF_APP_PREFIX}-ccs-scale-cat-service"
SERVICE_NAME_PG="${CF_APP_PREFIX}-ccs-scale-cat-db"
UPS_NAME="${CF_APP_PREFIX}-ccs-scale-cat-ups-service"

# TODO: Grab disk, mem and instance count vars from travis / UPS?
cf push -k $CF_DISK -m $CF_MEMORY -i $CF_INSTANCES --no-start --var CF_APP_PREFIX=$CF_APP_PREFIX

# Map an internal route to CaT API backend for UI
# cf map-route $APP_NAME apps.internal --hostname $APP_NAME

#######################
# Bind to Services
#######################
cf bind-service $APP_NAME $SERVICE_NAME_PG

# UPS
cf bind-service $APP_NAME $UPS_NAME

##################################
# Set Environment Variables in App
##################################

# TODO: Improve the sed command to remove need to prefix with additional '{' char
VCAP_SERVICES="{$(cf env $APP_NAME | sed -n '/^VCAP_SERVICES:/,/^$/{//!p;}')"

echo "${VCAP_SERVICES}"

# CaT API
cf set-env $APP_NAME AGREEMENTS_SERVICE_API_KEY $(echo $VCAP_SERVICES | jq -r '."user-provided"[] | select(.name == env.UPS_NAME).credentials."agreements-svc-api-key"')
cf set-env $APP_NAME AGREEMENTS_SERVICE_URL $(echo $VCAP_SERVICES | jq -r '."user-provided"[] | select(.name == env.UPS_NAME).credentials."agreements-svc-url"')
cf set-env $APP_NAME "spring.security.oauth2.client.registration.jaggaer.client-id" $(echo $VCAP_SERVICES | jq -r '."user-provided"[] | select(.name == env.UPS_NAME).credentials."jaggaer-client-id"')
cf set-env $APP_NAME "spring.security.oauth2.client.registration.jaggaer.client-secret" $(echo $VCAP_SERVICES | jq -r '."user-provided"[] | select(.name == env.UPS_NAME).credentials."jaggaer-client-secret"')
cf set-env $APP_NAME "spring.security.oauth2.resourceserver.jwt.jwk-set-uri" $(echo $VCAP_SERVICES | jq -r '."user-provided"[] | select(.name == env.UPS_NAME).credentials."auth-server-jwk-set-uri"')

#######################
# Restage and start
#######################
cf restage $APP_NAME