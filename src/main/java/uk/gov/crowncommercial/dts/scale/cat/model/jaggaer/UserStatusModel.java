package uk.gov.crowncommercial.dts.scale.cat.model.jaggaer;

import java.util.Optional;

import static uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.SubUsers.*;

/**
 * Class to hold details of a user status retrieved from Jaegger
 */
public class UserStatusModel {
    public SubUser buyerSubUser;

    public SubUser guruSubUser;

    public ReturnCompanyData supplierCompanyData;
}
