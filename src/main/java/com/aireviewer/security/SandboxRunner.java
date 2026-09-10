package com.aireviewer.security;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * Contrat d'exécution d'une commande dans un environnement isolé.
 *
 * Les autres packages (analysis, application) ne connaissent QUE cette interface.
 * Ils ne savent pas que Docker existe derrière.
 */
public interface SandboxRunner {

    /**
     * Exécute une commande dans un sandbox isolé.
     *
     * @param projectDir répertoire du projet à monter en lecture seule
     * @param command    commande à exécuter (ex. ["ls", "-la", "/projet"])
     * @param timeout    durée maximale d'exécution
     * @return le résultat de l'exécution
     */
    SandboxResult run(Path projectDir, List<String> command, Duration timeout);
}
