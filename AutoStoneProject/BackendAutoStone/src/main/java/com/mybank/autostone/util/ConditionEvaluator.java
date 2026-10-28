package com.mybank.autostone.util;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses and evaluates Autosys-style condition expressions:
 *   s(JOB_A)                  -> true if JOB_A's latest run in this batch SUCCEEDED
 *   f(JOB_A)                  -> true if JOB_A's latest run FAILED
 *   d(JOB_A) / done(JOB_A)    -> true if JOB_A's latest run SUCCEEDED or FAILED (i.e. finished)
 *   s(JOB_A) AND f(JOB_B)     -> AND / OR / NOT, also &&, ||, !, &, |
 *   (s(JOB_A) OR s(JOB_B)) AND s(JOB_C)   -> parentheses for grouping
 *
 * Function letters are case-insensitive (s/S, f/F, d/D, done/DONE).
 * Job names inside the parens follow \w and - . characters.
 */
public final class ConditionEvaluator {

    private ConditionEvaluator() {}

    /** Outcome of a single job as seen by the evaluator - deliberately not tied to JobStatus so this class has no entity dependency. */
    public enum Outcome { SUCCESS, FAILED, PENDING /* running/waiting/unknown */ }

    private static final Pattern TOKEN = Pattern.compile(
            "\\s*(\\(|\\)|&&|\\|\\||&|\\||!|(?i:AND)|(?i:OR)|(?i:NOT)|(?i:done|d|s|f)\\s*\\([\\w.\\-]+\\)|[^\\s()]+)");

    /**
     * Validates syntax and returns the set of job names referenced in the expression.
     * Throws IllegalArgumentException with a human-readable message on bad syntax.
     */
    public static Set<String> extractReferencedJobNames(String expr) {
        Set<String> names = new LinkedHashSet<>();
        new Parser(tokenize(expr), names).parseTopLevel();
        return names;
    }

    /**
     * Evaluates the expression against a lookup that returns the outcome of a named job.
     * Unknown/never-seen job names resolve to PENDING (expression short-circuits to false where needed).
     */
    public static boolean evaluate(String expr, Function<String, Outcome> outcomeLookup) {
        Parser parser = new Parser(tokenize(expr), new LinkedHashSet<>());
        return parser.parseAndEval(outcomeLookup);
    }

    private static java.util.List<String> tokenize(String expr) {
        java.util.List<String> tokens = new java.util.ArrayList<>();
        Matcher m = TOKEN.matcher(expr);
        int last = 0;
        while (m.find()) {
            if (m.start() != last) {
                String skipped = expr.substring(last, m.start()).trim();
                if (!skipped.isEmpty()) {
                    throw new IllegalArgumentException("Unrecognized token near '" + skipped + "' in condition: " + expr);
                }
            }
            tokens.add(m.group(1).trim());
            last = m.end();
        }
        if (last != expr.length() && !expr.substring(last).isBlank()) {
            throw new IllegalArgumentException("Unrecognized trailing content in condition: " + expr);
        }
        return tokens;
    }

    private static final Pattern ATOM = Pattern.compile("(?i)^(done|d|s|f)\\s*\\(([\\w.\\-]+)\\)$");

    private static class Parser {
        private final java.util.List<String> tokens;
        private final Set<String> collectedNames;
        private int pos = 0;

        Parser(java.util.List<String> tokens, Set<String> collectedNames) {
            this.tokens = tokens;
            this.collectedNames = collectedNames;
        }

        void parseTopLevel() {
            if (tokens.isEmpty()) {
                throw new IllegalArgumentException("Empty condition expression");
            }
            parseOr(null);
            if (pos != tokens.size()) {
                throw new IllegalArgumentException("Unexpected token '" + peek() + "' in condition");
            }
        }

        boolean parseAndEval(Function<String, Outcome> lookup) {
            if (tokens.isEmpty()) {
                throw new IllegalArgumentException("Empty condition expression");
            }
            boolean result = parseOr(lookup);
            if (pos != tokens.size()) {
                throw new IllegalArgumentException("Unexpected token '" + peek() + "' in condition");
            }
            return result;
        }

        private String peek() {
            return pos < tokens.size() ? tokens.get(pos) : "<end>";
        }

        private String next() {
            if (pos >= tokens.size()) throw new IllegalArgumentException("Unexpected end of condition expression");
            return tokens.get(pos++);
        }

        // orExpr := andExpr (('OR'|'|'|'||') andExpr)*
        private boolean parseOr(Function<String, Outcome> lookup) {
            boolean result = parseAnd(lookup);
            while (pos < tokens.size() && isOr(peek())) {
                next();
                boolean rhs = parseAnd(lookup);
                result = result || rhs;
            }
            return result;
        }

        // andExpr := notExpr (('AND'|'&'|'&&') notExpr)*
        private boolean parseAnd(Function<String, Outcome> lookup) {
            boolean result = parseNot(lookup);
            while (pos < tokens.size() && isAnd(peek())) {
                next();
                boolean rhs = parseNot(lookup);
                result = result && rhs;
            }
            return result;
        }

        // notExpr := ('NOT'|'!')? atom
        private boolean parseNot(Function<String, Outcome> lookup) {
            if (pos < tokens.size() && isNot(peek())) {
                next();
                return !parseNot(lookup);
            }
            return parseAtom(lookup);
        }

        // atom := '(' orExpr ')' | FUNC '(' NAME ')'
        private boolean parseAtom(Function<String, Outcome> lookup) {
            String tok = next();
            if (tok.equals("(")) {
                boolean result = parseOr(lookup);
                if (pos >= tokens.size() || !next().equals(")")) {
                    throw new IllegalArgumentException("Missing closing ')' in condition");
                }
                return result;
            }
            Matcher m = ATOM.matcher(tok);
            if (!m.matches()) {
                throw new IllegalArgumentException("Expected s(JOB)/f(JOB)/d(JOB) or '(' but found '" + tok + "'");
            }
            String func = m.group(1).toLowerCase();
            String name = m.group(2);
            collectedNames.add(name);
            if (lookup == null) return true; // syntax-only validation pass

            Outcome outcome = lookup.apply(name);
            if (outcome == null) outcome = Outcome.PENDING;
            return switch (func) {
                case "s" -> outcome == Outcome.SUCCESS;
                case "f" -> outcome == Outcome.FAILED;
                default -> outcome != Outcome.PENDING; // d / done
            };
        }

        private boolean isOr(String t) { return t.equalsIgnoreCase("OR") || t.equals("|") || t.equals("||"); }
        private boolean isAnd(String t) { return t.equalsIgnoreCase("AND") || t.equals("&") || t.equals("&&"); }
        private boolean isNot(String t) { return t.equalsIgnoreCase("NOT") || t.equals("!"); }
    }
}