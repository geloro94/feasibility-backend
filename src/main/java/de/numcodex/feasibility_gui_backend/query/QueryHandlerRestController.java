package de.numcodex.feasibility_gui_backend.query;

import com.fasterxml.jackson.core.JsonProcessingException;
import de.numcodex.feasibility_gui_backend.query.api.QueryResult;
import de.numcodex.feasibility_gui_backend.query.api.StoredQuery;
import de.numcodex.feasibility_gui_backend.query.api.StructuredQuery;
import de.numcodex.feasibility_gui_backend.query.conversion.StoredQueryConverter;
import de.numcodex.feasibility_gui_backend.query.dispatch.QueryDispatchException;
import java.util.ArrayList;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import javax.ws.rs.core.Context;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponentsBuilder;

/*
Rest Interface for the UI to send queries from the ui to the ui backend.
*/
@RequestMapping("api/v1/query-handler")
@RestController
@CrossOrigin
@Slf4j
public class QueryHandlerRestController {

  private final QueryHandlerService queryHandlerService;
  private final String apiBaseUrl;

  public QueryHandlerRestController(QueryHandlerService queryHandlerService,
      @Value("${app.apiBaseUrl}") String apiBaseUrl) {
    this.queryHandlerService = queryHandlerService;
    this.apiBaseUrl = apiBaseUrl;
  }

  @PostMapping("run-query")
  public ResponseEntity<Object> runQuery(
      @Valid @RequestBody StructuredQuery query, @Context HttpServletRequest httpServletRequest) {

    Long queryId;
    try {
      queryId = queryHandlerService.runQuery(query);
    } catch (QueryDispatchException e) {
      log.error("Error while running query", e);
      return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    UriComponentsBuilder uriBuilder = (apiBaseUrl != null && !apiBaseUrl.isEmpty())
        ? ServletUriComponentsBuilder.fromUriString(apiBaseUrl)
        : ServletUriComponentsBuilder.fromRequestUri(httpServletRequest);

    var uriString = uriBuilder.replacePath("")
        .pathSegment("api", "v1", "query-handler", "result", String.valueOf(queryId))
        .build()
        .toUriString();
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add(HttpHeaders.LOCATION, uriString);
    return new ResponseEntity<>(httpHeaders, HttpStatus.CREATED);
  }

  @GetMapping(path = "/result/{id}")
  public QueryResult getQueryResult(@PathVariable("id") Long queryId) {
    return queryHandlerService.getQueryResult(queryId);
  }

  @PostMapping(path = "/stored-query")
  public ResponseEntity<Object> storeQuery(@RequestBody StoredQuery query, @Context HttpServletRequest httpServletRequest) {
    Long queryId;
    try {
      queryId = queryHandlerService.storeQuery(query);
    } catch (JsonProcessingException e) {
      log.error("Error while storing query", e);
      return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
    } catch (DataIntegrityViolationException e) {
      return new ResponseEntity<>(HttpStatus.CONFLICT);
    }

    UriComponentsBuilder uriBuilder = (apiBaseUrl != null && !apiBaseUrl.isEmpty())
        ? ServletUriComponentsBuilder.fromUriString(apiBaseUrl)
        : ServletUriComponentsBuilder.fromRequestUri(httpServletRequest);

    var uriString = uriBuilder.replacePath("")
        .pathSegment("api", "v1", "query-handler", "stored-query", String.valueOf(queryId))
        .build()
        .toUriString();
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add(HttpHeaders.LOCATION, uriString);
    return new ResponseEntity<>(httpHeaders, HttpStatus.CREATED);
  }

  @GetMapping(path = "/stored-query/{queryId}")
  public ResponseEntity<Object> getStoredQuery(@PathVariable(value = "queryId") Long queryId) {

    var query = queryHandlerService.getQuery(queryId);
    try {
      return new ResponseEntity<>(StoredQueryConverter.convertPersistenceToApi(query), HttpStatus.OK);
    } catch (JsonProcessingException e) {
      return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  @GetMapping(path = "/stored-query")
  public ResponseEntity<Object> getStoredQueryList() {
    String authorId = "foobar"; // TODO: obviously get this from access token
    var queries = queryHandlerService.getQueriesForAuthor(authorId);
    var ret = new ArrayList<StoredQuery>();
    queries.forEach(q -> {
      try {
        StoredQuery convertedQuery = StoredQueryConverter.convertPersistenceToApi(q);
        convertedQuery.setStructuredQuery(null);
        ret.add(convertedQuery);
      } catch (JsonProcessingException e) {
        log.error("Error converting query");
      }
    });
    return new ResponseEntity<>(ret, HttpStatus.OK);
  }
}
