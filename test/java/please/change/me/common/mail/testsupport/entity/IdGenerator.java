package please.change.me.common.mail.testsupport.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * 採番
 */
@Entity
@Table(name = "ID_GENERATOR")
public class IdGenerator {

    public IdGenerator() {
    }

    public IdGenerator(final String id, final int generatedValue) {
        this.id = id;
        this.generatedValue = generatedValue;
    }

    @Id
    @Column(name = "ID", length = 2, nullable = false)
    public String id;

    @Column(name = "GENERATED_VALUE", nullable = false)
    public Integer generatedValue;
}
