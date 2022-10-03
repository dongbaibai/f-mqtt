package com.fmqtt.cluster.akka;

import java.io.Serializable;

/**
 * register server message
 */
public class Registration implements Serializable {

    private static final long serialVersionUID = 3633326229201460468L;
    private String service;

    public Registration() {
    }

    public Registration(String service) {
        this.service = service;
    }

    public String getService() {
        return service;
    }

    public void setService(String name) {
        this.service = name;
    }

}
