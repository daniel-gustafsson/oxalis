package no.sendregning.oxalis;

import eu.peppol.outbound.client.DocumentSenderBuilder;
import eu.peppol.outbound.client.PeppolDocumentSender;
import eu.peppol.start.util.Configuration;
import eu.peppol.start.util.Daemon;
import eu.peppol.start.util.Time;

import java.io.File;
import java.net.URL;

/**
 * User: nigel Date: Oct 8, 2011 Time: 9:29:08 AM
 */
public class TestDaemon extends Daemon {

    protected void init() {
        setInitialDelay(new Time(1, Time.SECONDS));
        setAntallIterasjoner(1);
    }

    protected void run() throws Exception {
        Configuration configuration = Configuration.getInstance();

        File xmlInvoice = new File(configuration.getProperty("test.file"));
        String recipient = "9909:976098897";
        URL destination = new URL(configuration.getProperty("web.service.address"));

        PeppolDocumentSender documentSender = new DocumentSenderBuilder()
                .setKeystoreFile(new File(configuration.getProperty("keystore")))
                .setKeystorePassword(configuration.getProperty("keystore.password"))
                .build();

        documentSender.sendInvoice(xmlInvoice, recipient, recipient, destination);

        Log.info("Test message successfully dispatched");
    }
}
