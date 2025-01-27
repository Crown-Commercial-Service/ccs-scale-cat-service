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
@Table(name = "cap_load_jobs")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CapLoadJobs {

  @Id
  @Column(name = "role_cluster")
  private String roleCluster;

  @Column(name = "role_num")
  private String roleNumber;

  @Column(name = "role_family")
  private String roleFamily;

  @Column(name = "role_name")
  private String roleName;

  @Column(name = "role_level")
  private String roleLevel;

}
