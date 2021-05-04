package uk.gov.crowncommercial.dts.scale.cat.service;

import org.springframework.stereotype.Service;

/**
 *
 */
@Service
public class JaggaerUserProfileService {

  public String resolveJaggaerUserId(String principal) {
    /*
     * TODO: Query the Jaggaer ProfileManagement API using the principal from the token subject
     */
    return "102463";
  }

}
