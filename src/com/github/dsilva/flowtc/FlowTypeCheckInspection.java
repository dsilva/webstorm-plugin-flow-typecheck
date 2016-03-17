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


/**
 * Plugin entry point
 */
public class FlowTypeCheckInspection extends LocalInspectionTool {
    private static final Logger log = Logger.getInstance(FlowTypeCheckInspection.class);
    static final ProblemDescriptor[] noProblems = new ProblemDescriptor[] {};
    static final Gson gson = new Gson();
    // TODO: make this configurable since /usr/local/bin isn't in $PATH by default on OS X
    static final String flowExecutablePath = "/usr/local/bin/flow";

    static class FlowMessagePart {
        public String descr;
        //public String level; // "error"
        public String path;
        public int line;
        public int endline;
        public int start;
        public int end;
    }

    static class FlowError {
        public ArrayList<FlowMessagePart> message;
        //public String kind; // "infer"
    }

    static class FlowResponse {
        public boolean passed;
        public ArrayList<FlowError> errors;
        //public String version;
    }


    @NotNull
    public ProblemDescriptor[] checkFile(@NotNull final PsiFile file, @NotNull final InspectionManager manager, final boolean isOnTheFly) {
        log.debug("Flow checkFile", file);

        final VirtualFile vfile = file.getVirtualFile();

        final String path = vfile.getCanonicalPath();
        if (path == null) {
            log.error("missing canonical path for " + file);
            return noProblems;
        }

        final String flowOutput = flowCheck(file.getProject(), path);
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

        final List<ProblemDescriptor> descriptors = new ArrayList<ProblemDescriptor>();

        for (final FlowError error: response.errors) {
            final ArrayList<FlowMessagePart> messageParts = error.message;
            if (messageParts == null || messageParts.size() == 0) {
                log.error("flow missing message in error " + error);
                continue;
            }

            final FlowMessagePart firstPart = messageParts.get(0);
            if (!path.equals(firstPart.path)) {
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

            for (final FlowMessagePart part: error.message) {
                if (part.path.isEmpty()) {
                    continue;
                }
                if (!path.equals(part.path)) {
                    continue;
                }

                final int lineStartOffset = document.getLineStartOffset(part.line - 1);
                final int lineEndOffset = document.getLineStartOffset(part.endline - 1);

                final ProblemDescriptor problemDescriptor = manager.createProblemDescriptor(
                        file,
                        TextRange.create(lineStartOffset + part.start - 1, lineEndOffset + part.end),
                        errorMessage,
                        ProblemHighlightType.ERROR,
                        true);

                descriptors.add(problemDescriptor);
            }
        }
        return descriptors.toArray(new ProblemDescriptor[descriptors.size()]);
    }

    @NotNull
    static String flowCheck(@NotNull final Project project, @NotNull final String path) {
        final String canonicalPath = project.getBaseDir().getCanonicalPath();
        if (canonicalPath == null) {
            log.error("flowCheck: missing canonical path for project");
            return "";
        }

        final File workingDir = new File(canonicalPath);
        log.debug("flowCheck working directory", canonicalPath);

        final String[] cmd = new String[] {flowExecutablePath, "--show-all-errors", "--json", path};

        try {
            final Process process = Runtime.getRuntime().exec(
                    cmd, null, workingDir);

            final StringBuilder outString = new StringBuilder();
            final Thread outThread = readStream(outString, process.getInputStream());

            final StringBuilder errString = new StringBuilder();
            final Thread errorThread = readStream(errString, process.getErrorStream());

            outThread.start();
            errorThread.start();
            outThread.join();
            errorThread.join();

            final int exitCode = process.waitFor();
            log.debug("flow exited with code ", exitCode);

            return outString.toString();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "";
    }

    static Thread readStream(@NotNull final StringBuilder outString, @NotNull final InputStream inputStream) {
        return new Thread(new Runnable() {
            @Override
            public void run() {
                final BufferedReader outStream = new BufferedReader(
                        new InputStreamReader(inputStream));
                try {
                    String line;
                    try {
                        while ((line = outStream.readLine()) != null) {
                            outString.append(line).append("\n");
                        }
                    } finally {
                        outStream.close();
                    }
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        });
    }
}
