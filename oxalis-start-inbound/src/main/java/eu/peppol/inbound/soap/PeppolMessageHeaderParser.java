package eu.peppol.inbound.soap;

import com.sun.xml.ws.api.message.HeaderList;
import eu.peppol.start.identifier.*;

import javax.xml.namespace.QName;

import static eu.peppol.start.identifier.IdentifierName.*;

import javax.xml.stream.*;
import java.io.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.ws.WebServiceContext;
import eu.peppol.inbound.util.XmlReaderToWriter;
import java.security.cert.*;
import javax.security.auth.x500.*;
import java.util.Set;
import sun.misc.BASE64Encoder;
import javax.security.auth.Subject;
import com.sun.xml.wss.SubjectAccessor;

/**
 * Parses the PEPPOL SOAP Headers into a simple structure, which contains the meta data for the
 * message being transferred.
 * 
 * @author Steinar Overbeck Cook
 *         <p/>
 *         Created by
 *         User: steinar
 *         Date: 04.12.11
 *         Time: 19:47
 */
public class PeppolMessageHeaderParser {

    private static final String NAMESPACE_TRANSPORT_IDS = "http://busdox.org/transport/identifiers/1.0/";
    private static final Logger log = LoggerFactory.getLogger(PeppolMessageHeaderParser.class);

    public static PeppolMessageHeader parseSoapHeaders(HeaderList headerList) {
        PeppolMessageHeader m = new PeppolMessageHeader();

        m.setMessageId(new MessageId(getContent(headerList, MESSAGE_ID)));
        m.setChannelId(new ChannelId(getContent(headerList, CHANNEL_ID)));
        m.setRecipientId(new ParticipantId(getContent(headerList, RECIPIENT_ID.stringValue())));
        m.setSenderId(new ParticipantId(getContent(headerList, SENDER_ID.stringValue())));
        m.setDocumentTypeIdentifier(PeppolDocumentTypeId.valueOf(getContent(headerList, DOCUMENT_ID)));
        m.setPeppolProcessTypeId(PeppolProcessTypeId.valueOf(getContent(headerList, PROCESS_ID)));

        return m;
    }

    public static void parseSecurity(WebServiceContext context, PeppolMessageHeader header)
    {
        log.info("Reading security info");
        try {
            header.setSenderCert("");
            header.setSamlAssertionXml("");

            Subject subject = SubjectAccessor.getRequesterSubject(context);
            if (subject != null) {
                log.info("Security subject found");

                Set<X509Certificate> certs = subject.getPublicCredentials(X509Certificate.class);
                for (X509Certificate c : certs) {
                    X500Principal p = c.getSubjectX500Principal();
                    log.info("Certificate found with subject: " + p.getName(p.RFC2253));
                    header.SetSenderSubject(p.getName(p.RFC2253));
                    byte[] enc = c.getEncoded();
                    StringBuilder sb = new StringBuilder();
                    sb.append("-----BEGIN CERTIFICATE-----\n");
                    sb.append(new BASE64Encoder().encode(enc));
                    sb.append("\n-----END CERTIFICATE-----\n");
                    header.setSenderCert(sb.toString());
                    break;
                }

                Set<XMLStreamReader> xrs = subject.getPublicCredentials(XMLStreamReader.class);
                for (XMLStreamReader xr : xrs) {
                    log.info("Saml assertion found");
                    XMLOutputFactory xf = XMLOutputFactory.newInstance();
                    xf.setProperty("javax.xml.stream.isRepairingNamespaces", new Boolean(true));
                    ByteArrayOutputStream os = new ByteArrayOutputStream();
                    XMLStreamWriter xw = xf.createXMLStreamWriter(os, "UTF-8");
                    xw.writeStartDocument("UTF-8", "1.0");
                    XmlReaderToWriter.writeAll(xr, xw);
                    xw.close();
                    os.flush();
                    header.setSamlAssertionXml(os.toString("UTF-8"));
                    os.close();
                    break;
                }
            }
            else {
                log.error("No subject found");
            }
        }
        catch (CertificateEncodingException e) {
            log.error("Certificate encoding exception: " + e.getMessage());
        }
        catch (XMLStreamException e) {
            log.error("XML Stream exception: " + e.getMessage());
        }
        catch (UnsupportedEncodingException e) {
            log.error("Unsupported encoding: " + e.getMessage());
        }
        catch (IOException e) {
            log.error("IO Exception: " + e.getMessage());
        }
        catch (com.sun.xml.wss.XWSSecurityException e) {
            log.error("Security exception: " + e.getMessage());
        }
        catch (Exception e) {
            log.error("Exception: " + e.getMessage());
        }
    }

    private static QName getQName(IdentifierName identifierName) {
        return getQName(identifierName.stringValue());
    }

    private static QName getQName(String headerName) {
        return new QName(NAMESPACE_TRANSPORT_IDS, headerName);
    }

    private static String getContent(HeaderList headerList, IdentifierName identifierName) {
        return headerList.get(getQName(identifierName), false).getStringContent();
    }

    private static String getContent(HeaderList headerList, String identifierName) {
        return headerList.get(getQName(identifierName), false).getStringContent();
    }
}
