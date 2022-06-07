data "cloudfoundry_org" "ccs_scale_cat" {
  name = var.organisation
}

data "cloudfoundry_space" "space" {
  name = var.space
  org  = data.cloudfoundry_org.ccs_scale_cat.id
}

data "cloudfoundry_domain" "domain" {
  name = "london.cloudapps.digital"
}

data "cloudfoundry_service_instance" "tenders_database" {
  name_or_id = "${var.environment}-ccs-scale-cat-tenders-pg-db"
  space      = data.cloudfoundry_space.space.id
}

data "cloudfoundry_service_instance" "tenders_s3_documents" {
  name_or_id = "${var.environment}-ccs-scale-cat-tenders-s3-documents"
  space      = data.cloudfoundry_space.space.id
}

data "cloudfoundry_user_provided_service" "logit" {
  name  = "${var.environment}-ccs-scale-cat-logit-ssl-drain"
  space = data.cloudfoundry_space.space.id
}

data "cloudfoundry_user_provided_service" "ip_router" {
  name  = "${var.environment}-ccs-scale-cat-ip-router"
  space = data.cloudfoundry_space.space.id
}

# SSM Params

# Jaggaer
data "aws_ssm_parameter" "jaggaer_client_id" {
  name = "/cat/${var.environment}/jaggaer-client-id"
}

data "aws_ssm_parameter" "jaggaer_client_secret" {
  name = "/cat/${var.environment}/jaggaer-client-secret"
}

data "aws_ssm_parameter" "jaggaer_base_url" {
  name = "/cat/${var.environment}/jaggaer-base-url"
}

data "aws_ssm_parameter" "jaggaer_token_url" {
  name = "/cat/${var.environment}/jaggaer-token-url"
}

data "aws_ssm_parameter" "jaggaer_self_service_id" {
  name = "/cat/${var.environment}/jaggaer-self-service-id"
}

# Auth server / CII
data "aws_ssm_parameter" "auth_server_jwk_set_uri" {
  name = "/cat/${var.environment}/auth-server-jwk-set-uri"
}

data "aws_ssm_parameter" "conclave_wrapper_api_base_url" {
  name = "/cat/${var.environment}/conclave-wrapper-api-base-url"
}

data "aws_ssm_parameter" "conclave_wrapper_api_key" {
  name = "/cat/${var.environment}/conclave-wrapper-api-key"
}

data "aws_ssm_parameter" "conclave_wrapper_identities_api_key" {
  name = "/cat/${var.environment}/conclave-wrapper-identities-api-key"
}

# Agreements Service
data "aws_ssm_parameter" "agreements_service_base_url" {
  name = "/cat/${var.environment}/agreements-service-base-url"
}

# RPA
data "aws_ssm_parameter" "jaggaer_rpa_base_url" {
  name = "/cat/${var.environment}/jaggaer-rpa-base-url"
}

data "aws_ssm_parameter" "jaggaer_rpa_username" {
  name = "/cat/${var.environment}/jaggaer-rpa-username"
}

data "aws_ssm_parameter" "jaggaer_rpa_password" {
  name = "/cat/${var.environment}/jaggaer-rpa-password"
}

data "aws_ssm_parameter" "jaggaer_rpa_encryption_key" {
  name = "/cat/${var.environment == "prd" ? "prd" : "default"}/jaggaer-rpa-encryption-key"
}

data "aws_ssm_parameter" "jaggaer_rpa_encryption_iv" {
  name = "/cat/${var.environment == "prd" ? "prd" : "default"}/jaggaer-rpa-encryption-iv"
}

# Document Upload Service
data "aws_ssm_parameter" "document_upload_service_upload_base_url" {
  name = "/cat/${var.environment}/document-upload-service-upload-base-url"
}

data "aws_ssm_parameter" "document_upload_service_get_base_url" {
  name = "/cat/${var.environment}/document-upload-service-get-base-url"
}

data "aws_ssm_parameter" "document_upload_service_api_key" {
  name = "/cat/${var.environment}/document-upload-service-api-key"
}

data "aws_ssm_parameter" "document_upload_service_aws_access_key_id" {
  name = "/cat/${var.environment}/document-upload-service-aws-access-key-id"
}

data "aws_ssm_parameter" "document_upload_service_aws_secret_key" {
  name = "/cat/${var.environment}/document-upload-service-aws-secret-key"
}

data "aws_ssm_parameter" "document_upload_service_s3_bucket" {
  name = "/cat/${var.environment}/document-upload-service-s3-bucket"
}

resource "cloudfoundry_app" "cat_service" {
  annotations = {}
  buildpack   = var.buildpack
  disk_quota  = var.disk_quota
  enable_ssh  = true
  environment = {
    "JBP_CONFIG_OPEN_JDK_JRE" : "{ \"jre\": { version: 17.+ } }"
    "JBP_CONFIG_SPRING_AUTO_RECONFIGURATION" : "{enabled: false}"
    "SPRING_PROFILES_ACTIVE": "cloud"
    "config.flags.devMode" : var.dev_mode
    "config.flags.resolveBuyerUsersBySSO" : var.resolve_buyer_users_by_sso
    "logging.level.uk.gov.crowncommercial.dts.scale.cat" : var.log_level

    # Jaggaer
    "spring.security.oauth2.client.registration.jaggaer.client-id" : data.aws_ssm_parameter.jaggaer_client_id.value
    "spring.security.oauth2.client.registration.jaggaer.client-secret" : data.aws_ssm_parameter.jaggaer_client_secret.value
    "spring.security.oauth2.client.provider.jaggaer.token-uri" : data.aws_ssm_parameter.jaggaer_token_url.value
    "config.external.jaggaer.baseUrl" : data.aws_ssm_parameter.jaggaer_base_url.value
    "config.external.jaggaer.self-service-id" : data.aws_ssm_parameter.jaggaer_self_service_id.value
    
    # Auth server / CII
    "spring.security.oauth2.resourceserver.jwt.jwk-set-uri" : data.aws_ssm_parameter.auth_server_jwk_set_uri.value
    "config.external.conclave-wrapper.baseUrl" : data.aws_ssm_parameter.conclave_wrapper_api_base_url.value
    "config.external.conclave-wrapper.apiKey" : data.aws_ssm_parameter.conclave_wrapper_api_key.value
    "config.external.conclave-wrapper.identitiesApiKey" : data.aws_ssm_parameter.conclave_wrapper_identities_api_key.value
    
    # Agreements Service
    "config.external.agreements-service.baseUrl" : data.aws_ssm_parameter.agreements_service_base_url.value

    # RPA
    "config.external.jaggaer.rpa.baseUrl" : data.aws_ssm_parameter.jaggaer_rpa_base_url.value
    "config.external.jaggaer.rpa.user-name" : data.aws_ssm_parameter.jaggaer_rpa_username.value
    "config.external.jaggaer.rpa.user-pwd" : data.aws_ssm_parameter.jaggaer_rpa_password.value
    "config.external.jaggaer.rpa.encrpytion-key" : data.aws_ssm_parameter.jaggaer_rpa_encryption_key.value
    "config.external.jaggaer.rpa.encrpytion-iv" : data.aws_ssm_parameter.jaggaer_rpa_encryption_iv.value

    # Document Upload Service
    "config.external.doc-upload-svc.upload-base-url" : data.aws_ssm_parameter.document_upload_service_upload_base_url.value
    "config.external.doc-upload-svc.get-base-url" : data.aws_ssm_parameter.document_upload_service_get_base_url.value
    "config.external.doc-upload-svc.api-key" : data.aws_ssm_parameter.document_upload_service_api_key.value
    "config.external.doc-upload-svc.aws-access-key-id" : data.aws_ssm_parameter.document_upload_service_aws_access_key_id.value
    "config.external.doc-upload-svc.aws-secret-key" : data.aws_ssm_parameter.document_upload_service_aws_secret_key.value
    "config.external.doc-upload-svc.s3-bucket" : data.aws_ssm_parameter.document_upload_service_s3_bucket.value
  }
  health_check_timeout = var.healthcheck_timeout
  health_check_type    = "port"
  instances            = var.instances
  labels               = {}
  memory               = var.memory
  name                 = "${var.environment}-ccs-scale-cat-service"
  path                 = var.path
  source_code_hash     = filebase64sha256(var.path)
  ports                = [8080]
  space                = data.cloudfoundry_space.space.id
  stopped              = false
  timeout              = 600

  service_binding {
    service_instance = data.cloudfoundry_service_instance.tenders_database.id
  }

  service_binding {
    service_instance = data.cloudfoundry_service_instance.tenders_s3_documents.id
  }

  service_binding {
    service_instance = data.cloudfoundry_user_provided_service.logit.id
  }
}

resource "cloudfoundry_route" "cat_service" {
  domain   = data.cloudfoundry_domain.domain.id
  space    = data.cloudfoundry_space.space.id
  hostname = "${var.environment}-ccs-scale-cat-service"

  target {
    app  = cloudfoundry_app.cat_service.id
    port = 8080
  }
}

# Bind to nginx IP Router UPS
resource "cloudfoundry_route_service_binding" "cat_service" {
  service_instance = data.cloudfoundry_user_provided_service.ip_router.id
  route            = cloudfoundry_route.cat_service.id
}
