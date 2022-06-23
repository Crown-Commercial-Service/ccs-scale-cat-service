package uk.gov.crowncommercial.dts.scale.cat.service;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.config.RPAAPIConfig;

@Service
@RequiredArgsConstructor
@Slf4j
public class EncryptionService {

  private final RPAAPIConfig rpaAPIConfig;
  private static final String CHAR_LOWERCASE = "abcdefghijklmnopqrstuvwxyz";
  private static final String CHAR_UPPERCASE = CHAR_LOWERCASE.toUpperCase();
  private static final String DIGIT = "0123456789";
  private static final String OTHER_SYMBOL = "\\!£$%&/()=?'^€[]#@,;.:_-><*+";
  private static final int PASSWORD_LENGTH = 10;
  private static SecureRandom random = new SecureRandom();

  public String generateBuyerPassword() {
    return encryptPassword(createJaggaerPassword());
  }

  @SneakyThrows
  public String encryptPassword(final String password) {
    String encryptString = Base64.getEncoder().encodeToString(
        createCipher(Cipher.ENCRYPT_MODE).doFinal(password.getBytes(StandardCharsets.US_ASCII)));
    log.trace("Encrypted key: {} ", encryptString);
    return encryptString;
  }

  @SneakyThrows
  public String decryptPassword(final String encryptedPassword) {
    String decryptString = new String(
        createCipher(Cipher.DECRYPT_MODE).doFinal(Base64.getDecoder().decode(encryptedPassword)));
    return decryptString;
  }

  @SneakyThrows
  private Cipher createCipher(final int mode) {
    byte[] iv = rpaAPIConfig.getEncryptionIv().getBytes(StandardCharsets.US_ASCII);
    IvParameterSpec ivspec = new IvParameterSpec(iv);
    SecretKeySpec secretKey = new SecretKeySpec(
        rpaAPIConfig.getEncryptionKey().getBytes(StandardCharsets.US_ASCII), "AES");
    Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
    cipher.init(mode, secretKey, ivspec);
    return cipher;
  }

  private String createJaggaerPassword() {
    var result = new StringBuilder(PASSWORD_LENGTH);
    // at least 4 chars (lowercase)
    result.append(generateRandomString(CHAR_LOWERCASE, 4));
    // at least 2 chars (uppercase)
    result.append(generateRandomString(CHAR_UPPERCASE, 2));
    // at least 2 digits
    result.append(generateRandomString(DIGIT, 2));
    // at least 2 special characters
    result.append(generateRandomString(OTHER_SYMBOL, 2));
    var password = result.toString();
    // shuffle again
    return shuffleString(password);
  }

  private static String generateRandomString(final String input, final int size) {
    var result = new StringBuilder(size);
    for (var i = 0; i < size; i++) {
      // produce a random order
      var index = random.nextInt(input.length());
      result.append(input.charAt(index));
    }
    return result.toString();
  }

  // make it more random
  private static String shuffleString(final String input) {
    List<String> result = Arrays.asList(input.split(""));
    Collections.shuffle(result);
    return result.stream().collect(Collectors.joining());
  }
}
