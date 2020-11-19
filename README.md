# CCS Scale CaT Service (API)

## Overview
Java 11 / SpringBoot skeleton component for the CaT API. Exposes some very basic data in JSON format from the Scale shared Agreements API (currently deployed in AWS).

## Local
The service can be configured and run locally assuming external IPs are in the Agreements API allowed list.

### Configure (Eclipse / STS)

1. Clone this repo locally
2. Import existing Maven project
3. Open the (Spring) Boot Dashboard view and open the config for the project (right-click menu)
4. Under the Environment tab add 3 environment variables:
    - `AGREEMENTS_SERVICE_URL` (base URL including `/[env]/scale/agreements-service` path prefix)
    - `AGREEMENTS_SERVICE_API_KEY` (environment specific)
    - `ROLLBAR_ACCESS_TOKEN` (a valid access token for CCS organisation in Rollbar)

### Run (Eclipse / STS)
Once configured, start the service from the Boot Dashboard and make a GET request in Postman or another API client to http://localhost:8080/agreement-summaries

### Build (Maven)
Run `mvn clean package` from a shell to run the test suite and build the deployable JAR artifact into the target directory

## Cloud Foundry
The application is configured for deployment to the GOV.UK PaaS Cloud Foundry platform in the CCS organisation.

### Configuration
The Github repository enabled in the CCS Travis CI organisational account on travis-ci.com: https://travis-ci.com/github/Crown-Commercial-Service/ccs-scale-cat-service/.

Multi-environment/branch build and deployment configuration is contained within the `.travis.yml` file.  This file defines a Maven based build (caching the .m2 repo), required environment variables (encrypted where appropriate) and installs the Cloud Foundry CLI tools on the build VM.

### Deployment
For each environment/branch deploy configuration block, the [script](https://docs.travis-ci.com/user/deployment/script/) provider is used to call `deploy.sh` which performs the tasks of logging into Cloud Foundry and pushing the app via its manifest.

The `manifest.yml` file defines the actual Cloud Foundry build and deployment, using the Java Buildpack (specifying version 11) and variable substitution to utilise or pass through into the runtime environment required data from the CI deployment configuration.
