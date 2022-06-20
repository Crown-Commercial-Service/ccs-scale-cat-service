package uk.gov.crowncommercial.dts.scale.cat.model.entity;

import java.time.Instant;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Entity
@Table(name = "buyer_user_details")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class BuyerUserDetails {

  @Id
  @Column(name = "user_id")
  String userId;

  @Column(name = "user_password")
  String userPassword;

  @Column(name = "exported")
  boolean exported;

  @Column(name = "created_by", updatable = false)
  String createdBy;

  @Column(name = "created_at", updatable = false)
  Instant createdAt;

  @Column(name = "updated_by")
  String updatedBy;

  @Column(name = "updated_at")
  Instant updatedAt;
}
