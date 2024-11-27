package uk.gov.crowncommercial.dts.scale.cat.service.ocds;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.gov.crowncommercial.dts.scale.cat.config.Constants;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.DataTemplate;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.LotDetail;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.OrganisationMapping;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementProject;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.*;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.CompanyData;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.ExportRfxResponse;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.RfxSetting;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.Supplier;
import uk.gov.crowncommercial.dts.scale.cat.repo.RetryableTendersDBDelegate;
import uk.gov.crowncommercial.dts.scale.cat.service.AgreementsService;
import uk.gov.crowncommercial.dts.scale.cat.service.QuestionAndAnswerService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Populates release tender related information regarding a given project
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CompiledReleaseTenderService extends AbstractOcdsService {
    private final AgreementsService agreementsService;
    private final OcdsConverter ocdsConverter;
    private final RetryableTendersDBDelegate tendersDBDelegate;
    private final QuestionAndAnswerService questionAndAnswerService;

    /**
     * Populate general tender related information regarding the project
     */
    public MapperResponse populateGeneral(Record1 re, ProjectQuery pq) {
        CompletableFuture<Void> cf = null;

        if (pq != null && pq.getProject() != null) {
            Tender1 tender = OcdsHelper.getTender(re);
            ProcurementProject pp = pq.getProject();

            if (pp != null) {
                ProcurementEvent pe = EventsHelper.getFirstPublishedEvent(pp);

                if (pe != null && pe.getProcurementTemplatePayload() != null && pe.getProcurementTemplatePayload().getCriteria() != null) {
                    tender.setDescription(EventsHelper.getData(Constants.MAPPERS_RELEASE_DESC_CRITERIA, Constants.MAPPERS_RELEASE_DESC_GROUP, Constants.MAPPERS_RELEASE_DESC_QUESTION, pe.getProcurementTemplatePayload().getCriteria()));
                }

                if (pp.getProjectName() != null) {
                    tender.setTitle(pp.getProjectName());
                }

                tender.setValue(getMaxValue(pp));
                tender.setMinValue(getMinValue(pp));
                tender.setSubmissionMethod(List.of(SubmissionMethod.ELECTRONICSUBMISSION));

                cf = CompletableFuture.runAsync(() -> {
                    populateStatusAndPeriod(tender, pp, pq);
                    populateLots(pq, tender);
                });
            }
        }

        return new MapperResponse(re, cf);
    }

    /**
     * Populate status and period tender related information regarding the project
     */
    @SneakyThrows
    private void populateStatusAndPeriod(Tender1 tender, ProcurementProject pp, ProjectQuery pq) {
        if (pp != null && pq != null && tender != null) {
            ProcurementEvent pe = EventsHelper.getLastPublishedEvent(pp);
            ExportRfxResponse exportRfxResponse = getLatestRFXWithSuppliers(pq);

            ExportRfxResponse firstRfxResponse = getFirstRFXWithSuppliers(pq);

            if (exportRfxResponse != null && exportRfxResponse.getRfxSetting() != null && pe != null) {
                TenderStatus status = EventStatusHelper.getStatus(exportRfxResponse.getRfxSetting(), pe);

                if (status != null) {
                    tender.setStatus(status);
                }

                if (firstRfxResponse != null && firstRfxResponse.getRfxSetting() != null) {
                    tender.setTenderPeriod(getTenderPeriod(firstRfxResponse.getRfxSetting()));
                }
            }
        }
    }

    /**
     * Functionality to populate a given tender period for the project
     */
    private Period1 getTenderPeriod(RfxSetting rfxSetting) {
        Period1 period = new Period1();

        if (rfxSetting != null) {
            if (rfxSetting.getPublishDate() != null) {
                period.setStartDate(rfxSetting.getPublishDate());
            }

            if (rfxSetting.getCloseDate() != null) {
                period.setEndDate(rfxSetting.getCloseDate());
            }
        }

        return period;
    }

    /**
     * Functionality to populate lots for the project
     */
    private void populateLots(ProjectQuery pq, Tender1 tender) {
        Lot1 l = new Lot1();

        if (pq != null && pq.getProject() != null) {
            ProcurementProject pp = pq.getProject();

            if (pp != null && pp.getLotNumber() != null) {
                l.setId(pp.getLotNumber());

                if (pp.getCaNumber() != null && pp.getLotNumber() != null) {
                    LotDetail lotDetail = agreementsService.getLotDetails(pp.getCaNumber(), pp.getLotNumber());

                    if (lotDetail != null) {
                        if (lotDetail.getName() != null) {
                            l.setTitle(lotDetail.getName());
                        }

                        if (lotDetail.getDescription() != null) {
                            l.setDescription(lotDetail.getDescription());
                        }
                    }
                }

                tender.setLots(List.of(l));
            }
        }
    }

    /**
     * Functionality to populate "tenderers" for the project
     */
    public MapperResponse populateTenderers(Record1 re, ProjectQuery pq) {
        Tender1 tender = OcdsHelper.getTender(re);
        CompletableFuture<Void> cf = null;

        if (pq != null) {
            cf = CompletableFuture.runAsync(() -> {
                ExportRfxResponse rfxResponse = getFirstRFXWithSuppliers(pq);

                if (rfxResponse != null && rfxResponse.getSuppliersList() != null && rfxResponse.getSuppliersList().getSupplier() != null) {
                    List<Supplier> sdf = rfxResponse.getSuppliersList().getSupplier();
                    Set<Integer> bravoIds = sdf.stream().map(t -> t.getCompanyData().getId()).collect(Collectors.toSet());
                    Set<OrganisationMapping> orgMappings = tendersDBDelegate.findOrganisationMappingByExternalOrganisationIdIn(bravoIds);

                    if (orgMappings != null) {
                        List<OrganizationReference1> tenderers = rfxResponse.getSuppliersList().getSupplier().stream().map(t -> this.convertSuppliers(t, orgMappings)).toList();

                        tender.setTenderers(tenderers);
                    }
                }
            });
        }

        return new MapperResponse(re, cf);
    }

    /**
     * Converts organisation mapping and supplier data into organisation reference data
     */
    private OrganizationReference1 convertSuppliers(Supplier supplier, Set<OrganisationMapping> orgMappings) {
        OrganizationReference1 ref = new OrganizationReference1();

        if (supplier != null && supplier.getCompanyData() != null && supplier.getCompanyData().getId() != null && orgMappings != null) {
            Optional<OrganisationMapping> orgMap = orgMappings.stream().filter(t -> t.getExternalOrganisationId().equals(supplier.getCompanyData().getId())).findFirst();
            CompanyData cData = supplier.getCompanyData();

            if (orgMap.isPresent()) {
                if (orgMap.get().getOrganisationId() != null) {
                    ref.setId(orgMap.get().getCasOrganisationId());
                }
            }

            if (supplier.getStatusCode() != null && supplier.getStatus() != null) {
                ref.setContactPoint(new ContactPoint1().name(supplier.getStatusCode() + ":" + supplier.getStatus()));
            }

            if (cData.getName() != null) {
                ref.setName(cData.getName());
            }
        }

        return ref;
    }

    /**
     * Functionality to populate enquiries data for the project from OCDS question and answer data
     */
    public MapperResponse populateEnquiries(Record1 re, ProjectQuery pq) {
        if (pq != null && re != null) {
            ProcurementProject pp = pq.getProject();
            Tender1 tender = OcdsHelper.getTender(re);

            if (pp != null) {
                ProcurementEvent pe = EventsHelper.getFirstPublishedEvent(pp);

                if (pp.getId() != null && pe != null) {
                    QandAWithProjectDetails qandAWithProjectDetails = questionAndAnswerService.getQuestionAndAnswerForSupplierByEvent(pp.getId(), EventsHelper.getEventId(pe));

                    if (qandAWithProjectDetails != null && qandAWithProjectDetails.getQandA() != null) {
                        tender.setEnquiries(qandAWithProjectDetails.getQandA().stream().map(this::convertQA).toList());
                    }
                }
            }
        }

        return new MapperResponse(re);
    }

    /**
     * Converts a QandA object into an Enquiry1 object
     */
    private Enquiry1 convertQA(QandA t) {
        Enquiry1 result = new Enquiry1();

        if (t != null) {
            if (t.getQuestion() != null) {
                result.setTitle(t.getQuestion());
            }

            if (t.getAnswer() != null) {
                result.setAnswer(t.getAnswer());
            }

            if (t.getCreated() != null) {
                result.setDate(t.getCreated());
            }

            if (t.getLastUpdated() != null) {
                result.setDateAnswered(t.getLastUpdated());
            } else if (t.getCreated() != null) {
                result.setDateAnswered(t.getCreated());
            }

            if (t.getId() != null) {
                result.setId(String.valueOf(t.getId()));
            }
        }

        return result;
    }

    /**
     * Functionality to populate criteria data for the project
     */
    public MapperResponse populateCriteria(Record1 re, ProjectQuery pq) {
        if (re != null && pq != null && pq.getProject() != null) {
            Tender1 tender = OcdsHelper.getTender(re);
            ProcurementEvent pe = EventsHelper.getFirstPublishedEvent(pq.getProject());

            if (pe != null && pe.getProcurementTemplatePayload() != null) {
                DataTemplate template = pe.getProcurementTemplatePayload();

                if (template != null && template.getCriteria() != null) {
                    List<Criterion1> result = template.getCriteria().stream().map(ocdsConverter::convert).toList();

                    tender.setCriteria(result);
                }
            }
        }

        return new MapperResponse(re);
    }

    /**
     * Functionality to populate minimum value data for the project from OCDS question and answer data
     */
    private Value1 getMinValue(ProcurementProject pp) {
        if (pp != null) {
            ProcurementEvent pe = EventsHelper.getFirstPublishedEvent(pp);

            if (pe != null) {
                return getValue1(pe, Constants.MAPPERS_TENDER_MINVALUE_QUESTION);
            }
        }

        return null;
    }

    /**
     * Functionality to populate maximum value data for the project from OCDS question and answer data
     */
    private Value1 getMaxValue(ProcurementProject pp) {
        if (pp != null) {
            ProcurementEvent pe = EventsHelper.getFirstPublishedEvent(pp);

            if (pe != null) {
                return getValue1(pe, Constants.MAPPERS_TENDER_MAXVALUE_QUESTION);
            }
        }

        return null;
    }

    /**
     * Converts OCDS quest and answer data based on provided IDs into a Value1 object
     */
    private static Value1 getValue1(ProcurementEvent pe, String reqId) {
        if (reqId != null && pe != null && pe.getProcurementTemplatePayload() != null && pe.getProcurementTemplatePayload().getCriteria() != null) {
            String valueStr = EventsHelper.getData(Constants.MAPPERS_TENDER_VALUE_CRITERIA, Constants.MAPPERS_TENDER_VALUE_GROUP, reqId, pe.getProcurementTemplatePayload().getCriteria());

            if (valueStr != null && !valueStr.trim().isEmpty()) {
                // The value string should be a numeric, but it might not be - so try to map it, but fail silently if it doesn't work so it doesn't take down the project
                try {
                    BigDecimal bd = BigDecimal.valueOf(Double.parseDouble(valueStr));
                    Value1 amount = new Value1();
                    amount.amount(bd).currency(Currency1.GBP);
                    return amount;
                } catch (Exception ex) {
                    log.warn("Error parsing value string to numeric", ex);
                }
            }
        }

        return null;
    }
}