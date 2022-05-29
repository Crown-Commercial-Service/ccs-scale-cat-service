variable "organisation" {}

variable "space" {}

variable "environment" {}

variable "buildpack" {
  default = "https://github.com/cloudfoundry/java-buildpack#v4.33"
}

variable "disk_quota" {
  default = 2048
}

variable "healthcheck_timeout" {
  default = 0
}

variable "instances" {}

variable "memory" {}

variable "path" {
  default = "../../../target/ccs-scale-cat-service-0.0.1-SNAPSHOT.jar"
}

variable "cf_username" {
  sensitive = true
}

variable "cf_password" {
  sensitive = true
}

variable "dev_mode" {}

variable "log_level" {}

variable "resolve_buyer_users_by_sso"{}
