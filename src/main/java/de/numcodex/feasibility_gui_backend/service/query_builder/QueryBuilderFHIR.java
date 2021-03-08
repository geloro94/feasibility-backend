package de.numcodex.feasibility_gui_backend.service.query_builder;

import de.numcodex.feasibility_gui_backend.model.query.StructuredQuery;

public class QueryBuilderFHIR implements QueryBuilder {

  @Override
  public String getQueryContent(StructuredQuery query) {
    return "FHIRQuery";
  }
}
