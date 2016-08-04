package com.github.dsilva.flowtc;

// already bundled with IntelliJ IDEA and WebStorm
import com.google.gson.Gson;

import com.intellij.codeInspection.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;


/**
 * Plugin entry point
 */
public class FlowTypeCheckInspection extends LocalInspectionTool {
    private static final Logger log = Logger.getInstance(FlowTypeCheckInspection.class);
    private static final ProblemDescriptor[] noProblems = new ProblemDescriptor[] {};
    private static final Gson gson = new Gson();

    private static class FlowMessagePart {
        String descr;
        //public String level; // "error"
        String path;
        int line;
        int endline;
        int start;
        int end;
    }

    private static class FlowError {
        ArrayList<FlowMessagePart> message;
        //public String kind; // "infer"
    }

    private static class FlowResponse {
        boolean passed;
        ArrayList<FlowError> errors;
        //public String version;
    }


    @NotNull
    public ProblemDescriptor[] checkFile(@NotNull final PsiFile file, @NotNull final InspectionManager manager, final boolean isOnTheFly) {
        log.debug("Flow checkFile", file);

        final VirtualFile vfile = file.getVirtualFile();
        if (vfile == null) {
            log.error("missing vfile for " + file);
            return noProblems;
        }

        final VirtualFile vparent = vfile.getParent();
        if (vparent == null) {
            log.error("missing vparent for " + file);
            return noProblems;
        }

        final String path = vfile.getCanonicalPath();
        if (path == null) {
            log.error("missing canonical path for " + file);
            return noProblems;
        }

        final String dir = vparent.getCanonicalPath();
        if (dir == null) {
            log.error("missing canonical dir for " + file);
            return noProblems;
        }

        vfile.getParent().getCanonicalPath();

        final String flowOutput = flowCheck(file.getProject(), dir, file.getText());
        log.debug("flow output", flowOutput);

        if (flowOutput.isEmpty()) {
            log.error("flow output was empty");
            return noProblems;
        }

        final FlowResponse response = gson.fromJson(flowOutput, FlowResponse.class);

        if (response == null) {
            log.error("could not parse flow output " + flowOutput);
            return noProblems;
        }

        if (response.passed) {
            log.info("flow passed");
            return noProblems;
        }
        if (response.errors == null) {
            log.error("flow failed, but shows no errors");
            return noProblems;
        }

        final Document document = FileDocumentManager.getInstance().getDocument(vfile);

        if (document == null) {
            log.error("missing document");
            return noProblems;
        }

        final List<ProblemDescriptor> descriptors = new ArrayList<>();

        for (final FlowError error: response.errors) {
            final ArrayList<FlowMessagePart> messageParts = error.message;
            if (messageParts == null || messageParts.size() == 0) {
                log.error("flow missing message in error " + error);
                continue;
            }

            final FlowMessagePart firstPart = messageParts.get(0);
            if (!path.equals(firstPart.path) && !"-".equals(firstPart.path)) {
                log.info("skip error because first message part path " + firstPart.path + " does not match file path " + path);
                continue;
            }

            final StringBuilder errorMessageBuilder = new StringBuilder(firstPart.descr);

            for (int i = 1; i < messageParts.size(); i++) {
                final FlowMessagePart messagePart = messageParts.get(i);
                if (messagePart.path == null || messagePart.path.isEmpty()) {
                    errorMessageBuilder.append(". ");
                } else {
                    errorMessageBuilder.append(" ");
                }
                errorMessageBuilder.append(messagePart.descr);
            }

            final String errorMessage = errorMessageBuilder.toString();
            log.info("Flow found error: " + errorMessage);

            for (final FlowMessagePart part: error.message) {
                if (part.path.isEmpty()) {
                    // skip part of error message that has no file/line reference
                    continue;
                }
                if (!path.equals(part.path) && !"-".equals(part.path)) {
                    // skip part of error message that refers to content in another file
                    continue;
                }

                final int lineStartOffset = document.getLineStartOffset(part.line - 1);
                final int lineEndOffset = document.getLineStartOffset(part.endline - 1);

                final ProblemDescriptor problemDescriptor = manager.createProblemDescriptor(
                        file,
                        TextRange.create(lineStartOffset + part.start - 1, lineEndOffset + part.end),
                        errorMessage,
                        ProblemHighlightType.ERROR,
                        isOnTheFly);

                log.info("Flow error for file " + file + " at " + part.line + ":" + part.start + " to " + part.endline + ":" + part.end + " range " + TextRange.create(lineStartOffset + part.start - 1, lineEndOffset + part.end));
                descriptors.add(problemDescriptor);
            }
        }
        if (descriptors.isEmpty()) {
            return noProblems;
        } else {
            log.info("Flow inspector found problem descriptors " + descriptors);
            return descriptors.toArray(new ProblemDescriptor[descriptors.size()]);
        }
    }

    private static final Random random = new Random();

    @NotNull
    private static String flowCheck(@NotNull final Project project, @NotNull final String dir, String text) {
        final String canonicalPath = project.getBaseDir().getCanonicalPath();
        if (canonicalPath == null) {
            log.error("flowCheck: missing canonical path for project");
            return "";
        }

        final File workingDir = new File(canonicalPath);
        log.debug("flowCheck working directory", canonicalPath);

        final String[] cmd = new String[] {Settings.readPath(),
                "check-contents",
                "--show-all-errors", "--json"};

        try {
            final Process process = Runtime.getRuntime().exec(
                    cmd, null, workingDir);

            final StringBuilder outString = new StringBuilder();
            final Thread outThread = readStream(outString, process.getInputStream());

            final StringBuilder errString = new StringBuilder();
            final Thread errorThread = readStream(errString, process.getErrorStream());

            outThread.start();
            errorThread.start();

            // Send the file text through stdin instead of letting Flow file it in the filesystem
            // because IntelliJ might not have written the latest changes yet.
            final OutputStream os = process.getOutputStream();
            os.write(text.getBytes());
            os.close();

            outThread.join();
            errorThread.join();

            final int exitCode = process.waitFor();
            log.debug("flow exited with code ", exitCode);

            return outString.toString();
        } catch (InterruptedException | IOException e) {
            e.printStackTrace();
        }
        return "";
    }

    private static Thread readStream(@NotNull final StringBuilder outString, @NotNull final InputStream inputStream) {
        return new Thread(() -> {
            try {
                String line;
                try (BufferedReader outStream = new BufferedReader(
                        new InputStreamReader(inputStream))) {
                    while ((line = outStream.readLine()) != null) {
                        outString.append(line).append("\n");
                    }
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        });
    }
}
