package com.github.dsilva.flowtc;

import java.util.ArrayList;

// already bundled with IntelliJ IDEA and WebStorm
import com.google.gson.Gson;
import org.jetbrains.annotations.NotNull;

class Output {
    private static final Gson gson = new Gson();

    static class MessagePart {
        String descr;
        //public String level; // "error"
        String path;
        int line;
        int endline;
        int start;
        int end;
    }

    static class Error {
        ArrayList<MessagePart> message;
        //public String kind; // "infer"
    }

    static class Response {
        boolean passed;
        ArrayList<Error> errors;
        //public String version;
    }

    static @NotNull Response parse(@NotNull final String flowOutput) {
        return gson.fromJson(flowOutput, Response.class);
    }

}
