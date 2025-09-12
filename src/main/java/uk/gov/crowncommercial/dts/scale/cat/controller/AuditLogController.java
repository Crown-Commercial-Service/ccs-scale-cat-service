package uk.gov.crowncommercial.dts.scale.cat.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.crowncommercial.dts.scale.cat.dto.AuditLogDTO;
import uk.gov.crowncommercial.dts.scale.cat.service.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;

@RestController
public class AuditLogController {

    private AuditLogService auditLogService;

    @GetMapping("/audit")
    public String getAuditLogs(HttpServletRequest request) {
        AuditLogDTO auditLogDTO = null;
        String fromDateStr = request.getParameter("fromDate");
        String toDateStr = request.getParameter("toDate");
        if (fromDateStr != null && toDateStr != null) {
            LocalDateTime fromDate = LocalDateTime.parse(fromDateStr);
            LocalDateTime toDate = LocalDateTime.parse(toDateStr);
            auditLogDTO = auditLogService.getAuditLogDTO(fromDate, toDate);
         }
        return auditLogDTO.toString();
    }
}

