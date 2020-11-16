#!/bin/bash

./cf api https://api.london.cloud.service.gov.uk
./cf login -u $CLOUDFOUNDRY_USERNAME -p $CLOUDFOUNDRY_PASSWORD -o ccs-scale-cat -s $CF_SPACE
./cf push --var route-prefix=$CF_ROUTE_PREFIX --var ram=$CF_RAM