package com.github.dsilva.flowtc;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class ExternalAnnotator extends com.intellij.lang.annotation.ExternalAnnotator<ExternalAnnotator.CollectedInfo, Collection<Error>> {
    //static class Ignore {}
    //private static final Ignore ignore = new Ignore();

    static class CollectedInfo {
        final @NotNull Document document;
        final @NotNull PsiFile file;
        CollectedInfo(final @NotNull Document document, final @NotNull PsiFile file) {
            this.document = document;
            this.file = file;
        }
    }
    private static final Logger log = Logger.getInstance(ExternalAnnotator.class);

    public CollectedInfo collectInformation(@NotNull PsiFile file) {
        final VirtualFile vfile = file.getVirtualFile();
        if (vfile == null) {
            log.info("Missing vfile for " + file);
            return null;
        }

        // collect the document here because doAnnotate has no read access to the file document manager
        final Document document = FileDocumentManager.getInstance().getDocument(vfile);

        if (document == null) {
            log.info("Missing document");
            return null;
        }

        return new CollectedInfo(document, file);
    }

    public CollectedInfo collectInformation(@NotNull PsiFile file, @NotNull Editor editor, boolean hasErrors) {
        return collectInformation(file);
    }

    // doAnnotate can perform long-running operations like waiting for a Flow server to start
    // See the comments in:
    //  https://upsource.jetbrains.com/idea-ce/file/HEAD/platform/analysis-api/src/com/intellij/lang/annotation/ExternalAnnotator.java
    public Collection<Error> doAnnotate(CollectedInfo collectedInfo) {
        log.info("running Flow external annotator for " + collectedInfo);
        return TypeCheck.errors(collectedInfo.file, collectedInfo.document);
    }

    public void apply(@NotNull final PsiFile file, final Collection<Error> annotationResult, @NotNull final AnnotationHolder holder) {
        log.info("applying Flow external annotator results for " + file);

        for (final Error error: annotationResult) {
            holder.createErrorAnnotation(error.range(), error.message());
        }
    }
}
