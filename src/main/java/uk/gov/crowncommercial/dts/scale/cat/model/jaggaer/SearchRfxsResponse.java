package uk.gov.crowncommercial.dts.scale.cat.model.jaggaer;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SearchRfxsResponse {

  Integer returnCode;
  String returnMessage;
  Integer totRecords;
  Integer returnedRecords;
  SearchRfxsDataList dataList;

}
