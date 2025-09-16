package uk.gov.crowncommercial.dts.scale.cat.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uk.gov.crowncommercial.dts.scale.cat.dto.AuditLogDTO;
import uk.gov.crowncommercial.dts.scale.cat.repo.AuditLogRepo;
import uk.gov.crowncommercial.dts.scale.cat.service.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;

@RestController
public class AuditLogController {

    private AuditLogService auditLogService;
    private AuditLogRepo auditLogRepo;

    @GetMapping("/audit/view-entry")
    public ResponseEntity getAuditLogs(@PathVariable("fromDate") final String fromDate, @PathVariable("toDate") final String toDate) {
        AuditLogDTO auditLogDTO = new AuditLogDTO();
        if (fromDate != null && toDate != null) {
            LocalDateTime localDateTimeFromDate = LocalDateTime.parse(fromDate);
            LocalDateTime localDateTimeToDate = LocalDateTime.parse(toDate);
            auditLogDTO = auditLogService.getAuditLogDTO(localDateTimeFromDate, localDateTimeToDate, auditLogDTO);
         }
        return ResponseEntity.ok(auditLogDTO);
    }
}

