package com.lms.platform.model;
import java.sql.Timestamp;
public class Course {
    public enum Status { DRAFT, PUBLISHED, ARCHIVED }
    public enum ContentType { FORMATION, DOCUMENT }
    private long id, authorId;
    private String bundleId, title, description, category, tags, level, duration;
    private String authorName, version, entryPoint, quizFile, deployedPath;
    private boolean quizEnabled;
    private int passThreshold, maxAttempts, questionsCount;
    private Status status;
    private ContentType contentType = ContentType.FORMATION;
    private Timestamp createdAt, publishedAt;
    public Course(){}
    public long getId(){return id;}public void setId(long v){id=v;}
    public long getAuthorId(){return authorId;}public void setAuthorId(long v){authorId=v;}
    public String getBundleId(){return bundleId;}public void setBundleId(String v){bundleId=v;}
    public String getTitle(){return title;}public void setTitle(String v){title=v;}
    public String getDescription(){return description;}public void setDescription(String v){description=v;}
    public String getCategory(){return category;}public void setCategory(String v){category=v;}
    public String getTags(){return tags;}public void setTags(String v){tags=v;}
    public String getLevel(){return level;}public void setLevel(String v){level=v;}
    public String getDuration(){return duration;}public void setDuration(String v){duration=v;}
    public String getAuthorName(){return authorName;}public void setAuthorName(String v){authorName=v;}
    public String getVersion(){return version;}public void setVersion(String v){version=v;}
    public String getEntryPoint(){return entryPoint;}public void setEntryPoint(String v){entryPoint=v;}
    public boolean isQuizEnabled(){return quizEnabled;}public void setQuizEnabled(boolean v){quizEnabled=v;}
    public String getQuizFile(){return quizFile;}public void setQuizFile(String v){quizFile=v;}
    public int getPassThreshold(){return passThreshold;}public void setPassThreshold(int v){passThreshold=v;}
    public int getMaxAttempts(){return maxAttempts;}public void setMaxAttempts(int v){maxAttempts=v;}
    public int getQuestionsCount(){return questionsCount;}public void setQuestionsCount(int v){questionsCount=v;}
    public Status getStatus(){return status;}public void setStatus(Status v){status=v;}
    public ContentType getContentType(){return contentType;}public void setContentType(ContentType v){contentType=v;}
    public String getDeployedPath(){return deployedPath;}public void setDeployedPath(String v){deployedPath=v;}
    public Timestamp getCreatedAt(){return createdAt;}public void setCreatedAt(Timestamp v){createdAt=v;}
    public Timestamp getPublishedAt(){return publishedAt;}public void setPublishedAt(Timestamp v){publishedAt=v;}
}
