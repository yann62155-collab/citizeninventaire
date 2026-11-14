# Citizen Inventaire 1.3.0 — NeoForge 21.1.215 / MC 1.21.1 / Java 21

Mod **serveur uniquement** (rien à installer chez les joueurs).

## Ce qu'il fait
- Photo de l'inventaire + coffre de l'Ender + expérience (niveau, barre, total) : à chaque déconnexion, toutes les 5 min, à la mort, à l'arrêt du serveur.
- Photos séparées **par personnage** (nom affiché, donc compatible CharacterSelect).
- Inventaire, coffre de l'Ender et expérience surveillés **séparément**.
- À la connexion, après 5 s (le temps que CharacterSelect charge le personnage) :
  si l'inventaire, le coffre de l'Ender ou l'XP est **à zéro** alors qu'il ne l'était pas à la déconnexion → la partie perdue est restaurée + message aux op.
- **Position** : enregistrée à la déconnexion ; si le joueur réapparaît à plus de 32 blocs (ou dans une autre dimension), il est replacé + message aux op (pas s'il était mort).
- Jamais de restauration auto après une mort, ni si le joueur a déjà des objets (pas de duplication).

## Commandes (op niveau 2, joueur connecté sur le bon personnage)
- `/inventaire liste <joueur>`
- `/inventaire restaurer <joueur> <numéro> [tout|inventaire|ender|xp]` (tout par défaut ; l'état actuel est sauvegardé avant : `avant-restauration`)
- `/inventaire sauver <joueur>`
- `/inventaire position <joueur>` (replace le joueur à sa dernière position de déconnexion)

## Fichiers
- Sauvegardes : `world/citizeninventaire/<uuid>/<personnage>/`
- Réglages : `world/serverconfig/citizeninventaire-server.toml`
  (autosaveMinutes, maxSnapshots, autoRestore, restoreDelayTicks, perCharacter, restorePosition, positionTolerance)

## Compiler
Les fichiers gradlew sont inclus. Dans le dossier :
`gradlew.bat build` (Windows) ou `./gradlew build` → `build/libs/citizeninventaire-1.3.0.jar` → dossier `mods/` du serveur.
