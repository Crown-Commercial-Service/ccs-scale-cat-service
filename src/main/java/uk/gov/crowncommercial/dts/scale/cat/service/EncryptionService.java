package uk.gov.crowncommercial.dts.scale.cat.service;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
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
  private static SecureRandom secureRandom = new SecureRandom();

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
    return new String(
        createCipher(Cipher.DECRYPT_MODE).doFinal(Base64.getDecoder().decode(encryptedPassword)));
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
    String combinedChars =
        // uppercase chars
        secureRandom.ints(3, 65, 90 + 1).mapToObj(i -> String.valueOf((char) i))
            .collect(Collectors.joining())
            // lowercase chars
            .concat(secureRandom.ints(3, 97, 122 + 1).mapToObj(i -> String.valueOf((char) i))
                .collect(Collectors.joining()))
            // numbers
            .concat(secureRandom.ints(3, 48, 57 + 1).mapToObj(i -> String.valueOf((char) i))
                .collect(Collectors.joining()))
            // special chars
            .concat(secureRandom.ints(3, 33, 47 + 1).mapToObj(i -> String.valueOf((char) i))
                .collect(Collectors.joining()))
            // random alphanumeric
            .concat(secureRandom.ints(3, 48, 122 + 1).mapToObj(i -> String.valueOf((char) i))
                .collect(Collectors.joining()));
    List<Character> pwdChars =
        combinedChars.chars().mapToObj(c -> (char) c).collect(Collectors.toList());
    Collections.shuffle(pwdChars);
    return pwdChars.stream()
        .collect(StringBuilder::new, StringBuilder::append, StringBuilder::append).toString();
  }

}
