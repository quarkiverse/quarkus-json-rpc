package io.quarkiverse.jsonrpc.sample.model;

import java.time.LocalDateTime;

public class Pojo {
    private String name;
    private String surname;
    private LocalDateTime time;
    private String thread;
    private Pojo2 pojo2;

    public Pojo() {
    }

    public Pojo(String name, String surname, LocalDateTime time, String thread, Pojo2 pojo2) {
        this.name = name;
        this.surname = surname;
        this.time = time;
        this.thread = thread;
        this.pojo2 = pojo2;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSurname() {
        return surname;
    }

    public void setSurname(String surname) {
        this.surname = surname;
    }

    public LocalDateTime getTime() {
        return time;
    }

    public void setTime(LocalDateTime time) {
        this.time = time;
    }

    public String getThread() {
        return thread;
    }

    public void setThread(String thread) {
        this.thread = thread;
    }

    public Pojo2 getPojo2() {
        return pojo2;
    }

    public void setPojo2(Pojo2 pojo2) {
        this.pojo2 = pojo2;
    }
}
