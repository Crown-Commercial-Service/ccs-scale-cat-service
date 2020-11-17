#!/bin/bash
set -meo pipefail

pwd
ls -altr
cf --version
cf api https://api.london.cloud.service.gov.uk
cf login -u $CLOUDFOUNDRY_USERNAME -p $CLOUDFOUNDRY_PASSWORD -o ccs-scale-cat -s $CF_SPACE
cf push --var route-prefix=$CF_ROUTE_PREFIX --var ram=$CF_RAM