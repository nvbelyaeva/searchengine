package searchengine.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

@Setter
@Getter
@NoArgsConstructor
@Entity
@Table(name = "site")
public class Website {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;
    @Column(columnDefinition = "VARCHAR(255)", nullable = false, unique = true)
    private String url;
    @Column(columnDefinition = "VARCHAR(255)", nullable = false)
    private String name;
    @Column(name = "last_error", nullable = true, columnDefinition = "TEXT")
    private String lastError;
    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "enum ('INDEXING', 'INDEXED', 'FAILED')", nullable = false)
    private StatusType status;
    @Column(name = "status_time", nullable = false)
    private LocalDateTime statusTime;
    @OneToMany(mappedBy = "site", cascade = CascadeType.ALL)
    private List<Page> pages;
}
