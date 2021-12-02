package de.numcodex.feasibility_gui_backend.service.query_executor.impl.dsf;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import de.numcodex.feasibility_gui_backend.service.query_executor.QueryStatus;
import org.highmed.fhir.client.FhirWebserviceClient;
import org.highmed.fhir.client.WebsocketClient;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.MeasureReport;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.Task;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.hl7.fhir.r4.model.MeasureReport.MeasureReportStatus.COMPLETE;
import static org.hl7.fhir.r4.model.MeasureReport.MeasureReportType.SUMMARY;
import static org.hl7.fhir.r4.model.Task.TaskIntent.ORDER;
import static org.hl7.fhir.r4.model.Task.TaskStatus.COMPLETED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class DSFQueryResultCollectorIT {

    private static final String SINGLE_DIC_RESULT_PROFILE = "https://www.netzwerk-universitaetsmedizin.de/fhir/StructureDefinition/codex-task-single-dic-result-simple-feasibility|0.1.0";

    @Mock
    private FhirWebserviceClient fhirClient;

    @Mock
    private FhirWebClientProvider fhirWebClientProvider;

    private WebsocketClientMock websocketClient;
    private QueryResultCollector resultCollector;

    @BeforeEach
    public void setUp() {
        FhirContext fhirCtx = FhirContext.forR4();
        DSFQueryResultStore resultStore = new DSFQueryResultStore();
        DSFQueryResultHandler resultHandler = new DSFQueryResultHandler(fhirWebClientProvider);

        websocketClient = new WebsocketClientMock();
        resultCollector = new DSFQueryResultCollector(resultStore, fhirCtx, fhirWebClientProvider, resultHandler);
    }

    private Task createTestTask(String queryId, String siteId, String measureReportReference, String profile) {
        Task task = new Task()
                .setStatus(COMPLETED)
                .setIntent(ORDER)
                .setAuthoredOn(new Date())
                .setInstantiatesUri("http://highmed.org/bpe/Process/requestSimpleFeasibility/0.1.0");

        task.getRequester()
                .setType("Organization")
                .getIdentifier().setSystem("http://highmed.org/fhir/NamingSystem/organization-identifier").setValue(siteId);

        task.getRestriction().getRecipientFirstRep()
                .setType("Organization")
                .getIdentifier().setSystem("http://highmed.org/fhir/NamingSystem/organization-identifier").setValue("ZARS");

        task.addInput()
                .setType(new CodeableConcept()
                        .addCoding(new Coding()
                                .setSystem("http://highmed.org/fhir/CodeSystem/bpmn-message")
                                .setCode("message-name")))
                .setValue(new StringType("requestSimpleFeasibilityMessage"));
        task.addInput()
                .setType(new CodeableConcept()
                        .addCoding(new Coding()
                                .setSystem("http://highmed.org/fhir/CodeSystem/bpmn-message")
                                .setCode("business-key")))
                .setValue(new StringType(queryId));
        task.addInput()
                .setType(new CodeableConcept()
                        .addCoding(new Coding()
                                .setSystem("https://www.netzwerk-universitaetsmedizin.de/fhir/CodeSystem/feasibility")
                                .setCode("measure-reference")))
                .setValue(new Reference()
                        .setReference("urn:uuid:" + UUID.randomUUID()));
        task.addOutput()
                .setType(new CodeableConcept()
                        .addCoding(new Coding()
                                .setSystem("https://www.netzwerk-universitaetsmedizin.de/fhir/CodeSystem/feasibility")
                                .setCode("measure-report-reference")))
                .setValue(new Reference()
                        .setReference(measureReportReference));

        task.setMeta(new Meta().addProfile(profile));

        return task;
    }

    private MeasureReport createTestMeasureReport(int measureCount) {
        MeasureReport measureReport = new MeasureReport()
                .setStatus(COMPLETE)
                .setType(SUMMARY)
                .setDate(new Date());

        measureReport.addGroup()
                .addPopulation()
                .setCode(new CodeableConcept()
                        .addCoding(new Coding()
                                .setSystem("http://terminology.hl7.org/CodeSystem/measure-population")
                                .setCode("initial-population")))
                .setCount(measureCount);
        return measureReport;
    }

    @Test
    public void testRegisteredListenerGetsNotifiedOnUpdate() throws IOException, FhirWebClientProvisionException {
        String queryId = UUID.randomUUID().toString();
        String siteId = "DIC";
        String measureReportId = UUID.randomUUID().toString();
        Task task = createTestTask(queryId, siteId, "MeasureReport/" + measureReportId, SINGLE_DIC_RESULT_PROFILE);

        int measureCount = 5;
        MeasureReport measureReport = createTestMeasureReport(measureCount);

        when(fhirWebClientProvider.provideFhirWebsocketClient()).thenReturn(websocketClient);
        when(fhirWebClientProvider.provideFhirWebserviceClient()).thenReturn(fhirClient);
        when(fhirClient.read(MeasureReport.class, measureReportId)).thenReturn(measureReport);

        var actual = new Object() {
            String queryId = null;
            String siteId = null;
            QueryStatus status = null;
        };
        resultCollector.addResultListener((qId, sId, status) -> {
            actual.queryId = qId;
            actual.siteId = sId;
            actual.status = status;
        });

        websocketClient.fakeIncomingMessage(task);

        assertEquals(queryId, actual.queryId);
        assertEquals(siteId, actual.siteId);
        assertEquals(QueryStatus.COMPLETED, actual.status);
    }

    @Test
    public void testResultFeasibilityIsPresentAfterListenerGetsNotifiedOnUpdate() throws IOException, FhirWebClientProvisionException {
        String measureReportId = UUID.randomUUID().toString();
        Task task = createTestTask(UUID.randomUUID().toString(), "DIC", "MeasureReport/" + measureReportId, SINGLE_DIC_RESULT_PROFILE);

        int measureCount = 5;
        MeasureReport measureReport = createTestMeasureReport(measureCount);

        when(fhirWebClientProvider.provideFhirWebsocketClient()).thenReturn(websocketClient);
        when(fhirWebClientProvider.provideFhirWebserviceClient()).thenReturn(fhirClient);
        when(fhirClient.read(MeasureReport.class, measureReportId)).thenReturn(measureReport);

        resultCollector.addResultListener((qId, cId, status) -> {
            try {
                int resultFeasibility = resultCollector.getResultFeasibility(qId, cId);

                assertEquals(measureCount, resultFeasibility);
            } catch (Exception e) {
                fail();
            }
        });

        websocketClient.fakeIncomingMessage(task);
    }

    @Test
    public void testSiteIdsArePresentAfterListenerGetsNotifiedOnUpdate() throws IOException, FhirWebClientProvisionException {
        String siteId = "DIC";
        String measureReportId = UUID.randomUUID().toString();
        Task task = createTestTask(UUID.randomUUID().toString(), siteId, "MeasureReport/" + measureReportId, SINGLE_DIC_RESULT_PROFILE);

        int measureCount = 5;
        MeasureReport measureReport = createTestMeasureReport(measureCount);

        when(fhirWebClientProvider.provideFhirWebsocketClient()).thenReturn(websocketClient);
        when(fhirWebClientProvider.provideFhirWebserviceClient()).thenReturn(fhirClient);
        when(fhirClient.read(MeasureReport.class, measureReportId)).thenReturn(measureReport);

        resultCollector.addResultListener((qId, sId, status) -> {
            try {
                List<String> siteIds = resultCollector.getResultSiteIds(qId);

                assertEquals(List.of(siteId), siteIds);
            } catch (Exception e) {
                fail();
            }
        });

        websocketClient.fakeIncomingMessage(task);
    }

    @Test
    public void testRegisteredListenersGetNotNotifiedOnIncomingTasksThatAreNoResults() throws IOException, FhirWebClientProvisionException {
        String measureReportId = UUID.randomUUID().toString();
        Task task = createTestTask(UUID.randomUUID().toString(), "DIC", "MeasureReport/" + measureReportId, "other-profile");

        when(fhirWebClientProvider.provideFhirWebsocketClient()).thenReturn(websocketClient);
        resultCollector.addResultListener((qId, cId, status) -> fail());

        websocketClient.fakeIncomingMessage(task);
    }

    private static class WebsocketClientMock implements WebsocketClient {

        private Consumer<DomainResource> consumer;

        @Override
        public void connect() {
            // DO NOTHING
        }

        @Override
        public void disconnect() {
            // DO NOTHING
        }

        @Override
        public void setDomainResourceHandler(Consumer<DomainResource> consumer, Supplier<IParser> supplier) {
            this.consumer = consumer;
        }

        @Override
        public void setPingHandler(Consumer<String> consumer) {
            // DO NOTHING
        }

        public void fakeIncomingMessage(DomainResource resource) {
            consumer.accept(resource);
        }
    }
}
