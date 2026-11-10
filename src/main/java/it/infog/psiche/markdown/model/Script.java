package it.infog.psiche.markdown.model;

import org.commonmark.node.CustomNode;
import org.commonmark.node.Delimited;

/**
 * Text set below or above the line, as the 2 of H~2~O or X^2^: one sign on each side, and no space inside, so a sign
 * written in the prose, as in {@code ~ 30 people}, stays a sign.
 */
public abstract sealed class Script extends CustomNode implements Delimited permits Subscript, Superscript {

    private final String sign;

    Script(char sign) {

        this.sign = String.valueOf(sign);
    }

    @Override
    public String getOpeningDelimiter() {

        return sign;
    }

    @Override
    public String getClosingDelimiter() {

        return sign;
    }
}
