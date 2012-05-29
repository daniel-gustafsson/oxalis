package eu.peppol.start.persistence;

import eu.peppol.start.identifier.IdentifierName;
import eu.peppol.start.identifier.PeppolMessageHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.util.Date;
import java.text.SimpleDateFormat;
import javax.xml.stream.*;

/**
 * @author $Author$ (of last change)
 *         Created by
 *         User: steinar
 *         Date: 28.11.11
 *         Time: 21:09
 */
public class SimpleMessageRepository implements MessageRepository {


    private static final Logger log = LoggerFactory.getLogger(SimpleMessageRepository.class);

    public void saveInboundMessage(String inboundMessageStore, PeppolMessageHeader peppolMessageHeader, Document document) {
        log.info("Default message handler " + peppolMessageHeader);

        File messageDirectory = prepareMessageDirectory(inboundMessageStore, peppolMessageHeader);


        try {
            String messageFileName = peppolMessageHeader.getMessageId().stringValue().replace(":", "_") + "_message.xml";
            File messageFullPath = new File(messageDirectory, messageFileName);
            saveDocument(document, messageFullPath);

            String certFileName = peppolMessageHeader.getMessageId().stringValue().replace(":", "_") + ".cer";
            File certFilePath = new File(messageDirectory, certFileName);
            saveSenderCert(peppolMessageHeader, certFilePath);

            String samlFileName = peppolMessageHeader.getMessageId().stringValue().replace(":", "_") + "_saml.xml";
            File samlFilePath = new File(messageDirectory, samlFileName);
            saveSamlAssertion(peppolMessageHeader, samlFilePath);

            String headerFileName = peppolMessageHeader.getMessageId().stringValue().replace(":", "_") + "_info.xml";
            File messageHeaderFilePath = new File(messageDirectory, headerFileName);
            saveHeader(peppolMessageHeader, messageHeaderFilePath, messageFullPath, certFilePath, samlFilePath);

        } catch (Exception e) {
            throw new IllegalStateException("Unable to persist message " + peppolMessageHeader.getMessageId(), e);
        }

    }


    File prepareMessageDirectory(String inboundMessageStore, PeppolMessageHeader peppolMessageHeader) {
        // Computes the full path of the directory in which message and routing data should be stored.
        File messageDirectory = computeDirectoryNameForInboundMessage(inboundMessageStore, peppolMessageHeader);
        if (!messageDirectory.exists()){
            if (!messageDirectory.mkdirs()){
                throw new IllegalStateException("Unable to create directory " + messageDirectory.toString());
            }
        }

        if (!messageDirectory.isDirectory() || !messageDirectory.canWrite()) {
            throw new IllegalStateException("Directory " + messageDirectory + " does not exist, or there is no access");
        }
        return messageDirectory;
    }


    void writeElement(XMLStreamWriter xw, String name, String value) throws XMLStreamException
    {
        xw.writeStartElement(name);
        xw.writeCharacters(value);
        xw.writeEndElement();
    }

    void saveHeader(PeppolMessageHeader peppolMessageHeader, File messageHeaderFilerPath, File messageFullPath, File certFullPath, File samlFullPath) {
        try {
            FileOutputStream fos = new FileOutputStream(messageHeaderFilerPath);
            XMLOutputFactory xf = XMLOutputFactory.newInstance();
            XMLStreamWriter xw = xf.createXMLStreamWriter(fos, "UTF-8");

            xw.writeStartDocument("UTF-8", "1.0");
            xw.writeStartElement("Info");

            // Formats the current time and date according to the ISO8601 standard.
            Date date = new Date();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
            writeElement(xw, "TimeStamp", sdf.format(date));

            writeElement(xw, "MessageFileName", messageFullPath.toString());
            writeElement(xw, "CertFileName", certFullPath.toString());
            writeElement(xw, "SamlFileName", samlFullPath.toString());
            writeElement(xw, IdentifierName.MESSAGE_ID.stringValue(), peppolMessageHeader.getMessageId().stringValue());
            writeElement(xw, IdentifierName.CHANNEL_ID.stringValue(), peppolMessageHeader.getChannelId().stringValue());
            writeElement(xw, IdentifierName.RECIPIENT_ID.stringValue(), peppolMessageHeader.getRecipientId().stringValue());
            writeElement(xw, IdentifierName.SENDER_ID.stringValue(), peppolMessageHeader.getSenderId().stringValue());
            writeElement(xw, IdentifierName.DOCUMENT_ID.stringValue(), peppolMessageHeader.getDocumentTypeIdentifier().toString());
            writeElement(xw, IdentifierName.PROCESS_ID.stringValue(), peppolMessageHeader.getPeppolProcessTypeId().toString());
            writeElement(xw, "SenderSubject", peppolMessageHeader.getSenderSubject());

            xw.writeEndElement();

            xw.close();
            fos.close();

            log.debug("File " + messageHeaderFilerPath + " written");

        } catch (FileNotFoundException e) {
            throw new IllegalStateException("Unable to create file " + messageHeaderFilerPath + "; " + e, e);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("Unable to create writer for " + messageHeaderFilerPath + "; " + e, e);
        } catch (XMLStreamException e) {
            throw new IllegalStateException("Unable to write xml to " + messageHeaderFilerPath + "; " + e, e);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to write to " + messageHeaderFilerPath + "; " + e, e);
        }
    }

    void saveSenderCert(PeppolMessageHeader header, File file)
    {
        try {
            FileOutputStream fos = new FileOutputStream(file);
            PrintWriter pw = new PrintWriter(new OutputStreamWriter(fos, "UTF-8"));
            pw.write(header.getSenderCert());
            pw.close();
            log.debug("File " + file + " written");
        } catch (FileNotFoundException e) {
            throw new IllegalStateException("Unable to create file " + file + "; " + e, e);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("Unable to create writer for " + file + "; " + e, e);
        }
    }

    void saveSamlAssertion(PeppolMessageHeader header, File file)
    {
        try {
            FileOutputStream fos = new FileOutputStream(file);
            PrintWriter pw = new PrintWriter(new OutputStreamWriter(fos, "UTF-8"));
            pw.write(header.getSamlAssertionXml());
            pw.close();
            log.debug("File " + file + " written");
        } catch (FileNotFoundException e) {
            throw new IllegalStateException("Unable to create file " + file + "; " + e, e);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("Unable to create writer for " + file + "; " + e, e);
        }
    }

    /**
     * Transforms an XML document into a String
     *
     * @param document the XML document to be transformed
     * @return the string holding the XML document
     */
    void saveDocument(Document document, File outputFile) {

        try {
            FileOutputStream fos = new FileOutputStream(outputFile);
            Writer writer = new BufferedWriter(new OutputStreamWriter(fos, "UTF-8"));

            StreamResult result = new StreamResult(writer);

            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer transformer;
            transformer = tf.newTransformer();
            transformer.transform(new DOMSource(document), result);
            fos.close();
            log.debug("File " + outputFile + " written");
        } catch (Exception e) {
            throw new SimpleMessageRepositoryException(outputFile, e);
        }

    }


    @Override
    public String toString() {
        return SimpleMessageRepository.class.getSimpleName();
    }


    /**
     * Computes the directory name for inbound messages.
     * <pre>
     *     /basedir/{recipientId}/{channelId}/{senderId}
     * </pre>
     * @param inboundMessageStore
     * @param peppolMessageHeader
     * @return
     */
    File computeDirectoryNameForInboundMessage(String inboundMessageStore, PeppolMessageHeader peppolMessageHeader) {
        if (peppolMessageHeader == null) {
            throw new IllegalArgumentException("peppolMessageHeader required");
        }

        String path = String.format("%s/%s",
                peppolMessageHeader.getRecipientId().stringValue().replace(":", "_"),
                peppolMessageHeader.getSenderId().stringValue().replace(":", "_"));
        return new File(inboundMessageStore, path);
    }

    /**
     * Computes the directory
     * @param outboundMessageStore
     * @param peppolMessageHeader
     * @return
     */
    File computeDirectoryNameForOutboundMessages(String outboundMessageStore, PeppolMessageHeader peppolMessageHeader) {
        if (peppolMessageHeader == null) {
            throw new IllegalArgumentException("peppolMessageHeader required");
        }

        String path = String.format("%s/%s",
                peppolMessageHeader.getSenderId().stringValue().replace(":", "_"),
                peppolMessageHeader.getRecipientId().stringValue().replace(":", "_"));
        return new File(outboundMessageStore, path);
    }
}
