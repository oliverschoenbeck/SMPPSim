package com.seleniumsoftware.SMPPSim.tools;

import org.json.JSONObject;

import java.io.IOException;

public class ChuckNorris {

    public static String Quote() {
        try {
            JSONObject json = JsonReader.readJsonFromUrl("https://api.chucknorris.io/jokes/random");
            return (String) json.get("value");
        } catch (Exception e){
            return "No Quote today :(";
        }

    }
}
