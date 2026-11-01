
package com.noteshub.app;

import com.google.firebase.firestore.Exclude;
import com.google.firebase.firestore.IgnoreExtraProperties;

@IgnoreExtraProperties
public class Note {

    private String id;
    private String title;
    private String subject;
    private String semester;
    private String description;
    private String fileUrl;
    private String fileName;
    private String storagePath;
    private String uploaderId;
    private String uploaderName;
    private long timestamp;
    private long updatedAt;

    public Note() {
    }

    public Note(String title, String subject, String semester,
                String description, String fileUrl, String fileName,
                String storagePath, String uploaderId,
                String uploaderName, long timestamp) {

        this.title = title;
        this.subject = subject;
        this.semester = semester;
        this.description = description;
        this.fileUrl = fileUrl;
        this.fileName = fileName;
        this.storagePath = storagePath;
        this.uploaderId = uploaderId;
        this.uploaderName = uploaderName;
        this.timestamp = timestamp;
        this.updatedAt = 0;
    }

    @Exclude
    public String getId() { return id; }

    @Exclude
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getSemester() { return semester; }
    public void setSemester(String semester) { this.semester = semester; }

    public String getDescription() { return description; }
    public void setDescription(String description) {
        this.description = description;
    }

    public String getFileUrl() { return fileUrl; }
    public void setFileUrl(String fileUrl) { this.fileUrl = fileUrl; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getStoragePath() { return storagePath; }
    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
    }

    public String getUploaderId() { return uploaderId; }
    public void setUploaderId(String uploaderId) {
        this.uploaderId = uploaderId;
    }

    public String getUploaderName() { return uploaderName; }
    public void setUploaderName(String uploaderName) {
        this.uploaderName = uploaderName;
    }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }
}
