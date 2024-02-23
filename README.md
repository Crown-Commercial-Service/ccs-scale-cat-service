# CCS Scale CaT Service (API)

## Overview

This is the Java 11+ SpringBoot implementation of the CaT (Tenders) API [Open API specification](https://github.com/Crown-Commercial-Service/ccs-scale-api-definitions/blob/master/cat/CaT-service.yaml).

## Deployment

This application is hosted on [AWS](https://aws.amazon.com/) and we use [Jenkins](https://www.jenkins.io/) to deploy the code.

The environments are mapped as follows:

| Environment     | Branch          |
|-----------------|-----------------|
| Sandbox 4       | `release/sbx4`  |
| Development     | `develop`       |
| Integration     | `release/int`   |
| NFT             | `release/nft`   |
| UAT             | `release/uat`   |
| Pre-Production  | `release/pre`   |
| Production      | `release/prod`  |

When your code changes are merged you will need to deploy the code manually.

To do this you must run the job to build the docker image for the app.
Make sure you are targeting the right branch for the environment you wish to deploy to.

Once the image has been built, the job to deploy the code can be run which releases the code to the selected environment.
