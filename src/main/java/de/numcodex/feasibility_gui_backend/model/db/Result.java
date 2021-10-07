package de.numcodex.feasibility_gui_backend.model.db;

import lombok.Data;

import java.io.Serializable;
import java.sql.Timestamp;

import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Embeddable;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.MapsId;

@Data
@Entity
public class Result {

    @EmbeddedId
    private ResultId id;

    @MapsId("queryId")
    @JoinColumn(referencedColumnName = "id", name = "query_id")
    @ManyToOne(fetch = FetchType.LAZY)
    private Query query;

    @MapsId("siteId")
    @JoinColumn(referencedColumnName = "id", name = "site_id")
    @ManyToOne(fetch = FetchType.LAZY)
    private Site site;

    @Convert(converter = ResultTypeConverter.class)
    private ResultType resultType;

    private Integer result;

    private Timestamp receivedAt;

    @Data
    @Embeddable
    static class ResultId implements Serializable {
        @Column(name = "query_id")
        private String queryId;

        @Column(name = "site_id")
        private Long siteId;
    }
}
