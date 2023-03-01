package uk.gov.crowncommercial.dts.scale.cat.auth.apikey;

import java.util.Collection;
import org.springframework.security.core.GrantedAuthority;
import lombok.Builder;
import lombok.Value;

/**
 * POJO for holding API Key details. This is limited to just the key and associated granted
 * authorities at the moment.
 */
@Value
@Builder
public class ApiKeyDetails {

  String key;

  Collection<GrantedAuthority> authorities;
}
