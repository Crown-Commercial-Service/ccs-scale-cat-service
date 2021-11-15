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

data "cloudfoundry_user_provided_service" "logit" {
  name  = "${var.environment}-ccs-scale-cat-logit-ssl-drain"
  space = data.cloudfoundry_space.space.id
}

data "cloudfoundry_user_provided_service" "ip_router" {
  name  = "${var.environment}-ccs-scale-cat-ip-router"
  space = data.cloudfoundry_space.space.id
}

# SSM Params
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

data "aws_ssm_parameter" "auth_server_jwk_set_uri" {
  name = "/cat/${var.environment}/auth-server-jwk-set-uri"
}

data "aws_ssm_parameter" "agreements_service_base_url" {
  name = "/cat/${var.environment}/agreements-service-base-url"
}

data "aws_ssm_parameter" "conclave_wrapper_api_base_url" {
  name = "/cat/${var.environment}/conclave-wrapper-api-base-url"
}

data "aws_ssm_parameter" "conclave_wrapper_api_key" {
  name = "/cat/${var.environment}/conclave-wrapper-api-key"
}

resource "cloudfoundry_app" "cat_service" {
  annotations = {}
  buildpack   = var.buildpack
  disk_quota  = var.disk_quota
  enable_ssh  = true
  environment = {
    JBP_CONFIG_OPEN_JDK_JRE : "{ \"jre\": { version: 11.+ } }"
    "spring.security.oauth2.client.registration.jaggaer.client-id" : data.aws_ssm_parameter.jaggaer_client_id.value
    "spring.security.oauth2.client.registration.jaggaer.client-secret" : data.aws_ssm_parameter.jaggaer_client_secret.value
    "spring.security.oauth2.client.provider.jaggaer.token-uri" : data.aws_ssm_parameter.jaggaer_token_url.value
    "config.external.jaggaer.baseUrl" : data.aws_ssm_parameter.jaggaer_base_url.value
    "config.external.jaggaer.self-service-id" : data.aws_ssm_parameter.jaggaer_self_service_id.value
    "spring.security.oauth2.resourceserver.jwt.jwk-set-uri" : data.aws_ssm_parameter.auth_server_jwk_set_uri.value
    "config.external.agreements-service.baseUrl" : data.aws_ssm_parameter.agreements_service_base_url.value
    "config.external.conclave-wrapper.baseUrl" : data.aws_ssm_parameter.conclave_wrapper_api_base_url.value
    "config.external.conclave-wrapper.apiKey" : data.aws_ssm_parameter.conclave_wrapper_api_key.value
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
