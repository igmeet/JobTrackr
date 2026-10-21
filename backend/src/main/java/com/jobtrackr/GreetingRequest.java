package com.jobtrackr;

import jakarta.validation.constraints.NotBlank;

public class GreetingRequest {
    @NotBlank(message = "Name is required")
    private String name;

    public String getName(){
        return name;
    }

    public void setName(String name){
       this.name = name;
    }
}


