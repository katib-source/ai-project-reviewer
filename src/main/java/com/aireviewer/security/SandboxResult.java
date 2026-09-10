package com.aireviewer.security;

/**
 * Résultat d'une exécution dans le sandbox.
 *
 * @param exitCode  code de sortie du processus (0 = succès, -1 = erreur interne)
 * @param stdout    sortie standard
 * @param stderr    sortie d'erreur
 * @param timedOut  vrai si le timeout a été dépassé
 */
public record SandboxResult(
    int exitCode,
    String stdout,
    String stderr,
    boolean timedOut
) {
    public boolean isSuccess() {
        return exitCode == 0 && !timedOut;
    }
}
