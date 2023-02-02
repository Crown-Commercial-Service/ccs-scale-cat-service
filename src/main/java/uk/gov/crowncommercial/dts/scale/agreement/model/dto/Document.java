package uk.gov.crowncommercial.dts.scale.agreement.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.Instant;

/**
 * Document.
 */
@Data
public class Document {

  /**
   * URI for the document. Should be a perma-link and can be used as a key for the document.
   */
  private String url;

  /**
   * Document title.
   */
  @JsonProperty("title")
  private String name;

  /**
   * Document description.
   */
  private String description;

  /**
   * The type of document e.g. overview, t&cs, guidance, how to buy, contract notice etc.
   */
  private String documentType;

  /**
   * The language of the linked document using either two-letter ISO639-1, or extended BCP47
   * language tags. The use of lowercase two-letter codes from ISO639-1 is recommended unless there
   * is a clear user need for distinguishing the language subtype.
   */
  private String language;

  /**
   * The format of the document, using the open IANA Media Types codelist (see the values in the
   * 'Template' column), or using the 'offline/print' code if the described document is published
   * offline. For example, web pages have a format of 'text/html'.
   */
  private String format;

  /**
   * The date on which the document was first published. This is particularly important for legally
   * important documents such as notices of a tender.
   */
  @JsonProperty("datePublished")
  private Instant publishedDate;

  /**
   * Date that the document was last modified.
   */
  @JsonProperty("dateModified")
  private Instant modifiedDate;
}
