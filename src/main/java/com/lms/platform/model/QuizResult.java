package com.lms.platform.model;
import java.sql.Timestamp;
public class QuizResult {
    private long id, userId, courseId;
    private String userName, courseTitle, moduleScores, detailedAnswers;
    private int attemptNumber, score, totalQuestions, correctAnswers, wrongAnswers, skipped, durationSeconds;
    private Timestamp submittedAt;
    public QuizResult(){}
    public long getId(){return id;}public void setId(long v){id=v;}
    public long getUserId(){return userId;}public void setUserId(long v){userId=v;}
    public long getCourseId(){return courseId;}public void setCourseId(long v){courseId=v;}
    public String getUserName(){return userName;}public void setUserName(String v){userName=v;}
    public String getCourseTitle(){return courseTitle;}public void setCourseTitle(String v){courseTitle=v;}
    public int getAttemptNumber(){return attemptNumber;}public void setAttemptNumber(int v){attemptNumber=v;}
    public int getScore(){return score;}public void setScore(int v){score=v;}
    public int getTotalQuestions(){return totalQuestions;}public void setTotalQuestions(int v){totalQuestions=v;}
    public int getCorrectAnswers(){return correctAnswers;}public void setCorrectAnswers(int v){correctAnswers=v;}
    public int getWrongAnswers(){return wrongAnswers;}public void setWrongAnswers(int v){wrongAnswers=v;}
    public int getSkipped(){return skipped;}public void setSkipped(int v){skipped=v;}
    public String getModuleScores(){return moduleScores;}public void setModuleScores(String v){moduleScores=v;}
    public String getDetailedAnswers(){return detailedAnswers;}public void setDetailedAnswers(String v){detailedAnswers=v;}
    public int getDurationSeconds(){return durationSeconds;}public void setDurationSeconds(int v){durationSeconds=v;}
    public Timestamp getSubmittedAt(){return submittedAt;}public void setSubmittedAt(Timestamp v){submittedAt=v;}
}
