#########################################################
# Config: deploy-all
#
# This configuration will deploy all components.
#########################################################

module "cat-service" {
  source       = "../../cat-service"
  organisation = var.organisation
  space        = var.space
  environment  = var.environment
  cf_username  = var.cf_username
  cf_password  = var.cf_password
  instances    = var.instances
  memory       = var.memory
  dev_mode     = var.dev_mode
  log_level    = var.log_level
  resolve_buyer_users_by_sso = var.resolve_buyer_users_by_sso
  eetime_enabled = var.eetime_enabled
}
