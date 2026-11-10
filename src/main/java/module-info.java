module it.infog.psiche {
    requires javafx.controls;
    requires javafx.web;
    requires java.desktop;
    requires java.logging;
    requires jdk.xml.dom;
    requires org.commonmark;
    requires org.commonmark.ext.footnotes;
    requires org.commonmark.ext.gfm.strikethrough;
    requires openhtmltopdf.core;
    requires openhtmltopdf.pdfbox;
    requires org.apache.fontbox;
    requires org.apache.lucene.analysis.common;
    requires org.apache.lucene.core;
    requires org.apache.pdfbox;
    requires org.apache.pdfbox.io;
    requires org.eclipse.jgit;
    requires org.fxmisc.flowless;
    requires org.fxmisc.richtext;
    requires org.reactfx;
    requires org.slf4j;

    exports it.infog.psiche.ui to javafx.graphics;
}
