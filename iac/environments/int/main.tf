#########################################################
# Environment: INT
#
# Deploy CaT resources
#########################################################
module "deploy-all" {
  source      = "../../modules/configs/deploy-all"
  space       = "INT"
  environment = "int"
  cf_username = var.cf_username
  cf_password = var.cf_password
  dev_mode    = true
  instances   = 3
  eetime_enabled = false
}
