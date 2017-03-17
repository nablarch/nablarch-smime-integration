package please.change.me.common.mail.testsupport.entity;

import java.sql.Blob;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.Table;

/**
 * メール添付ファイル
 */
@Entity
@IdClass(MailAttachedFile.MailAttachedFileId.class)
@Table(name = "MAIL_ATTACHED_FILE")
public class MailAttachedFile {

    public MailAttachedFile() {
    }

    @Id
    @Column(name = "MAIL_REQUEST_ID", length = 20, nullable = false)
    public String mailRequestId;

    @Id
    @Column(name = "ATTACHED_NO", nullable = false)
    public Integer attachedNo;

    @Column(name = "FILE_NAME", length = 100)
    public String fileName;

    @Column(name = "CONTENT_TYPE", length = 50)
    public String contentType;

    @Column(name = "ATTACHED_FILE")
    public Blob attachedFile;

    /**
     * メール添付ファイルの複合キー
     */
    @Embeddable
    public static class MailAttachedFileId {

        @Column(name = "MAIL_REQUEST_ID", length = 20, nullable = false)
        public String mailRequestId;

        @Column(name = "ATTACHED_NO", nullable = false)
        public Integer attachedNo;
    }
}
