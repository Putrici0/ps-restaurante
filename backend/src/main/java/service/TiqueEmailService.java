package service;

import jakarta.activation.DataHandler;
import jakarta.activation.DataSource;
import jakarta.mail.*;
import jakarta.mail.internet.*;
import jakarta.mail.util.ByteArrayDataSource;

import java.util.Properties;

public class TiqueEmailService {

    public void enviarTique(String destino, String nombreArchivo, byte[] pdfBytes) throws Exception {

        String emailFrom = System.getProperty("EMAIL_FROM", System.getenv("EMAIL_FROM"));
        String password = System.getProperty("EMAIL_PASSWORD", System.getenv("EMAIL_PASSWORD"));

        if (emailFrom == null) {
            throw new IllegalStateException("Falta la variable EMAIL_FROM");
        }

        if (password == null) {
            throw new IllegalStateException("Falta la variable EMAIL_PASSWORD");
        }

        Properties props = new Properties();

        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.starttls.required", "true");
        props.put("mail.smtp.host", "smtp.gmail.com");
        props.put("mail.smtp.port", "587");
        props.put("mail.smtp.ssl.protocols", "TLSv1.2 TLSv1.3");
        props.put("mail.smtp.ssl.checkserveridentity", "true");
        props.put("mail.smtp.ssl.trust", "smtp.gmail.com");

        Session session = Session.getInstance(props,
                new Authenticator() {
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(emailFrom, password);
                    }
                }
        );

        Message message = new MimeMessage(session);

        message.setFrom(new InternetAddress(emailFrom));
        message.setRecipients(
                Message.RecipientType.TO,
                InternetAddress.parse(destino)
        );

        message.setSubject("Tique de PS Restaurante");

        MimeBodyPart texto = new MimeBodyPart();

        texto.setText("Adjunto encontrarás tu tique.");

        MimeBodyPart adjunto = new MimeBodyPart();

        DataSource source = new ByteArrayDataSource(pdfBytes, "application/pdf");

        adjunto.setDataHandler(new DataHandler(source));
        adjunto.setFileName(nombreArchivo);

        Multipart multipart = new MimeMultipart();

        multipart.addBodyPart(texto);
        multipart.addBodyPart(adjunto);

        message.setContent(multipart);

        Transport.send(message);
    }
}
