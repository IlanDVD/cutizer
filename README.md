# Cutizer — Miso

Un petit compagnon de temps d’écran. Premier prototype **Windows**, local, sans compte ni serveur.

## Ouvrir l’application

Double-cliquer sur `dist/Cutizer/Cutizer.exe`. Conserver tout le dossier `Cutizer` ensemble : l’exécutable a besoin des fichiers voisins. Aucune installation ou élévation administrateur n’est nécessaire.

Pour développer : Node.js 22+, `npm install`, puis `npm start`.

## Essayer

- À l’accueil, cliquer sur Miso pour lui faire un câlin.
- Dans **Mes applications**, activer les réseaux à suivre. Instagram, TikTok et YouTube sont activés par défaut. Ajouter des règles avec un nom de processus Windows ou des mots présents dans le titre de l’onglet.
- Dans **Les interventions**, cliquer sur **Essayer**. Les essais durent au maximum 12 secondes par défaut. Décocher « Essais courts » pour les durées réelles. Les essais n’ajoutent rien aux statistiques.
- **Masquer** sur chaque overlay ou **Ctrl + Alt + M** ferme immédiatement l’animation. Échap fonctionne aussi depuis la fenêtre principale.
- Le bouton en haut à droite met le suivi en pause et ferme l’overlay en cours.

## Les paliers

Le temps est **cumulé par jour sur toutes les applications sélectionnées**. Chaque palier se déclenche une seule fois dans la journée, y compris après redémarrage de l’application.

| Temps cumulé | Intervention |
| --- | --- |
| 30 minutes | Traversée du bas de l’écran de droite à gauche, 12 secondes. |
| 60 minutes | Descente en rappel au centre, regard insistant, remontée. Total : 15 secondes. |
| 90 minutes | Apparition depuis le bas, bras croisés. Attend 120 secondes, avec 1 seconde d’entrée et 1 seconde de sortie. |
| 120 minutes | Miso géant, voile opaque et sorts animés pendant 10 minutes, puis fondu de 2 secondes. |

Les overlays sont des fenêtres transparentes Windows indépendantes, au-dessus des applications. Ils ne changent pas leur contenu et ne bloquent pas leurs clics : seul le bouton Masquer reçoit la souris. Ils apparaissent sur l’écran où se trouve le curseur. Les applications en plein écran exclusif et les écrans sécurisés Windows peuvent passer au-dessus de l’overlay.

## Fonctionnement du suivi

Un processus PowerShell local interroge Windows environ chaque seconde (`GetForegroundWindow`, titre de fenêtre, nom du processus, inactivité). Seuls les intervalles consécutifs de la même application sont comptés ; une transition peut donc perdre environ une seconde. Les longues interruptions, veille et verrouillage ne sont pas comptés. Après cinq minutes sans activité clavier/souris, le suivi est suspendu — cela inclut une vidéo regardée sans interaction.

Pour les navigateurs Chrome, Edge, Firefox, Brave, Opera, Vivaldi, Arc et Zen, la reconnaissance repose sur **le titre de l’onglet**, pas sur son URL : ce prototype peut manquer un site dont le nom n’apparaît pas dans le titre ou compter un autre onglet qui contient le même mot. Les règles personnalisées permettent d’affiner ce comportement. Pour une détection web fiable, une extension navigateur sera une étape ultérieure.

L’application doit rester ouverte ; on peut réduire sa fenêtre. La fermeture arrête le suivi. Aucun lancement automatique avec Windows.

Les statistiques et choix sont enregistrés dans `%APPDATA%/cutizer/cutizer.json`, toutes les 15 secondes, lors des changements et à la fermeture. Historique limité à 30 jours, remise à zéro du compteur à minuit selon l’heure locale du PC. Les titres des fenêtres ne sont jamais enregistrés. Aucun envoi réseau par l’application. Les éventuelles erreurs de suivi ou de sauvegarde sont affichées dans l’interface.

## Remplacer Miso

Remplacer `src/assets/miso-happy.png` et `src/assets/miso-judge.png` par des PNG transparents avec un cadrage similaire. Le premier représente le personnage souriant ; le second les bras croisés avec un air de jugement. Les mouvements sont en CSS dans `src/overlay.css`. Refaire `npm run pack` après modification. Dans la version portable, les mêmes fichiers se trouvent dans `dist/Cutizer/resources/app/src/assets`.

Images provisoires créées avec l’outil intégré imagegen. Prompts dans `src/assets/PROMPTS.txt`.

## Vérifications et distribution

- `npm test` : tests des paliers, pause, inactivité, reconnaissance, changement de jour et restauration.
- `npm run test:desktop` : lance réellement Electron et le suivi Windows, vérifie l’interface et les quatre overlays, enregistre des captures dans `test-output`. Utilise un profil isolé, sans toucher aux statistiques normales. Ce test complet nécessite de laisser la fenêtre au premier plan et un PC non inactif. Pour vérifier uniquement les interactions et overlays pendant une autre activité : `npm run test:desktop -- --no-captures --smoke-ui-only`.
- `npm run pack` : prépare `dist/Cutizer/Cutizer.exe` et ses dépendances. Application portable non signée, pour test local.

## Téléphone

Cette livraison est uniquement le prototype PC. L’intégration Android/iOS n’est pas implémentée. Le moteur des règles et les assets sont séparés pour préparer le portage ; détection des applications et overlays devront être remplacés par les capacités et autorisations de chaque système mobile.

Références techniques : https://www.electronjs.org/docs/latest/api/browser-window/ et https://learn.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-getforegroundwindow.
