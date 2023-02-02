package uk.gov.crowncommercial.dts.scale.agreement.model.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import java.time.Instant;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Embeddable
public class Timestamps {

  @Column(name = "created_by", updatable = false)
  String createdBy;

  @Column(name = "created_at", updatable = false)
  Instant createdAt;

  @Column(name = "updated_by")
  String updatedBy;

  @Column(name = "updated_at")
  Instant updatedAt;

  /**
   * Create entity time stamps.
   */
  public static Timestamps createTimestamps(final String userId) {
    var timestamps = new Timestamps();
    timestamps.setCreatedAt(Instant.now());
    timestamps.setCreatedBy(userId);
    return timestamps;
  }

  /**
   * Update entity time stamps.
   */
  public static Timestamps updateTimestamps(final Timestamps timestamps, final String userId) {
    timestamps.setUpdatedAt(Instant.now());
    timestamps.setUpdatedBy(userId);
    return timestamps;
  }
}
