# CCS Scale CaT Service (API)

## Overview

This is the Java 11+ SpringBoot implementation of the CaT (Tenders) API [Open API specification](https://github.com/Crown-Commercial-Service/ccs-scale-api-definitions/blob/master/cat/CaT-service.yaml).

It is deployed via Travis CI & Terraform to the GOV.UK PaaS environments.

## Prerequisites

The [Terraform CaT infra](https://github.com/Crown-Commercial-Service/ccs-scale-cat-paas-infra) should have been provisioned against the target environment(s) prior to provisioning of this service infrastructure. This will ensure the Tenders DB is created and initialised with the necessary schema.

## Local initialisation & provisioning (sandbox spaces only)

Terraform state for each space (environment) is persisted to a dedicated AWS account. Access keys for an account in this environment with the appropriate permissions must be obtained before provisioning any infrastructure from a local development machine. The S3 state bucket name and Dynamo DB locaking table name must also be known.

1. The AWS credentials, state bucket name and DDB locking table name must be supplied during the `terraform init` operation to setup the backend. `cd` to the `iac/environments/{env}` folder corresponding to the environment you are provisioning, and execute the following command to initialise the backend:

   ```
   terraform init \
   -backend-config="access_key=ACCESS_KEY_ID" \
   -backend-config="secret_key=SECRET_ACCESS_KEY" \
   -backend-config="bucket=S3_STATE_BUCKET_NAME" \
   -backend-config="dynamodb_table=DDB_LOCK_TABLE_NAME"
   ```

   Note: some static/non-sensitive options are supplied in the `backend.tf` file. Any sensitive config supplied via command line only (this may change in the future if we can use Vault or similar).

   This will ensure all Terraform state is persisted to the dedicated bucket and that concurrent builds are prevented via the DDB locking table.

2. `cd` to `/uk-gov-paas/iac/environments/{env}`
3. Build the CaT Service using maven `mvn clean package`

4. We use Terraform to provision the underlying service infrastructure in a space. We will need to supply the Cloud Foundry login credentials for the user who will provision the infrastructure.

   These credentials can be supplied in one of 3 ways:

   - via a `secret.tfvars` file, e.g. `terraform apply -var-file="secret.tfvars"` (this file should not be committed to Git)
   - via input variables in the terminal, e.g. `terraform apply -var="cf_username=USERNAME" -var="cf_password=PASSWORD"`
   - via environment variables, e.g. `$ export TF_VAR_cf_username=USERNAME`

   Assume one of these approaches is used in the commands below (TBD)

5. Run `terraform plan` to confirm the changes look ok
6. Run `terraform apply` to deploy to UK.Gov PaaS

## Provision the service via Travis

The main environments are provisioned automatically via Travis CI. Merges to key branches will trigger an automatic deployment to certain environments - mapped below:

- `develop` branch -> `development` space
- `release/int` branch -> `int` space
- `release/nft` branch -> `nft` space
- `release/uat` branch -> `uat` space

* other environments TBD (these mappings may change as we evolve the process as more environments come online)
* feature branches can be deployed to specific sandboxes by making minor changes in the `travis.yml` file (follow instructions)
