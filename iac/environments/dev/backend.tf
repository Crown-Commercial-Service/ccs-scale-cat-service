terraform {
  backend "s3" {
    region  = "eu-west-2"
    key     = "ccs-scale-cat-service-dev"
    encrypt = true
  }
}

provider "aws" {
  profile = "default"
  region  = "eu-west-2"
}
