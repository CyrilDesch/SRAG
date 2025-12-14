# Serveur MCP pour SRAG

Ce document explique comment construire et utiliser le serveur MCP (Model Context Protocol) pour intégrer SRAG avec Claude Code.

## Architecture

Le serveur MCP est un point d'entrée dédié (`MCPMain`) qui :

- Communique via stdin/stdout (protocole MCP)
- Évite les logs polluants de sbt en utilisant un JAR assemblé
- Reste en attente entre les requêtes
- S'intègre facilement avec Claude Code

## Construction du JAR

### Prérequis

Avant de builder le JAR, assurez-vous que :

- Les services Docker sont démarrés : `docker compose up -d`
- Le projet compile : `sbt compile`

### Étape 1 : Assembler le JAR

```bash
sbt 'srag-infrastructure/assembly'
```

Cette commande crée un fat JAR contenant toutes les dépendances dans :

```
srag-infrastructure/target/scala-3.7.3/srag-infrastructure-assembly-*.jar
```

**⏱️ Temps de build** : ~1-2 minutes la première fois, puis ~30 secondes pour les builds suivants.

**Note** : Le nom exact du JAR contient le numéro de version dynamique (via sbt-dynver).
Le script `mcpServer.sh` détecte automatiquement le JAR assemblé.

### Étape 2 : Vérifier la création du JAR

```bash
ls -lh srag-infrastructure/target/scala-3.7.3/srag-infrastructure-assembly-*.jar
```

Vous devriez voir un fichier de ~100-150 MB.

## Utilisation

### Lancement du serveur MCP

Le script `scripts/mcpServer.sh` lance le serveur MCP :

```bash
./scripts/mcpServer.sh
```

**Caractéristiques importantes :**

- Les logs sont redirigés vers `/tmp/srag-mcp.log` pour ne pas polluer stdin/stdout
- Le serveur reste actif et attend les requêtes MCP
- Utilise le fichier de configuration logback existant

### Consulter les logs

```bash
tail -f /tmp/srag-mcp.log
```

## Intégration avec Claude Code

Pour utiliser le serveur MCP avec Claude Code, ajoutez cette configuration dans les paramètres de Claude :

### Configuration MCP (Claude Desktop ou VS Code)

**Emplacement du fichier de configuration :**

- **VS Code** : `.vscode/mcp.json` dans votre workspace
- **Claude Desktop** :
  - macOS: `~/Library/Application Support/Claude/claude_desktop_config.json`
  - Windows: `%APPDATA%\Claude\claude_desktop_config.json`
  - Linux: `~/.config/Claude/claude_desktop_config.json`

**Fichier de configuration :**

Un exemple de configuration est disponible dans `docs/mcp-config.example.json`.

```json
{
  "mcpServers": {
    "srag": {
      "command": "/absolute/path/to/SRAG/scripts/mcpServer.sh",
      "args": [],
      "env": {}
    }
  }
}
```

⚠️ **Important** : Utilisez un **chemin absolu** vers le script `mcpServer.sh`.

### Exemple pratique

```bash
# 1. Trouvez le chemin absolu du script
cd /path/to/SRAG
pwd  # Copier ce chemin

# 2. Créer la configuration pour Claude
# Linux/macOS:
mkdir -p ~/.config/Claude
cat > ~/.config/Claude/claude_desktop_config.json << EOF
{
  "mcpServers": {
    "srag": {
      "command": "$(pwd)/scripts/mcpServer.sh",
      "args": [],
      "env": {}
    }
  }
}
EOF

# 3. Redémarrer Claude Desktop pour appliquer les changements
```

## Dépannage

### Le serveur ne démarre pas

1. Vérifiez que le JAR existe :

   ```bash
   ls -lh srag-infrastructure/target/scala-3.7.3/srag-infrastructure-assembly-*.jar
   ```

2. Consultez les logs :

   ```bash
   tail -50 /tmp/srag-mcp.log
   ```

3. Vérifiez que les services externes sont actifs (PostgreSQL, Redis, Qdrant, etc.)

### Claude ne trouve pas le serveur

1. Vérifiez le chemin absolu dans la configuration MCP
2. Vérifiez que le script est exécutable :
   ```bash
   chmod +x scripts/mcpServer.sh
   ```
3. Testez manuellement :
   ```bash
   ./scripts/mcpServer.sh
   ```

### Logs pollués

Si vous voyez des logs dans la sortie standard :

- Vérifiez que la redirection stderr fonctionne dans le script
- Ajustez le niveau de log dans `logback.xml`