package uk.gov.crowncommercial.dts.scale.cat.service;

import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.exception.AuthorisationFailureException;
import uk.gov.crowncommercial.dts.scale.cat.exception.JaggaerRPAException;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.Award2AllOf;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.AwardState;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.OrganizationReference1;
import uk.gov.crowncommercial.dts.scale.cat.model.rpa.RPAProcessInput;
import uk.gov.crowncommercial.dts.scale.cat.model.rpa.RPAProcessNameEnum;

@Service
@RequiredArgsConstructor
@Slf4j
public class AwardService {

  private final ValidationService validationService;
  private final UserProfileService userService;
  private final RPAGenericService rpaGenericService;

  public static final String JAGGAER_USER_NOT_FOUND = "Jaggaer user not found";
  public static final String SUPPLIERS_NOT_FOUND = "Supplier details not found";
  public static final String AWARDS_TO_MUTLIPLE_SUPPLIERS =
      "Awards to multiple suppliers is not currently supported";
  private static final String PRE_AWARD = "Pre-Award";
  private static final String EDIT_PRE_AWARD = "Edit Pre-Awarding";
  private static final String AWARD = "Award";
  private static final String RPA_END_EVALUATION_ERROR_DESC =
      "Awarding action dropdown button not found";

  /**
   * Pre-Award or Edit-Pre-Award or Complete Award to the supplied suppliers.
   * 
   * @param profile
   * @param projectId
   * @param eventId
   * @param awardAction
   * @param award
   * @return status
   */
  public String createOrUpdateAward(final String profile, final Integer projectId,
      final String eventId, final AwardState awardState, final Award2AllOf award,
      final Integer awardId) {
    var procurementEvent = validationService.validateProjectAndEventIds(projectId, eventId);
    var buyerUser = userService.resolveBuyerUserBySSOUserLogin(profile)
        .orElseThrow(() -> new AuthorisationFailureException(JAGGAER_USER_NOT_FOUND));

    if (award.getSuppliers().size() > 1) {
      throw new JaggaerRPAException(AWARDS_TO_MUTLIPLE_SUPPLIERS);
    }

    var validSuppliers = rpaGenericService.getValidSuppliers(procurementEvent, award.getSuppliers()
        .stream().map(OrganizationReference1::getId).collect(Collectors.toList()));

    var validSupplierName =
        validSuppliers.getFirst().stream().map(e -> e.getCompanyData().getName()).findFirst()
            .orElseThrow(() -> new JaggaerRPAException(SUPPLIERS_NOT_FOUND));

    var awardAction = awardState.equals(AwardState.COMPLETE) ? AWARD : PRE_AWARD;
    if (awardId != null) {
      awardAction = awardState.equals(AwardState.COMPLETE) ? AWARD : EDIT_PRE_AWARD;
    }
    log.info("SupplierName {} and Award-action {}", validSupplierName, awardAction);
    // Creating RPA process input string
    var inputBuilder = RPAProcessInput.builder().userName(buyerUser.getEmail())
        .password(rpaGenericService.getBuyerEncryptedPassword(buyerUser.getUserId()))
        .ittCode(procurementEvent.getExternalReferenceId()).awardAction(awardAction)
        .supplierName(validSupplierName);
    try {
      return rpaGenericService.callRPAMessageAPI(inputBuilder.build(), RPAProcessNameEnum.AWARDING);
    } catch (JaggaerRPAException je) {
      // End Evaluation
      if (je.getMessage().contains(RPA_END_EVALUATION_ERROR_DESC)) {
        this.callEndEvaluation(buyerUser.getEmail(),
            rpaGenericService.getBuyerEncryptedPassword(buyerUser.getUserId()),
            procurementEvent.getExternalReferenceId());
        return rpaGenericService.callRPAMessageAPI(inputBuilder.build(),
            RPAProcessNameEnum.AWARDING);
      }
      throw je;
    }
  }

  /**
   * Calls RPA to End Evaluation
   */
  public String callEndEvaluation(final String userEmail, final String password,
      final String externalReferenceId) {
    log.info("Calling End Evaluation for {}", externalReferenceId);
    // Creating RPA process input string
    var inputBuilder = RPAProcessInput.builder().userName(userEmail).password(password)
        .ittCode(externalReferenceId);
    return rpaGenericService.callRPAMessageAPI(inputBuilder.build(),
        RPAProcessNameEnum.END_EVALUATION);
  }

}
