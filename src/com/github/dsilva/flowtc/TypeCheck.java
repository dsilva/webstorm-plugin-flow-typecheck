package com.github.dsilva.flowtc;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

class TypeCheck {
    private static final Logger log = Logger.getInstance(TypeCheck.class);
    private static final Collection<Error> noProblems = Collections.emptyList();

    static @NotNull Collection<Error> errors(@NotNull final PsiFile file) {
        final VirtualFile vfile = file.getVirtualFile();
        if (vfile == null) {
            log.info("Missing vfile for " + file);
            return noProblems;
        }

        final Document document = FileDocumentManager.getInstance().getDocument(vfile);

        if (document == null) {
            log.info("Missing document");
            return noProblems;
        }
        return errors(file, document);
    }

    static @NotNull Collection<Error> errors(PsiFile file, Document document) {

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

        final String flowOutput = flowCheck(path, file.getText());
        log.debug("flow output", flowOutput);

        if (flowOutput.isEmpty()) {
            log.error("flow output was empty");
            return noProblems;
        }

        final Output.Response response = Output.parse(flowOutput);

        if (response.passed) {
            log.info("flow passed");
            return noProblems;
        }
        if (response.errors == null) {
            log.error("flow failed, but shows no errors");
            return noProblems;
        }

        final Collection<Error> errors = new ArrayList<>();

        for (final Output.Error error: response.errors) {
            final ArrayList<Output.MessagePart> messageParts = error.message;
            if (messageParts == null || messageParts.size() == 0) {
                log.error("flow missing message in error " + error);
                continue;
            }

            final Output.MessagePart firstPart = messageParts.get(0);
            if (!path.equals(firstPart.path) && !"-".equals(firstPart.path)) {
                log.info("skip error because first message part path " + firstPart.path + " does not match file path " + path);
                continue;
            }

            final StringBuilder errorMessageBuilder = new StringBuilder(firstPart.descr);

            for (int i = 1; i < messageParts.size(); i++) {
                final Output.MessagePart messagePart = messageParts.get(i);
                if (messagePart.path == null || messagePart.path.isEmpty()) {
                    errorMessageBuilder.append(": ");
                } else {
                    errorMessageBuilder.append(" ");
                }
                errorMessageBuilder.append(messagePart.descr);
            }

            final String errorMessage = errorMessageBuilder.toString();
            log.info("Flow found error: " + errorMessage);

            for (final Output.MessagePart part: error.message) {
                if (part.path.isEmpty()) {
                    // skip part of error message that has no file/line reference
                    continue;
                }
                if (!path.equals(part.path) && !"-".equals(part.path)) {
                    // skip part of error message that refers to content in another file
                    continue;
                }

                final int lineStartOffset = document.getLineStartOffset(remapLine(part.line, document));
                final int lineEndOffset = document.getLineStartOffset(remapLine(part.endline, document));

                log.info("Flow error for file " + file + " at " + part.line + ":" + part.start + " to " + part.endline + ":" + part.end + " range " + TextRange.create(lineStartOffset + part.start - 1, lineEndOffset + part.end));

                errors.add(new Error(errorMessage, TextRange.create(lineStartOffset + part.start - 1, lineEndOffset + part.end)));
            }
        }

        if (errors.isEmpty()) {
            return noProblems;
        } else {
            log.info("Flow inspector found errors " + errors);
            return errors;
        }
    }

    private static int remapLine(int flowLine, Document document) {
        final int lineIndex = flowLine - 1;
        return Math.max(0, Math.min(lineIndex, document.getLineCount() - 1));
    }

    @NotNull
    private static String flowCheck(@NotNull final String filePath, @NotNull final String text) {

        final File file = new File(filePath);

        final File workingDir = file.getParentFile();
        log.debug("flowCheck working directory", workingDir);

        final String[] cmd = new String[] {Settings.readPath(),
                "check-contents",
                "--show-all-errors", "--json",
                file.getName()};

        final StringBuilder outString = new StringBuilder();
        final StringBuilder errString = new StringBuilder();

        final int exitCode = run(cmd, workingDir, outString, errString, text.getBytes());
        log.debug("flow exited with code ", exitCode);

        return outString.toString();
    }

    private static int run(@NotNull final String[] cmd, @NotNull final File dir, @NotNull final StringBuilder stdout, @NotNull final StringBuilder stderr, final byte[] stdin) {
        try {
            final Process process = Runtime.getRuntime().exec(
                    cmd, null, dir);

            final Thread outThread = readStreamThread(stdout, process.getInputStream());
            final Thread errThread = readStreamThread(stderr, process.getErrorStream());

            outThread.start();
            errThread.start();

            if (stdin != null && stdin.length > 0) {
                OutputStream os = process.getOutputStream();
                os.write(stdin);
                os.close();
            }

            outThread.join();
            errThread.join();

            return process.waitFor();
        } catch (InterruptedException | IOException e) {
            e.printStackTrace();
            return -100;
        }
    }

    private static Thread readStreamThread(@NotNull final StringBuilder outString, @NotNull final InputStream inputStream) {
        return new Thread(() -> readStream(outString, inputStream));
    }

    private static void readStream(@NotNull final StringBuilder outString, @NotNull final InputStream inputStream) {
        try {
            String line;
            try (BufferedReader outStream = new BufferedReader(new InputStreamReader(inputStream))) {
                while ((line = outStream.readLine()) != null) {
                    outString.append(line).append("\n");
                }
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}
