package uk.gov.crowncommercial.dts.scale.cat.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.DocumentAudienceType;

class DocumentKeyTest {

  @Test
  void testCreateKeySuccess() {
    // Decoded key is 'buyer-1234-test-file.txt'
    DocumentKey key = DocumentKey.fromString("YnV5ZXItMTIzNC10ZXN0LWZpbGUudHh0");
    assertEquals(DocumentAudienceType.BUYER, key.getAudience());
    assertEquals(1234, key.getFileId());
    assertEquals("test-file.txt", key.getFileName());
  }

  @Test
  void testCreateKeySuccessCapitalisedAudience() {
    // Decoded key is 'BUYER-1234-test-file.txt'
    DocumentKey key = DocumentKey.fromString("QlVZRVItMTIzNC10ZXN0LWZpbGUudHh0");
    assertEquals(DocumentAudienceType.BUYER, key.getAudience());
    assertEquals(1234, key.getFileId());
    assertEquals("test-file.txt", key.getFileName());
  }

  @Test
  void testCreateKeyFailNonMatchingPattern() {

    Exception exception = assertThrows(IllegalArgumentException.class, () -> {
      // Decoded key is 'test'
      DocumentKey.fromString("dGVzdA==");
    });
    assertEquals("Invalid Document Id", exception.getMessage());
  }

  @Test
  void testCreateKeyFailInvalidId() {

    Exception exception = assertThrows(IllegalArgumentException.class, () -> {
      // Decoded key is 'buyer-nonnumeric-test-file.txt'
      DocumentKey.fromString("YnV5ZXItbm9ubnVtZXJpYy10ZXN0LWZpbGUudHh0");
    });
    assertEquals("Invalid Document Id", exception.getMessage());
  }


  @Test
  void testCreateKeyFailInvalidAudience() {

    Exception exception = assertThrows(IllegalArgumentException.class, () -> {
      // Decoded key is 'unknown-1234-test-file.txt'
      DocumentKey.fromString("dW5rbm93bi0xMjM0LXRlc3QtZmlsZS50eHQ=");
    });
    assertEquals("Unexpected value 'unknown'", exception.getMessage());
  }
}
