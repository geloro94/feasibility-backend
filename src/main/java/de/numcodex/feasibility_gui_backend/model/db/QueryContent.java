package de.numcodex.feasibility_gui_backend.model.db;

import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;

@Data
@Entity
@NoArgsConstructor
public class QueryContent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "query_content", nullable = false)
    private String queryContent;

    @Column(columnDefinition = "TEXT", insertable = false, updatable = false)
    private String hash;

    public QueryContent(String queryContent) {
        this.queryContent = queryContent;
    }
}
