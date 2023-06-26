package uk.gov.crowncommercial.dts.scale.cat.model.entity.ca;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import lombok.*;
import lombok.experimental.FieldDefaults;

/**
*
*/
@Entity
@Table(name = "cap_load_resources")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CapLoadResources {

  @Id
  @Column(name = "resource_cluster")
  private String resourceCluster;

  @Column(name = "resource_family")
  private String resourceFamily;

}
