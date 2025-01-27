package uk.gov.crowncommercial.dts.scale.cat.model.entity.ca;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;
import lombok.experimental.FieldDefaults;

/**
*
*/
@Entity
@Table(name = "cap_load_service_capability")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CapLoadServiceCapability {

  @Id
  @Column(name = "service_capability_hdr")
  private String serviceCapabilityHdr;

  @Column(name = "service_capability")
  private String serviceCapability;

}
