package uk.gov.crowncommercial.dts.scale.agreement.model.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.Collection;

/**
 * Agreement Detail.
 */
@Data
public class AgreementDetail {

  /**
   * Commercial Agreement Number e.g. "RM1045".
   */
  private String number;

  /**
   * Commercial Agreement Name e.g. "Technology Products 2".
   */
  private String name;

  /**
   * Short textual description of the commercial agreement.
   */
  private String description;

  /**
   * Effective start date of Commercial Agreement.
   */
  private LocalDate startDate;

  /**
   * Effective end date of Commercial Agreement.
   */
  private LocalDate endDate;

  /**
   * Effective start date of Commercial Agreement.
   */
  private String detailUrl;

  /**
   * A party (organization).
   */
  private Organization owner;

  /**
   * Contacts.
   */
  private Collection<Contact> contacts;

  /**
   * Short description of the benefit.
   */
  private Collection<String> benefits;

  /**
   * Associated lots.
   */
  private Collection<LotSummary> lots;

  /**
   * Assessment ID provided when a new assessment is created.
   */
  private Integer lotAssessmentTool;

  /**
   * Defines if a project can be set up without defining a lot. The lot is defined as an assessment provided by the agreement.
   */
  private Boolean preDefinedLotRequired;
}
