package please.change.me.common.mail.testsupport;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.List;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;

import nablarch.common.mail.FreeTextMailContext;
import nablarch.common.mail.MailRequester;
import nablarch.common.mail.MailUtil;
import nablarch.common.mail.TemplateMailContext;
import nablarch.core.db.connection.AppDbConnection;
import nablarch.core.db.connection.ConnectionFactory;
import nablarch.core.db.transaction.SimpleDbTransactionExecutor;
import nablarch.core.db.transaction.SimpleDbTransactionManager;
import nablarch.core.repository.SystemRepository;
import nablarch.core.repository.di.ComponentDefinitionLoader;
import nablarch.core.repository.di.DiContainer;
import nablarch.core.repository.di.config.xml.XmlComponentDefinitionLoader;
import nablarch.core.transaction.TransactionFactory;
import oracle.jdbc.pool.OracleDataSource;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * メール関連のテストをサポートするクラス。
 *
 * @author hisaaki sioiri
 */
public class MailTestSupport {

    /** テスト用のデータベースコネクション */
    protected static Connection testDbConnection = null;

    /** タイムスタンプ型をアサートする際に使用するタイムスタンプ */
    protected static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyyMMddhhmmssSSS");

    @BeforeClass
    public static void setupClass() throws SQLException {
        ComponentDefinitionLoader loader = new XmlComponentDefinitionLoader(
                "please/change/me/common/mail/smime/SmimeSignedMailSenderTest.xml");
        SystemRepository.load(new DiContainer(loader));

        // メール送信要求テーブルの準備
        OracleDataSource dataSource = new OracleDataSource();
        dataSource.setUser(SystemRepository.getString("db.user"));
        dataSource.setPassword(SystemRepository.getString("db.password"));
        dataSource.setURL(SystemRepository.getString("db.url"));
        testDbConnection = dataSource.getConnection();

        //--------------------------------------------------------------------------------
        // メール送信要求テーブルの作成
        //--------------------------------------------------------------------------------
        try {
            testDbConnection.createStatement().execute("drop table mail_send_request CASCADE CONSTRAINTS");
        } catch (SQLException e) {
            e.printStackTrace();
        }
        testDbConnection.createStatement().execute("create table mail_send_request ("
                + " mail_request_id varchar2(20 char),"
                + " mail_subject varchar2(100 char),"
                + " from_mail_address varchar2(100 char),"
                + " reply_mail_address varchar2(100 char),"
                + " return_mail_address varchar2(100 char),"
                + " charset varchar2(200 char),"
                + " status char(1 char),"
                + " request_timestamp timestamp,"
                + " sending_timestamp timestamp,"
                + " body varchar2(1000 char),"
                + " mail_send_pattern_id char(2 char),"
                + " primary keY(mail_request_id)) nologging");

        //--------------------------------------------------------------------------------
        // メール送信先テーブルの作成
        //--------------------------------------------------------------------------------
        try {
            testDbConnection.createStatement().execute("drop table mail_recipient_table CASCADE CONSTRAINTS");
        } catch (SQLException e) {
            // nop
        }
        testDbConnection.createStatement().execute("create table mail_recipient_table ("
                + " mail_request_id varchar2(20 char),"
                + " recipient_no number(10),"
                + " recipient_type varchar2(3 char),"
                + " mail_address varchar2(100 char),"
                + " primary keY(mail_request_id, recipient_no)) nologging");

        //--------------------------------------------------------------------------------
        // メール添付ファイルテーブル
        //--------------------------------------------------------------------------------
        try {
            testDbConnection.createStatement().execute("drop table mail_attached_file CASCADE CONSTRAINTS");
        } catch (SQLException e) {
            // nop
        }
        testDbConnection.createStatement().execute("create table mail_attached_file ("
                + " mail_request_id varchar2(20 char),"
                + " attached_no number(10),"
                + " file_name varchar2(100 char),"
                + " content_type varchar2(50 char),"
                + " attached_file blob,"
                + " primary keY(mail_request_id, attached_no)) nologging");

        //--------------------------------------------------------------------------------
        // メールテンプレートテーブル
        //--------------------------------------------------------------------------------
        try {
            testDbConnection.createStatement().execute("drop table mail_template CASCADE CONSTRAINTS");
        } catch (SQLException e) {
            // nop
        }
        testDbConnection.createStatement().execute("create table mail_template ("
                + " template_id char(3char),"
                + " lang char(2 char),"
                + " subject varchar2(100 char),"
                + " body varchar2(1000 char),"
                + " charset varchar2(10 char),"
                + " primary keY(template_id, lang)) nologging");

        //--------------------------------------------------------------------------------
        // 採番テーブルの作成
        //--------------------------------------------------------------------------------
        try {
            testDbConnection.createStatement().execute("drop table id_generator CASCADE CONSTRAINTS");
        } catch (SQLException e) {
            // nop
        }
        testDbConnection.createStatement().execute("create table id_generator ("
                + " id char(2 char),"
                + " generated_value number(10),"
                + " primary key(id)) nologging");

        //--------------------------------------------------------------------------------
        // メッセージテーブルの作成
        //--------------------------------------------------------------------------------
        try {
            testDbConnection.createStatement().execute("drop table mail_message CASCADE CONSTRAINTS");
        } catch (SQLException e) {
            // nop
        }
        testDbConnection.createStatement().execute("create table mail_message ("
                + " message_id char(3 char),"
                + " lang char(2 char),"
                + " message varchar2(250 char),"
                + " primary key(message_id, lang)) nologging");
    }

    @Before
    public void setup() throws Exception {

        OnMemoryLogWriter.clear();

        cleaningTable("mail_send_request", "mail_recipient_table", "mail_attached_file", "mail_template", "mail_message", "id_generator");

        // メールテンプレートデータの準備
        PreparedStatement insertTemplate = testDbConnection.prepareStatement(
                "insert into mail_template values (?, ?, ?, ?, ?)");
        insertTemplate.setString(1, "001");
        insertTemplate.setString(2, "JP");
        insertTemplate.setString(3, "タイトル");
        insertTemplate.setString(4, "本文\n{line2}\n{line3}");
        insertTemplate.setString(5, "utf-8");
        insertTemplate.addBatch();
        insertTemplate.setString(1, "001");
        insertTemplate.setString(2, "EN");
        insertTemplate.setString(3, "title");
        insertTemplate.setString(4, "body\n{line2}\n{line3}");
        insertTemplate.setString(5, "utf-8");
        insertTemplate.addBatch();
        insertTemplate.setString(1, "002");
        insertTemplate.setString(2, "JP");
        insertTemplate.setString(3, "タイトル。");
        insertTemplate.setString(4, "ほんぶん。");
        insertTemplate.setString(5, "utf-8");
        insertTemplate.addBatch();
        insertTemplate.setString(1, "003");
        insertTemplate.setString(2, "JP");
        insertTemplate.setString(3, "タイトル。");
        insertTemplate.setString(4, "ほんぶん。");
        insertTemplate.setNull(5, Types.VARCHAR);
        insertTemplate.addBatch();
        insertTemplate.setString(1, "005");
        insertTemplate.setString(2, "JP");
        insertTemplate.setString(3, "タイトル。");
        insertTemplate.setString(4, "ほんぶん。");
        insertTemplate.setNull(5, Types.VARCHAR);
        insertTemplate.addBatch();
        insertTemplate.executeBatch();

        // 採番テーブルの初期データ準備
        PreparedStatement idGeneratorSetup = testDbConnection.prepareStatement(
                "insert into id_generator values (?, ?)");
        idGeneratorSetup.setString(1, "99");
        idGeneratorSetup.setInt(2, 100);
        idGeneratorSetup.execute();

        // メッセージの登録
        PreparedStatement message = testDbConnection.prepareStatement("insert into mail_message values (?, ?, ?)");
        message.setString(1, "001");
        message.setString(2, "ja");
        message.setString(3, "メール送信要求が {0} 件あります。");
        message.addBatch();
        message.setString(1, "002");
        message.setString(2, "ja");
        message.setString(3, "メール送信完了。メールリクエストID {0}");
        message.addBatch();
        message.setString(1, "ZZZ");
        message.setString(2, "en");
        message.setString(3, "メール送信失敗：メールリクエストID {0}");
        message.addBatch();
        message.setString(1, "ZZZ");
        message.setString(2, "ja");
        message.setString(3, "メール送信失敗：メールリクエストID {0}");
        message.addBatch();
        message.executeBatch();
    }

    /**
     * 指定されたテーブルのデータを削除する。
     *
     * @param tableNames テーブル一覧
     * @throws SQLException
     */
    private void cleaningTable(String... tableNames) throws SQLException {
        for (String tableName : tableNames) {
            PreparedStatement statement = testDbConnection.prepareStatement("truncate table " + tableName);
            statement.execute();
            statement.close();
        }
    }

    @After
    public void after() {
    }

    @AfterClass
    public static void afterClass() throws SQLException {
        if (testDbConnection != null) {
            testDbConnection.close();
        }
    }

    protected void mailRequest(final FreeTextMailContext mailContext) {
        SimpleDbTransactionManager manager = new SimpleDbTransactionManager();
        ConnectionFactory connectionFactory = SystemRepository.get("connectionFactory");
        TransactionFactory transactionFactory = SystemRepository.get("transactionFactory");
        manager.setConnectionFactory(connectionFactory);
        manager.setTransactionFactory(transactionFactory);
        new SimpleDbTransactionExecutor<Void>(manager) {
            @Override
            public Void execute(AppDbConnection connection) {
                MailRequester mailRequester = MailUtil.getMailRequester();
                mailRequester.requestToSend(mailContext);
                return null;
            }
        }.doTransaction();
    }

    protected void mailRequest(final TemplateMailContext mailContext) {
        SimpleDbTransactionManager manager = new SimpleDbTransactionManager();
        ConnectionFactory connectionFactory = SystemRepository.get("connectionFactory");
        TransactionFactory transactionFactory = SystemRepository.get("transactionFactory");
        manager.setConnectionFactory(connectionFactory);
        manager.setTransactionFactory(transactionFactory);
        new SimpleDbTransactionExecutor<Void>(manager) {
            @Override
            public Void execute(AppDbConnection connection) {
                MailRequester mailRequester = MailUtil.getMailRequester();
                mailRequester.requestToSend(mailContext);
                return null;
            }
        }.doTransaction();
    }

    protected static void assertLog(String message) {
        List<String> log = OnMemoryLogWriter.getMessages("writer.mail");
        System.out.println("log = " + log);
        boolean writeLog = false;
        for (String logMessage : log) {
            String str = logMessage.replaceAll("\\r|\\n", "");
            if (str.indexOf(message) >= 0) {
                writeLog = true;
            }
        }
        assertThat("ログが出力されていること", writeLog, is(true));
    }
}



