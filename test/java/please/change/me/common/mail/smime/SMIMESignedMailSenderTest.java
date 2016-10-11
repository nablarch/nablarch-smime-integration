package please.change.me.common.mail.smime;


import java.io.File;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Properties;
import javax.mail.Authenticator;
import javax.mail.BodyPart;
import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.Multipart;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.internet.InternetAddress;
import org.junit.Before;
import org.junit.Test;

import nablarch.common.mail.AttachedFile;
import nablarch.common.mail.FreeTextMailContext;
import nablarch.fw.launcher.CommandLine;
import nablarch.fw.launcher.Main;
import please.change.me.common.mail.testsupport.MailTestSupport;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

/**
 * {@link SMIMESignedMailSender}のテスト。
 *
 * @author hisaaki sioiri
 */
public class SMIMESignedMailSenderTest extends MailTestSupport {

    /** メールサーバへの接続プロパティ */
    private static final Properties MAIL_SESSION_PROPERTIES = new Properties();

    /** メール受信時のウェイト時間 */
    private static final int WAIT_TIME = 5000;

    static {
        MAIL_SESSION_PROPERTIES.setProperty("mail.smtp.host", "localhost");
        MAIL_SESSION_PROPERTIES.setProperty("mail.host", "localhost");
        MAIL_SESSION_PROPERTIES.setProperty("mail.pop3.host", "localhost");
        MAIL_SESSION_PROPERTIES.setProperty("mail.pop3.port", "110");
    }

    @Before
    public void setupTestCase() throws Exception {
        cleanupMail("to1");
        cleanupMail("cc1");
        cleanupMail("bcc1");
    }

    /**
     * テスト用のメールフォルダをクリーニングする。
     *
     * @param account クリーニング対象のメールアカウント
     * @throws Exception
     */
    private void cleanupMail(final String account) throws Exception {
        Session session = Session.getInstance(MAIL_SESSION_PROPERTIES, new Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(account, "default");
            }
        });

        Store store = session.getStore("pop3");
        store.connect();
        Folder folder = store.getFolder("INBOX");
        folder.open(Folder.READ_WRITE);
        Message[] messages = folder.getMessages();
        System.out.println("account " + account + ": " + messages.length + " messages will be deleted.");
        for (int i = 0; i < messages.length; i++) {
            messages[i].setFlag(Flags.Flag.DELETED, true);
        }
        folder.close(true);
        store.close();
    }

    private Message[] getMailMessage(final String account) throws Exception {
        Session session = Session.getInstance(MAIL_SESSION_PROPERTIES, new Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(account, "default");
            }
        });

        Store store = session.getStore("pop3");
        store.connect();
        Folder folder = store.getFolder("INBOX");
        for (int i = 0; i < 10; i++) {
            folder.open(Folder.READ_WRITE);
            Message[] messages = folder.getMessages();
            if (messages.length >= 1) {
                return messages;
            }
            folder.close(true);
            Thread.sleep(WAIT_TIME);
        }
        return null;
    }

    /**
     * 添付ファイル無しパターンの電子署名確認。
     */
    @Test
    public void testSmimeNoAttachedFile() throws Exception {

        FreeTextMailContext mailContext = new FreeTextMailContext();
        mailContext.setSubject("けんめい");
        mailContext.setMailBody("本文");
        mailContext.setCharset("utf-8");
        mailContext.setFrom("from@from.com");
        mailContext.addTo("to1@localhost");
        mailContext.addCc("cc1@localhost");
        mailContext.addBcc("bcc1@localhost");
        mailContext.setMailSendPatternId("01");
        mailRequest(mailContext);
        // 処理対象外
        mailContext.setMailSendPatternId("00");
        mailRequest(mailContext);
        // 処理対象外
        mailContext.setMailSendPatternId("02");
        mailRequest(mailContext);

        // バッチ実行
        CommandLine commandLine = new CommandLine("-diConfig",
                "please/change/me/common/mail/smime/SmimeSignedMailSenderTest.xml", "-requestPath",
                "please.change.me.common.mail.smime.SMIMESignedMailSender/SENDMAIL00", "-userId", "userid",
                "-mailSendPatternId", "01");
        int exitCode = Main.execute(commandLine);

        assertThat(exitCode, is(0));

        // ログアサート
        assertLog("メール送信要求が 1 件あります。");
        assertLog("メール送信完了。メールリクエストID 101");

        // 送信したメッセージの確認
        Message[] toMessages = getMailMessage("to1");
        Message[] ccMessages = getMailMessage("cc1");
        Message[] bccMessages = getMailMessage("bcc1");

        // メッセージが1つう送信されていること
        assertThat(toMessages, is(notNullValue()));
        assertThat(toMessages.length, is(1));
        assertThat(ccMessages, is(notNullValue()));
        assertThat(ccMessages.length, is(1));
        assertThat(bccMessages, is(notNullValue()));
        assertThat(bccMessages.length, is(1));

        // 各メッセージ部をアサート
        for (Message message : new Message[]{toMessages[0], ccMessages[0], bccMessages[0]}) {
            // メッセージ共通部のアサート
            assertThat(message.getContentType(), is(containsString("multipart/signed;")));
            assertThat(message.getContentType(), is(containsString("application/pkcs7-signature")));
            assertThat(message.getContentType(), is(containsString("micalg=sha-1;")));
            assertThat(message.getFrom().length, is(1));
            assertThat((InternetAddress) message.getFrom()[0], is(new InternetAddress("from@from.com")));

            assertThat(message.getContent(), is(instanceOf(Multipart.class)));
            assertThat(message.getSubject(), is("けんめい"));

            //----------------------------------------------------------------------
            // 本文部分のアサート
            //----------------------------------------------------------------------
            Multipart multipart = (Multipart) message.getContent();
            BodyPart body = multipart.getBodyPart(0);
            assertThat(body.getContentType(), is(containsString("text/plain")));
            assertThat(body.getContentType(), is(containsString("charset=utf-8")));
            assertThat((String) body.getContent(), is("本文"));

            //----------------------------------------------------------------------
            // 電子署名部のアサート
            //----------------------------------------------------------------------
            BodyPart smime = multipart.getBodyPart(1);
            assertThat(smime.getFileName(), is("smime.p7s"));

        }

        PreparedStatement statement = testDbConnection.prepareStatement(
                "select * from mail_send_request order by mail_request_id");
        ResultSet rs = statement.executeQuery();
        assertThat(rs.next(), is(true));
        assertThat(rs.getString("mail_request_id"), is("101"));
        assertThat(rs.getString("status"), is("B"));
        assertThat(rs.getObject("sending_timestamp"), is(notNullValue()));
        assertThat(rs.next(), is(true));
        assertThat(rs.getString("mail_request_id"), is("102"));
        assertThat(rs.getString("status"), is("A"));
        assertThat(rs.getObject("sending_timestamp"), is(nullValue()));
        assertThat(rs.next(), is(true));
        assertThat(rs.getString("mail_request_id"), is("103"));
        assertThat(rs.getString("status"), is("A"));
        assertThat(rs.getObject("sending_timestamp"), is(nullValue()));
    }

    /**
     * 添付ファイル有りパターンの電子署名確認。
     */
    @Test
    public void testSmimeAttachedFile() throws Exception {

        FreeTextMailContext mailContext = new FreeTextMailContext();
        mailContext.setSubject("けんめい");
        mailContext.setMailBody("本文");
        mailContext.setCharset("utf-8");
        mailContext.setFrom("from@from.com");
        mailContext.addTo("to1@localhost");
        mailContext.addCc("cc1@localhost");
        mailContext.addBcc("bcc1@localhost");
        mailContext.setMailSendPatternId("01");
        mailContext.addAttachedFile(new AttachedFile("text/plain", new File("test/java/please/change/me/common/mail/smime/data/temp1.txt")));
        mailRequest(mailContext);
        // 処理対象外
        mailContext.setMailSendPatternId("00");
        mailRequest(mailContext);
        // 処理対象外
        mailContext.setMailSendPatternId("02");
        mailRequest(mailContext);

        // バッチ実行
        CommandLine commandLine = new CommandLine("-diConfig",
                "please/change/me/common/mail/smime/SmimeSignedMailSenderTest.xml", "-requestPath",
                "please.change.me.common.mail.smime.SMIMESignedMailSender/SENDMAIL00", "-userId", "userid",
                "-mailSendPatternId", "01");
        int exitCode = Main.execute(commandLine);

        assertThat(exitCode, is(0));

        // ログアサート
        assertLog("メール送信要求が 1 件あります。");
        assertLog("メール送信完了。メールリクエストID 101");

        // 送信したメッセージの確認
        Message[] toMessages = getMailMessage("to1");
        Message[] ccMessages = getMailMessage("cc1");
        Message[] bccMessages = getMailMessage("bcc1");

        // メッセージが1つう送信されていること
        assertThat(toMessages, is(notNullValue()));
        assertThat(toMessages.length, is(1));
        assertThat(ccMessages, is(notNullValue()));
        assertThat(ccMessages.length, is(1));
        assertThat(bccMessages, is(notNullValue()));
        assertThat(bccMessages.length, is(1));

        // 各メッセージ部をアサート
        for (Message message : new Message[]{toMessages[0], ccMessages[0], bccMessages[0]}) {

            // メッセージ共通部のアサート
            assertThat(message.getContentType(), is(containsString("multipart/signed;")));
            assertThat(message.getContentType(), is(containsString("application/pkcs7-signature")));
            assertThat(message.getContentType(), is(containsString("micalg=sha-1;")));
            assertThat(message.getFrom().length, is(1));
            assertThat((InternetAddress) message.getFrom()[0], is(new InternetAddress("from@from.com")));

            assertThat(message.getContent(), is(instanceOf(Multipart.class)));
            assertThat(message.getSubject(), is("けんめい"));

            //----------------------------------------------------------------------
            // 本文部分のアサート
            //----------------------------------------------------------------------
            Multipart multipart = (Multipart) message.getContent();
            assertThat(multipart.getCount(), is(2));
            BodyPart body = multipart.getBodyPart(0);
            assertThat(body.getContentType(), is(containsString("multipart/mixed")));
            assertThat(body.getContent(), is(instanceOf(Multipart.class)));
            Multipart bodyPart = (Multipart) body.getContent();

            // 本文部は、本文と添付ファイルの2つ
            assertThat(bodyPart.getCount(), is(2));
            BodyPart bodyText = bodyPart.getBodyPart(0);
            assertThat(bodyText.getContentType(), is(containsString("text/plain")));
            assertThat(bodyText.getContentType(), is(containsString("charset=utf-8")));
            assertThat((String) bodyText.getContent(), is("本文"));

            BodyPart file = bodyPart.getBodyPart(1);
            assertThat(file.getFileName(), is("temp1.txt"));
            assertThat(file.getContentType(), is(containsString("text/plain")));
            assertThat((String) file.getContent(), is("l-1\r\nl-2\r\n"));

            //----------------------------------------------------------------------
            // 電子署名部のアサート
            //----------------------------------------------------------------------
            BodyPart smime = multipart.getBodyPart(1);
            assertThat(smime.getFileName(), is("smime.p7s"));
        }

        PreparedStatement statement = testDbConnection.prepareStatement(
                "select * from mail_send_request order by mail_request_id");
        ResultSet rs = statement.executeQuery();
        assertThat(rs.next(), is(true));
        assertThat(rs.getString("mail_request_id"), is("101"));
        assertThat(rs.getString("status"), is("B"));
        assertThat(rs.getObject("sending_timestamp"), is(notNullValue()));
        assertThat(rs.next(), is(true));
        assertThat(rs.getString("mail_request_id"), is("102"));
        assertThat(rs.getString("status"), is("A"));
        assertThat(rs.getObject("sending_timestamp"), is(nullValue()));
        assertThat(rs.next(), is(true));
        assertThat(rs.getString("mail_request_id"), is("103"));
        assertThat(rs.getString("status"), is("A"));
        assertThat(rs.getObject("sending_timestamp"), is(nullValue()));
    }
    /**
     * 添付ファイル複数有りパターンの電子署名確認。
     */
    @Test
    public void testSmimeMultiAttachedFile() throws Exception {

        FreeTextMailContext mailContext = new FreeTextMailContext();
        mailContext.setSubject("けんめい");
        mailContext.setMailBody("本文");
        mailContext.setCharset("utf-8");
        mailContext.setFrom("from@from.com");
        mailContext.addTo("to1@localhost");
        mailContext.addCc("cc1@localhost");
        mailContext.addBcc("bcc1@localhost");
        mailContext.setMailSendPatternId("04");
        mailContext.addAttachedFile(new AttachedFile("text/plain", new File("test/java/please/change/me/common/mail/smime/data/temp1.txt")));
        mailContext.addAttachedFile(new AttachedFile("application/pdf", new File("test/java/please/change/me/common/mail/smime/data/temp1.pdf")));
        mailRequest(mailContext);
        // 処理対象外
        mailContext.setMailSendPatternId("00");
        mailRequest(mailContext);
        // 処理対象外
        mailContext.setMailSendPatternId("02");
        mailRequest(mailContext);

        // バッチ実行
        CommandLine commandLine = new CommandLine("-diConfig",
                "please/change/me/common/mail/smime/SmimeSignedMailSenderTest.xml", "-requestPath",
                "please.change.me.common.mail.smime.SMIMESignedMailSender/SENDMAIL00", "-userId", "userid",
                "-mailSendPatternId", "04");
        int exitCode = Main.execute(commandLine);

        assertThat(exitCode, is(0));

        // ログアサート
        assertLog("メール送信要求が 1 件あります。");
        assertLog("メール送信完了。メールリクエストID 101");

        // 送信したメッセージの確認
        Message[] toMessages = getMailMessage("to1");
        Message[] ccMessages = getMailMessage("cc1");
        Message[] bccMessages = getMailMessage("bcc1");

        // メッセージが1つう送信されていること
        assertThat(toMessages, is(notNullValue()));
        assertThat(toMessages.length, is(1));
        assertThat(ccMessages, is(notNullValue()));
        assertThat(ccMessages.length, is(1));
        assertThat(bccMessages, is(notNullValue()));
        assertThat(bccMessages.length, is(1));

        // 各メッセージ部をアサート
        for (Message message : new Message[]{toMessages[0], ccMessages[0], bccMessages[0]}) {

            // メッセージ共通部のアサート
            assertThat(message.getContentType(), is(containsString("multipart/signed;")));
            assertThat(message.getContentType(), is(containsString("application/pkcs7-signature")));
            assertThat(message.getContentType(), is(containsString("micalg=sha-1;")));
            assertThat(message.getFrom().length, is(1));
            assertThat((InternetAddress) message.getFrom()[0], is(new InternetAddress("from@from.com")));

            assertThat(message.getContent(), is(instanceOf(Multipart.class)));
            assertThat(message.getSubject(), is("けんめい"));

            //----------------------------------------------------------------------
            // 本文部分のアサート
            //----------------------------------------------------------------------
            Multipart multipart = (Multipart) message.getContent();
            assertThat(multipart.getCount(), is(2));
            BodyPart body = multipart.getBodyPart(0);
            assertThat(body.getContentType(), is(containsString("multipart/mixed")));
            assertThat(body.getContent(), is(instanceOf(Multipart.class)));
            Multipart bodyPart = (Multipart) body.getContent();

            // 本文部は、本文と添付ファイル2ファイルの3つ
            assertThat(bodyPart.getCount(), is(3));
            BodyPart bodyText = bodyPart.getBodyPart(0);
            assertThat(bodyText.getContentType(), is(containsString("text/plain")));
            assertThat(bodyText.getContentType(), is(containsString("charset=utf-8")));
            assertThat((String) bodyText.getContent(), is("本文"));

            BodyPart file = bodyPart.getBodyPart(1);
            assertThat(file.getFileName(), is("temp1.txt"));
            assertThat(file.getContentType(), is(containsString("text/plain")));
            assertThat((String) file.getContent(), is("l-1\r\nl-2\r\n"));

            BodyPart pdf = bodyPart.getBodyPart(2);
            assertThat(pdf.getFileName(), is("temp1.pdf"));
            assertThat(pdf.getContentType(), is(containsString("application/pdf")));

            //----------------------------------------------------------------------
            // 電子署名部のアサート
            //----------------------------------------------------------------------
            BodyPart smime = multipart.getBodyPart(1);
            assertThat(smime.getFileName(), is("smime.p7s"));
        }

        PreparedStatement statement = testDbConnection.prepareStatement(
                "select * from mail_send_request order by mail_request_id");
        ResultSet rs = statement.executeQuery();
        assertThat(rs.next(), is(true));
        assertThat(rs.getString("mail_request_id"), is("101"));
        assertThat(rs.getString("status"), is("B"));
        assertThat(rs.getObject("sending_timestamp"), is(notNullValue()));
        assertThat(rs.next(), is(true));
        assertThat(rs.getString("mail_request_id"), is("102"));
        assertThat(rs.getString("status"), is("A"));
        assertThat(rs.getObject("sending_timestamp"), is(nullValue()));
        assertThat(rs.next(), is(true));
        assertThat(rs.getString("mail_request_id"), is("103"));
        assertThat(rs.getString("status"), is("A"));
        assertThat(rs.getObject("sending_timestamp"), is(nullValue()));
    }

    /**
     * 添付ファイルのcontentTypeを空文字列にした場合。
     *
     * 電子署名生成でエラーになること。
     */
    @Test
    public void testAttachedFileContextTypeNull() throws Exception {

        FreeTextMailContext mailContext = new FreeTextMailContext();
        mailContext.setSubject("けんめい");
        mailContext.setMailBody("本文");
        mailContext.setCharset("utf-8");
        mailContext.setFrom("from@from.com");
        mailContext.addTo("to1@localhost");
        mailContext.addCc("cc1@localhost");
        mailContext.addBcc("bcc1@localhost");
        mailContext.setMailSendPatternId("01");
        mailContext.addAttachedFile(new AttachedFile("", new File("test/java/please/change/me/common/mail/smime/data/temp1.txt")));
        mailRequest(mailContext);
        // 処理対象外
        mailContext.setMailSendPatternId("00");
        mailRequest(mailContext);
        // 処理対象外
        mailContext.setMailSendPatternId("02");
        mailRequest(mailContext);

        // バッチ実行
        CommandLine commandLine = new CommandLine("-diConfig",
                "please/change/me/common/mail/smime/SmimeSignedMailSenderTest.xml", "-requestPath",
                "please.change.me.common.mail.smime.SMIMESignedMailSender/SENDMAIL00", "-userId", "userid",
                "-mailSendPatternId", "02");
        int exitCode = Main.execute(commandLine);

        assertThat(exitCode, is(199));

        // ログアサート
        assertLog("メール送信要求が 1 件あります。");

        PreparedStatement statement = testDbConnection.prepareStatement(
                "select * from mail_send_request order by mail_request_id");
        ResultSet rs = statement.executeQuery();
        assertThat(rs.next(), is(true));
        assertThat(rs.getString("mail_request_id"), is("101"));
        assertThat(rs.getString("status"), is("A"));
        assertThat(rs.getObject("sending_timestamp"), is(nullValue()));
        assertThat(rs.next(), is(true));
        assertThat(rs.getString("mail_request_id"), is("102"));
        assertThat(rs.getString("status"), is("A"));
        assertThat(rs.getObject("sending_timestamp"), is(nullValue()));
        assertThat(rs.next(), is(true));
        // 障害になったレコード
        assertThat(rs.getString("mail_request_id"), is("103"));
        assertThat(rs.getString("status"), is("Z"));
        assertThat(rs.getObject("sending_timestamp"), is(nullValue()));
        statement.close();

    }

}

