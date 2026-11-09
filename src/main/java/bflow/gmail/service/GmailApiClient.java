package bflow.gmail.service;

import com.google.api.client.auth.oauth2.BearerToken;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.MessagePartBody;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/** Typed Gmail API client. Requests only message metadata and attachment bytes. */
@Component
public class GmailApiClient {
    private static final String APPLICATION_NAME = "BFlow";
    private static final GsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final NetHttpTransport TRANSPORT = new NetHttpTransport();

    public List<String> listMessageIds(final String accessToken,
                                       final int lookbackDays,
                                       final int maxMessages) throws IOException {
        List<Message> messages = gmail(accessToken).users().messages().list("me")
                .setQ("newer_than:" + lookbackDays + "d")
                .setMaxResults((long) maxMessages).execute().getMessages();
        if (messages == null) return List.of();
        return messages.stream().map(Message::getId).toList();
    }

    public List<AttachmentRef> listAttachments(final String accessToken,
                                                final String messageId) throws IOException {
        Message message = gmail(accessToken).users().messages().get("me", messageId)
                .setFormat("full").execute();
        List<AttachmentRef> attachments = new ArrayList<>();
        collect(message.getPayload(), attachments);
        return attachments;
    }

    /** Returns only inline text bodies. Callers must use the text in memory and never persist it. */
    public List<TextPartRef> listTextParts(final String accessToken, final String messageId)
            throws IOException {
        Message message = gmail(accessToken).users().messages().get("me", messageId)
                .setFormat("full").execute();
        List<TextPartRef> textParts = new ArrayList<>();
        collectText(message.getPayload(), textParts);
        List<TextPartRef> plainTextParts = textParts.stream()
                .filter(part -> "text/plain".equalsIgnoreCase(part.mimeType()))
                .toList();
        return plainTextParts.isEmpty() ? textParts.stream()
                .filter(part -> "text/html".equalsIgnoreCase(part.mimeType()))
                .limit(1).toList() : plainTextParts.stream().limit(1).toList();
    }

    public byte[] downloadAttachment(final String accessToken,
                                     final String messageId,
                                     final String attachmentId) throws IOException {
        Gmail client = gmail(accessToken);
        String data;
        if (attachmentId.startsWith("inline:") || attachmentId.startsWith("body:")) {
            Message message = client.users().messages().get("me", messageId)
                    .setFormat("full").execute();
            data = findInlineData(message.getPayload(), attachmentId.substring(attachmentId.indexOf(':') + 1));
        } else {
            data = client.users().messages().attachments()
                    .get("me", messageId, attachmentId).execute().getData();
        }
        return Base64.getUrlDecoder().decode(data == null ? "" : data);
    }

    private Gmail gmail(final String accessToken) {
        Credential credential = new Credential.Builder(BearerToken.authorizationHeaderAccessMethod())
                .build();
        credential.setAccessToken(accessToken);
        return new Gmail.Builder(TRANSPORT, JSON_FACTORY, credential)
                .setApplicationName(APPLICATION_NAME).build();
    }

    private void collect(final MessagePart part, final List<AttachmentRef> result) {
        if (part == null) return;
        MessagePartBody body = part.getBody();
        String attachmentId = body == null ? null : body.getAttachmentId();
        if (attachmentId == null && body != null && body.getData() != null) {
            attachmentId = part.getPartId() == null ? null : "inline:" + part.getPartId();
        }
        String filename = part.getFilename();
        if (attachmentId != null && filename != null && !filename.isBlank()) {
            result.add(new AttachmentRef(attachmentId, part.getMimeType(),
                    body == null || body.getSize() == null ? 0 : body.getSize().longValue()));
        }
        if (part.getParts() != null) part.getParts().forEach(child -> collect(child, result));
    }

    private void collectText(final MessagePart part, final List<TextPartRef> result) {
        if (part == null) return;
        String mime = part.getMimeType();
        MessagePartBody body = part.getBody();
        if (("text/plain".equalsIgnoreCase(mime) || "text/html".equalsIgnoreCase(mime))
                && body != null && body.getData() != null) {
            byte[] bytes = Base64.getUrlDecoder().decode(body.getData());
            String partId = part.getPartId() == null ? "root" : part.getPartId();
            result.add(new TextPartRef("body:" + partId, mime, bytes.length,
                    new String(bytes, java.nio.charset.StandardCharsets.UTF_8)));
            java.util.Arrays.fill(bytes, (byte) 0);
        }
        if (part.getParts() != null) part.getParts().forEach(child -> collectText(child, result));
    }

    private String findInlineData(final MessagePart part, final String partId) {
        if (part == null) return "";
        if (partId.equals(part.getPartId())
                || ("root".equals(partId) && part.getPartId() == null)) {
            return part.getBody() == null ? "" : part.getBody().getData();
        }
        if (part.getParts() != null) {
            for (MessagePart child : part.getParts()) {
                String data = findInlineData(child, partId);
                if (!data.isBlank()) return data;
            }
        }
        return "";
    }

    public record AttachmentRef(String attachmentId, String mimeType, long size) { }
    public record TextPartRef(String partId, String mimeType, long size, String preview) { }
}
