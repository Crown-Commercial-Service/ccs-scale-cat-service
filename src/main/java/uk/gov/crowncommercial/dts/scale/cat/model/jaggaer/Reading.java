package uk.gov.crowncommercial.dts.scale.cat.model.jaggaer;

import java.util.Date;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
public class Reading {
  Integer readerId;
  String readerCode;
  String readerName;
  ReaderCompany readerCompany;
  String readerType;
  Date readingDate;
}